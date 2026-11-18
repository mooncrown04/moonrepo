package com.mooncrown04.fontplugin

import android.content.Context

/**
 * Secimler SharedPreferences'ta tutulur; eklenti her activity acilisinda buradan okur.
 *
 * spec formatlari:
 *  - ""                  -> sistem varsayilani
 *  - "system:sans-serif" -> sistem font ailesi
 *  - "file:/sdcard/..."  -> cihazdaki .ttf/.otf dosyasi
 *  - "uri:content://..." -> content resolver ile ice aktarilmis font
 */
object FontPreferences {

    private const val PREFS_NAME = "cloudstream_font_plugin"
    private const val KEY_SPEC = "font_spec"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun spec(context: Context): String =
        prefs(context).getString(KEY_SPEC, "") ?: ""

    fun setSpec(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SPEC, value).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
