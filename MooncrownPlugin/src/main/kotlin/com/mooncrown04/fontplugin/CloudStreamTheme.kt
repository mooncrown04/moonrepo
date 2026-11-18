package com.mooncrown04.fontplugin

import android.content.Context
import android.content.SharedPreferences

/**
 * CloudStream'in kendi tema tercihine erisir.
 *
 * CloudStream temasini PreferenceManager.getDefaultSharedPreferences uzerinden
 * "app_theme_key" anahtarinda tutar; degerler CommonActivity.loadThemes icinde
 * "System", "Light", "Black", "Amoled" olarak eslesir.
 *
 * Eklentinin kendi paletleri (CustomThemes) app_theme_key'e yazilamaz; onlar
 * ayri bir tercihte tutulur ve CustomThemeApplier ile View agacina uygulanir.
 */
object CloudStreamTheme {

    private const val KEY = "app_theme_key"
    private const val DEFAULT = "AmoledLight"

    const val SYSTEM = "System"
    const val LIGHT = "Light"
    const val DARK = "Black"
    const val AMOLED = "Amoled"

    fun current(context: Context): String {
        val custom = CustomThemes.active(context)
        return if (custom != null) CustomThemes.customValue(custom) else
            prefs(context).getString(KEY, DEFAULT) ?: DEFAULT
    }

    /**
     * Yerlesik tema secildiginde app_theme_key'e yazilir. Ozel palet secildiginde
     * ise palet ayri tercihe kaydedilir; app_theme_key yalnizca Compose ekranlari
     * icin yakin bir taban tema (Light/Black) alir.
     */
    fun apply(context: Context, value: String) {
        val custom = CustomThemes.byValue(value)
        if (custom != null) {
            CustomThemes.set(context, custom)
            prefs(context).edit().putString(KEY, if (custom.light) LIGHT else DARK).apply()
        } else {
            CustomThemes.clear(context)
            prefs(context).edit().putString(KEY, value).apply()
        }
        CustomThemeApplier.bumpGeneration()
    }

    fun label(value: String): String = when (value) {
        SYSTEM -> "Sistem"
        LIGHT -> "Açık"
        DARK -> "Koyu"
        AMOLED -> "AMOLED"
        DEFAULT -> "CloudStream varsayılanı"
        else -> CustomThemes.byValue(value)?.let { "Özel: ${it.name}" }
            ?: "CloudStream teması ($value)"
    }

    private fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        return app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)
    }
}
