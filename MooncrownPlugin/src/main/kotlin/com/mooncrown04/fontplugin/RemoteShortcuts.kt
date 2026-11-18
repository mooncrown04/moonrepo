package com.mooncrown04.fontplugin

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.widget.Toast
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * TV kumandasi kisayollari.
 *
 * Her tusun ayri bir ac/kapat anahtari ve islemi vardir. Bolum islemleri
 * CloudStream'in GeneratorPlayer.prevEpisode()/nextEpisode() metotlarini,
 * gezinme islemleri host'un NavController'ini kullanir. Boylece uygulamanin
 * kendi akisi korunur.
 */
object RemoteShortcuts {

    private const val TAG = "FontPlugin"
    private const val ACTION_PREFIX = "font_plugin_key_action_"
    private const val ENABLED_PREFIX = "font_plugin_key_enabled_"

    /**
     * Kullaniciya gosterilen tuslar. Varsayilanlar onceki istekleri korur:
     * kirmizi ana sayfa, yesil arama, sari ayarlar, mavi depo, DPAD bolum.
     * Menü ve ayarlar tusu varsayilan olarak kapali; host'un kendi menulerini
     * bozmamak icin kullanici isterse acar.
     */
    val keys: List<KeySpec> = listOf(
        KeySpec(KeyEvent.KEYCODE_PROG_RED, "Kırmızı tuş", KeyAction.HOME),
        KeySpec(KeyEvent.KEYCODE_PROG_GREEN, "Yeşil tuş", KeyAction.SEARCH),
        KeySpec(KeyEvent.KEYCODE_PROG_YELLOW, "Sarı tuş", KeyAction.SETTINGS),
        KeySpec(KeyEvent.KEYCODE_PROG_BLUE, "Mavi tuş", KeyAction.EXTENSIONS),
        KeySpec(KeyEvent.KEYCODE_DPAD_UP, "DPAD yukarı", KeyAction.PREV_EPISODE),
        KeySpec(KeyEvent.KEYCODE_DPAD_DOWN, "DPAD aşağı", KeyAction.NEXT_EPISODE),
        KeySpec(KeyEvent.KEYCODE_MEDIA_REWIND, "Medya: geri sar", KeyAction.EXTENSIONS),
        KeySpec(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Medya: ileri sar", KeyAction.SETTINGS),
        KeySpec(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Oynat / duraklat", KeyAction.HOME),
        KeySpec(KeyEvent.KEYCODE_MENU, "Menü tuşu", KeyAction.NONE, defaultEnabled = false),
        KeySpec(KeyEvent.KEYCODE_SETTINGS, "Ayarlar tuşu", KeyAction.NONE, defaultEnabled = false),
    )

    private val originals = WeakHashMap<Activity, Window.Callback>()
    private val wrappers = WeakHashMap<Activity, Window.Callback>()
    private val consumed = WeakHashMap<Activity, MutableSet<Int>>()

    fun action(context: Context, spec: KeySpec): KeyAction {
        val saved = prefs(context).getString(ACTION_PREFIX + spec.code, null)
        return KeyAction.fromId(saved) ?: spec.defaultAction
    }

    fun setAction(context: Context, spec: KeySpec, action: KeyAction) {
        prefs(context).edit().putString(ACTION_PREFIX + spec.code, action.id).apply()
    }

    fun isKeyEnabled(context: Context, spec: KeySpec): Boolean =
        prefs(context).getBoolean(ENABLED_PREFIX + spec.code, spec.defaultEnabled)

    fun setKeyEnabled(context: Context, spec: KeySpec, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED_PREFIX + spec.code, enabled).apply()
    }

    fun resetBindings(context: Context) {
        val editor = prefs(context).edit()
        keys.forEach { spec ->
            editor.remove(ACTION_PREFIX + spec.code)
            editor.remove(ENABLED_PREFIX + spec.code)
        }
        editor.apply()
    }

    /** Her activity'nin window callback'ini sarar; tekrar cagrilmasi zararsizdir. */
    fun install(activity: Activity) {
        try {
            val window = activity.window ?: return
            val current = window.callback ?: return
            val existing = wrappers[activity]

            if (current === existing) return

            val original = current
            originals[activity] = original

            val wrapper = Proxy.newProxyInstance(
                activity.classLoader,
                arrayOf(Window.Callback::class.java),
            ) { _, method, args ->
                if (method.name == "dispatchKeyEvent" && args?.size == 1 && args[0] is KeyEvent) {
                    if (handle(activity, args[0] as KeyEvent)) {
                        true
                    } else {
                        invoke(original, method, args)
                    }
                } else {
                    invoke(original, method, args)
                }
            } as Window.Callback

            wrappers[activity] = wrapper
            runCatching { window.callback = wrapper }
                .onFailure { Log.w(TAG, "Key callback could not be installed", it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Key callback could not be installed", t)
        }
    }

    fun forget(activity: Activity) {
        originals.remove(activity)
        wrappers.remove(activity)
        consumed.remove(activity)
        KeyOverlay.forget(activity)
    }

    private fun handle(activity: Activity, event: KeyEvent): Boolean {
        val spec = keys.firstOrNull { it.code == event.keyCode } ?: return false
        if (!isKeyEnabled(activity, spec)) return false

        val action = action(activity, spec)
        if (action == KeyAction.NONE) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) {
                    consumed[activity]?.contains(spec.code) == true
                } else {
                    val handled = perform(activity, spec, action)
                    if (handled) {
                        consumed.getOrPut(activity) { mutableSetOf() }.add(spec.code)
                    }
                    handled
                }
            }

            KeyEvent.ACTION_UP -> consumed[activity]?.remove(spec.code) == true
            else -> false
        }
    }

    private fun perform(activity: Activity, spec: KeySpec, action: KeyAction): Boolean {
        val player = findPlayerFragment(activity)

        if (action.episode != null) {
            if (player == null) return false
            if (isDpad(spec) && isPlayerUiShowing(player)) return false

            val isPrev = action.episode == EpisodeDirection.PREV
            val label = if (isPrev) "ÖNCEKİ BÖLÜM" else "SONRAKİ BÖLÜM"

            // 4.91-pre akışı: GeneratorPlayer.nextEpisode() -> viewModel.loadLinksNext().
            // Son/ilk bölümde boşa basmamak için önce hasNextEpisode()/hasPrevEpisode() sorulur.
            if (hasEpisode(player, isPrev) == false) {
                KeyOverlay.message(
                    activity,
                    label,
                    if (isPrev) "İlk bölümdesin" else "Son bölümdesin",
                )
                return true
            }

            val handled = invokeEpisode(player, if (isPrev) "prevEpisode" else "nextEpisode")
            if (handled) {
                // Bölüm adı host tarafında biraz sonra güncellenir; banner bunu bekler.
                KeyOverlay.episodeChange(activity, label) { episodeTitle(player) }
            }
            return handled
        }

        val destination = action.navId ?: return false

        // Video oynarken gezinme tuslari videoyu kazara kapatmasin.
        if (player != null) return false

        val handled = navigateTo(activity, destination)
        if (handled) {
            // Hangi tusa basildigini ve ne yaptigini kullaniciya bildir.
            toast(activity, "${spec.label} → ${action.label}")
        }
        return handled
    }

    /**
     * 4.91-pre (2dcc25c) icinde GeneratorPlayer guncel bolumu
     * `private val currentMeta get() = viewModel.state.generatorState?.meta` uzerinden tutar.
     * Meta bir ResultEpisode'dir; kullaniciya gosterilecek ad `name` alanindadir.
     */
    private fun episodeTitle(player: Any): String? {
        val meta = readProperty(player, "currentMeta")
            ?: listOf("currentEpisode", "episode", "currentEpisodeData")
                .firstNotNullOfOrNull { readProperty(player, it) }
            ?: return null

        val name = readProperty(meta, "name") as? String
        val header = readProperty(meta, "headerName") as? String
        return listOfNotNull(name, header, readProperty(meta, "title") as? String)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    /** PlayerGeneratorViewModel.hasNextEpisode()/hasPrevEpisode(); bilinmiyorsa null. */
    private fun hasEpisode(player: Any, prev: Boolean): Boolean? {
        val viewModel = readProperty(player, "viewModel") ?: return null
        val methodName = if (prev) "hasPrevEpisode" else "hasNextEpisode"
        return callFlag(viewModel, methodName)
    }

    private fun callFlag(target: Any, methodName: String): Boolean? =
        runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(target) as? Boolean
        }.getOrNull()

    private fun readProperty(target: Any, name: String): Any? {
        val capitalized = name.replaceFirstChar { it.uppercase() }
        for (methodName in listOf("get$capitalized", "is$capitalized", name)) {
            runCatching { target.javaClass.getMethod(methodName).invoke(target) }
                .getOrNull()?.let { return it }
        }

        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            for (methodName in listOf("get$capitalized", name)) {
                val method = runCatching { cls!!.getDeclaredMethod(methodName) }.getOrNull()
                if (method != null && method.parameterCount == 0) {
                    method.isAccessible = true
                    runCatching { method.invoke(target) }.getOrNull()?.let { return it }
                }
            }

            val field = runCatching { cls!!.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return runCatching { field.get(target) }.getOrNull()
            }
            cls = cls.superclass
        }
        return null
    }

    private fun toast(activity: Activity, message: String) {
        runCatching {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDpad(spec: KeySpec): Boolean =
        spec.code == KeyEvent.KEYCODE_DPAD_UP || spec.code == KeyEvent.KEYCODE_DPAD_DOWN

    private fun invokeEpisode(fragment: Any, methodName: String): Boolean {
        val method = fragment.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        } ?: return false

        return try {
            method.invoke(fragment)
            true
        } catch (t: InvocationTargetException) {
            val cause = t.targetException
            if (cause !is NotImplementedError) {
                Log.w(TAG, "$methodName failed", cause)
            }
            false
        } catch (t: Throwable) {
            Log.w(TAG, "$methodName failed", t)
            false
        }
    }

    private fun navigateTo(activity: Activity, destinationName: String): Boolean {
        val id = runCatching {
            activity.resources.getIdentifier(destinationName, "id", activity.packageName)
        }.getOrDefault(0)
        if (id == 0) return false

        val controller = findNavController(activity) ?: return false
        return try {
            controller.javaClass
                .getMethod("navigate", Integer.TYPE)
                .invoke(controller, id)
            true
        } catch (t: InvocationTargetException) {
            Log.w(TAG, "Navigation to $destinationName failed", t.targetException)
            false
        } catch (t: Throwable) {
            Log.w(TAG, "Navigation to $destinationName failed", t)
            false
        }
    }

    private fun findPlayerFragment(activity: Activity): Any? {
        val manager = supportFragmentManager(activity) ?: return null
        return findPlayerInManager(manager, 0)
    }

    private fun findPlayerInManager(manager: Any, depth: Int): Any? {
        if (depth > 10) return null

        val fragments = runCatching {
            manager.javaClass.getMethod("getFragments").invoke(manager) as? List<*>
        }.getOrNull() ?: return null

        var fallback: Any? = null
        for (fragment in fragments) {
            if (fragment == null) continue
            val name = fragment.javaClass.name

            if (hasEpisodeMethod(fragment) && isFragmentActive(fragment)) {
                if (name.contains("GeneratorPlayer")) return fragment
                if (name.contains("Player") &&
                    !name.contains("Result") &&
                    !name.contains("Trailer")
                ) {
                    fallback = fallback ?: fragment
                }
            }

            val childManager = runCatching {
                fragment.javaClass.getMethod("getChildFragmentManager").invoke(fragment)
            }.getOrNull()
            if (childManager != null) {
                findPlayerInManager(childManager, depth + 1)?.let { return it }
            }
        }

        return fallback
    }

    private fun findNavController(activity: Activity): Any? {
        val manager = supportFragmentManager(activity) ?: return null
        return findNavControllerIn(manager, 0)
    }

    private fun findNavControllerIn(manager: Any, depth: Int): Any? {
        if (depth > 10) return null

        val fragments = runCatching {
            manager.javaClass.getMethod("getFragments").invoke(manager) as? List<*>
        }.getOrNull() ?: return null

        for (fragment in fragments) {
            if (fragment == null) continue

            if (fragment.javaClass.name.contains("NavHostFragment")) {
                val controller = runCatching {
                    fragment.javaClass.getMethod("getNavController").invoke(fragment)
                }.getOrNull()
                if (controller != null) return controller
            }

            val childManager = runCatching {
                fragment.javaClass.getMethod("getChildFragmentManager").invoke(fragment)
            }.getOrNull()
            if (childManager != null) {
                findNavControllerIn(childManager, depth + 1)?.let { return it }
            }
        }

        return null
    }

    private fun supportFragmentManager(activity: Activity): Any? =
        runCatching {
            activity.javaClass.getMethod("getSupportFragmentManager").invoke(activity)
        }.getOrNull()

    private fun hasEpisodeMethod(fragment: Any): Boolean =
        fragment.javaClass.methods.any {
            (it.name == "prevEpisode" || it.name == "nextEpisode") && it.parameterCount == 0
        }

    private fun isFragmentActive(fragment: Any): Boolean {
        val visible = runCatching {
            fragment.javaClass.getMethod("isVisible").invoke(fragment) as? Boolean
        }.getOrNull() ?: true
        val resumed = runCatching {
            fragment.javaClass.getMethod("isResumed").invoke(fragment) as? Boolean
        }.getOrNull() ?: true
        return visible && resumed
    }

    /**
     * 4.91-pre: FullScreenPlayer.isUIShowing() kontrolleri, isDialogOpen() ise bolum
     * listesi/kalite gibi ust duzey panelleri bildirir. Ikisi de acikken DPAD bolum
     * degistirmemeli; host'un kendi odak gezinmesi calismali.
     */
    private fun isPlayerUiShowing(fragment: Any): Boolean {
        if (callFlag(fragment, "isUIShowing") == true) return true
        return callFlag(fragment, "isDialogOpen") == true
    }

    private fun invoke(target: Any, method: java.lang.reflect.Method, args: Array<Any?>?): Any? {
        return try {
            method.invoke(target, *(args ?: emptyArray()))
        } catch (t: InvocationTargetException) {
            throw t.targetException
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
