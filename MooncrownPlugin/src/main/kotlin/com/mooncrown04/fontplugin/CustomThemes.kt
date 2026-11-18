package com.mooncrown04.fontplugin

import android.content.Context
import android.graphics.Color

/**
 * Eklentinin kendi renk paletleri.
 *
 * CloudStream'in app_theme_key anahtari yalnizca sabit stilleri kabul ettigi icin
 * bu paletler uygulama tarafindan degil, CustomThemeApplier tarafindan View agacina
 * uygulanir. Compose ekranlari bu boyamadan etkilenmez.
 */
object CustomThemes {

    data class Palette(
        val id: String,
        val name: String,
        val background: Int,
        val darker: Int,
        val gray: Int,
        val item: Int,
        val text: Int,
        val secondaryText: Int,
        val icon: Int,
        val light: Boolean,
    )

    private const val PREFIX = "custom:"
    private const val KEY = "font_plugin_custom_theme"

    val all: List<Palette> = listOf(
        Palette(
            id = "gs_ters",
            name = "Galatasaray Blue (ters)",
            background = Color.parseColor("#C89100"),
            darker = Color.parseColor("#FDB912"),
            gray = Color.parseColor("#FDB912"),
            item = Color.parseColor("#A90432"),
            text = Color.parseColor("#A90432"),
            secondaryText = Color.parseColor("#800325"),
            icon = Color.parseColor("#A90432"),
            light = true,
        ),
        Palette(
            id = "gs_bordo",
            name = "Galatasaray Bordo",
            background = Color.parseColor("#2B040D"),
            darker = Color.parseColor("#1A0207"),
            gray = Color.parseColor("#A90432"),
            item = Color.parseColor("#800325"),
            text = Color.parseColor("#FDB912"),
            secondaryText = Color.parseColor("#D08D9A"),
            icon = Color.parseColor("#FDB912"),
            light = false,
        ),
        Palette(
            id = "gs_kirmizi",
            name = "Galatasaray Kırmızı",
            background = Color.parseColor("#A90432"),
            darker = Color.parseColor("#800325"),
            gray = Color.parseColor("#C1053A"),
            item = Color.parseColor("#B50436"),
            text = Color.parseColor("#FDB912"),
            secondaryText = Color.parseColor("#FFFFFF"),
            icon = Color.parseColor("#FDB912"),
            light = false,
        ),
    )

    fun customValue(palette: Palette): String = PREFIX + palette.id

    fun isCustom(value: String): Boolean = value.startsWith(PREFIX)

    fun idOf(value: String): String? =
        if (isCustom(value)) value.removePrefix(PREFIX) else null

    fun byId(id: String?): Palette? = all.firstOrNull { it.id == id }

    fun byValue(value: String): Palette? = byId(idOf(value))

    fun active(context: Context): Palette? = byId(storedId(context))

    fun set(context: Context, palette: Palette?) {
        prefs(context).edit().apply {
            if (palette == null) remove(KEY) else putString(KEY, palette.id)
        }.apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun storedId(context: Context): String? =
        prefs(context).getString(KEY, null)

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
