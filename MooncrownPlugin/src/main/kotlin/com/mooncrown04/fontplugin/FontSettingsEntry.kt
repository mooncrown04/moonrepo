package com.mooncrown04.fontplugin

import android.app.Activity
import android.content.Context
import android.util.Log
import java.lang.reflect.Proxy

/**
 * TV box / eski CloudStream surumleri icin deneysel kisayol.
 *
 * CloudStream'in ayarlar ekrani legacy (androidx PreferenceFragment) ise "Font
 * ayarlari" satiri eklenir. Compose ayarlar ekraninda HICBIR SEYE dokunulmaz;
 * orada resmi disli yolu kullanilmaya devam eder.
 */
object FontSettingsEntry {

    private const val TAG = "FontPlugin"
    private const val KEY = "font_plugin_settings"

    private var opener: ((Context) -> Unit)? = null

    fun setOpener(block: (Context) -> Unit) {
        opener = block
    }

    fun tryInstall(activity: Activity) {
        try {
            val version = activity.packageManager
                .getPackageInfo(activity.packageName, 0)
                .versionName

            if (isComposeSettingsHost(activity)) {
                Log.d(TAG, "Settings entry skipped: Compose settings host (v$version)")
                return
            }

            val fragment = findPreferenceSettingsFragment(activity) ?: return

            val screen = fragment.javaClass
                .getMethod("getPreferenceScreen")
                .invoke(fragment) ?: return

            if (findExisting(screen)) return

            addPreference(activity, fragment, screen)
            Log.d(TAG, "Settings entry added for v$version")
        } catch (t: Throwable) {
            // Host surumu farkliysa sessizce vazgec; disli yolu bozulmaz.
            Log.d(TAG, "Settings entry skipped: ${t.javaClass.simpleName}")
        }
    }

    private fun isComposeSettingsHost(activity: Activity): Boolean {
        val loader = activity.javaClass.classLoader ?: return false
        return runCatching {
            Class.forName("com.lagradost.cloudstream3.utils.BaseComposeFragment", false, loader)
            true
        }.getOrElse {
            runCatching {
                Class.forName("com.lagradost.cloudstream3.ui.settings.SettingsUIScreen", false, loader)
                true
            }.getOrDefault(false)
        }
    }

    private fun findPreferenceSettingsFragment(activity: Activity): Any? {
        val manager = activity.javaClass
            .getMethod("getSupportFragmentManager")
            .invoke(activity) ?: return null

        return walkFragments(manager, 0)
    }

    private fun walkFragments(manager: Any, depth: Int): Any? {
        if (depth > 8) return null

        val fragments = manager.javaClass
            .getMethod("getFragments")
            .invoke(manager) as? List<*> ?: return null

        for (fragment in fragments) {
            if (fragment == null) continue

            if (isPreferenceFragment(fragment) && fragment.javaClass.name.contains("Settings", true)) {
                return fragment
            }

            val childManager = runCatching {
                fragment.javaClass.getMethod("getChildFragmentManager").invoke(fragment)
            }.getOrNull() ?: continue

            walkFragments(childManager, depth + 1)?.let { return it }
        }

        return null
    }

    private fun isPreferenceFragment(fragment: Any): Boolean {
        var type: Class<*>? = fragment.javaClass
        while (type != null) {
            if (type.name == "androidx.preference.PreferenceFragmentCompat" ||
                type.name == "android.preference.PreferenceFragment"
            ) {
                return true
            }
            type = type.superclass
        }
        return false
    }

    private fun findExisting(screen: Any): Boolean = runCatching {
        val method = screen.javaClass.getMethod("findPreference", CharSequence::class.java)
        method.invoke(screen, KEY) != null
    }.getOrDefault(false)

    private fun addPreference(activity: Activity, fragment: Any, screen: Any) {
        val loader = fragment.javaClass.classLoader
        val preferenceClass = Class.forName("androidx.preference.Preference", false, loader)

        val preference = preferenceClass
            .getConstructor(Context::class.java)
            .newInstance(activity)

        preferenceClass.getMethod("setKey", String::class.java).invoke(preference, KEY)
        preferenceClass.getMethod("setTitle", CharSequence::class.java)
            .invoke(preference, "Font ayarları")
        preferenceClass.getMethod("setSummary", CharSequence::class.java)
            .invoke(preference, "Yazı tipi ve uygulama teması")

        val listenerClass = Class.forName(
            "androidx.preference.Preference\$OnPreferenceClickListener",
            false,
            loader
        )

        val listener = Proxy.newProxyInstance(loader, arrayOf(listenerClass)) { _, method, _ ->
            if (method.name == "onPreferenceClick") {
                opener?.invoke(activity)
                true
            } else {
                false
            }
        }

        preferenceClass
            .getMethod("setOnPreferenceClickListener", listenerClass)
            .invoke(preference, listener)

        screen.javaClass
            .getMethod("addPreference", preferenceClass)
            .invoke(screen, preference)
    }
}
