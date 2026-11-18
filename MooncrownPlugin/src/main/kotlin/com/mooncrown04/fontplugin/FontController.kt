package com.mooncrown04.fontplugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewTreeObserver
import java.util.Collections
import java.util.WeakHashMap

/**
 * Eklentinin kalbi: CloudStream'in resmi API'sinde tema/font hook'u olmadigi icin
 * Application.ActivityLifecycleCallbacks ile her activity'nin decorView'ina mudahale eder.
 *
 * Onemli: font yalnizca activity resume aninda uygulanirsa, ayni activity icinde
 * sonradan acilan fragment/dialog/menu view'lari varsayilan fontla kalir. Bu yuzden
 * decorView'un layout degisimlerini dinleyip yeni view'lara da uygulariz.
 */
object FontController {

    private var installed = false

    /** Ayni activity'nin birden fazla kez uygulanmasini engellemek icin zayif referanslar. */
    private val resumed: MutableSet<Activity> =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    /** Activity basina eklenen layout dinleyicisi; destroy'da kaldirilir. */
    private val observers = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Layout dinleyicisi her zaman tetiklenmeyebilir (ornegin uygulama sonradan
     * typeface atarsa). Bu yuzden acik ekranlarda dusuk frekansli bir guvenlik agi.
     */
    private val tick = object : Runnable {
        override fun run() {
            if (resumed.isEmpty()) return
            resumed.toList().forEach { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    applyTo(activity)
                    FontSettingsEntry.tryInstall(activity)
                }
            }
            handler.postDelayed(this, 800)
        }
    }

    fun install(context: Context) {
        if (installed) return

        val application = context.applicationContext as? Application ?: return
        installed = true

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                attachLayoutObserver(activity)
                RemoteShortcuts.install(activity)
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                resumed.add(activity)
                attachLayoutObserver(activity)
                RemoteShortcuts.install(activity)
                applyTo(activity)
                FontSettingsEntry.tryInstall(activity)
                startTicker()
            }

            override fun onActivityPaused(activity: Activity) {
                resumed.remove(activity)
                if (resumed.isEmpty()) stopTicker()
            }

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                resumed.remove(activity)
                detachLayoutObserver(activity)
                RemoteShortcuts.forget(activity)
                if (resumed.isEmpty()) stopTicker()
            }
        })

        // Eklenti, uygulama acikken yuklendiyse mevcut activity'ye de uygula.
        (context as? Activity)?.let { activity ->
            resumed.add(activity)
            attachLayoutObserver(activity)
            applyTo(activity)
            startTicker()
        }
    }

    fun applyTo(activity: Activity) {
        RemoteShortcuts.install(activity)
        val typeface = FontLoader.load(activity, FontPreferences.spec(activity))
        val root = activity.window?.decorView ?: return
        FontApplier.apply(root, typeface)
        runCatching { CustomThemeApplier.apply(activity, root) }
            .onFailure { Log.e("FontPlugin", "Theme apply failed", it) }
    }

    /** Ayar ekraninda "Kaydet"e basinca: cache temizlenir ve acik ekranlara uygulanir. */
    fun refresh() {
        FontLoader.clearCache()
        FontApplier.bumpGeneration()
        resumed.toList().forEach { activity ->
            applyTo(activity)
        }
    }

    /** Tema tercihi degistiginde acik Activity'leri yeniden olusturur. */
    fun recreateAll() {
        resumed.toList().forEach { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) activity.recreate()
        }
    }

    /** "Sifirla": orijinal typeface'i geri koyar; ozel tema seciliyse renkler devam eder. */
    fun reset() {
        FontLoader.clearCache()
        FontApplier.bumpGeneration()
        resumed.toList().forEach { activity ->
            FontApplier.restore(activity.window?.decorView)
            runCatching { CustomThemeApplier.apply(activity, activity.window?.decorView) }
        }
    }

    private fun attachLayoutObserver(activity: Activity) {
        if (observers.containsKey(activity)) return

        val decor = activity.window?.decorView ?: return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!activity.isFinishing && !activity.isDestroyed) applyTo(activity)
        }

        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        observers[activity] = listener
    }

    private fun detachLayoutObserver(activity: Activity) {
        val listener = observers.remove(activity) ?: return
        val decor = activity.window?.decorView ?: return

        if (decor.viewTreeObserver.isAlive) {
            decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    private fun startTicker() {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 800)
    }

    private fun stopTicker() {
        handler.removeCallbacks(tick)
    }
}
