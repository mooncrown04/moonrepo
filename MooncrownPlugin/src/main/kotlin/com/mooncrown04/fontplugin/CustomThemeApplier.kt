package com.mooncrown04.fontplugin

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ozel paletleri View agacina uygular.
 *
 * CloudStream'in tema motoru yalnizca sabit stilleri bildigi icin burada renkler
 * calisma aninda verilir. Yalnizca klasik View ekranlari etkilenir; Compose
 * ekranlari kendi MaterialTheme renklerini kullanmaya devam eder.
 *
 * Kurallar bilincli olarak dar tutuldu:
 *  - pencere/root, toolbar, appbar, alt navigasyon ve kartlar boyanir,
 *  - metinler okunabilirlik kontrolunden gecirilir,
 *  - poster/thumbnail gibi bitmap ImageView'lara dokunulmaz.
 */
object CustomThemeApplier {

    private data class Applied(val generation: Int, val surface: Int)

    private val applied = WeakHashMap<View, Applied>()
    private val windowApplied = WeakHashMap<Activity, Int>()
    private var generation = 0

    fun bumpGeneration() {
        generation++
    }

    fun apply(activity: Activity, root: View?) {
        val palette = CustomThemes.active(activity) ?: return
        root ?: return

        val window = activity.window

        if (windowApplied[activity] != generation) {
            windowApplied[activity] = generation
            window?.setBackgroundDrawable(ColorDrawable(palette.background))
            runCatching {
                window?.statusBarColor = palette.darker
                window?.navigationBarColor = palette.darker
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val flags = root.systemUiVisibility
                    root.systemUiVisibility = if (palette.light) {
                        flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                }
            }
        }

        setBackground(root, palette.background)
        walk(root, palette.background, palette)
    }

    private fun walk(view: View, surface: Int, palette: CustomThemes.Palette) {
        var childSurface = surface
        val className = view.javaClass.name

        when {
            isCard(className) -> {
                if (!invokeColorState(view, "setCardBackgroundColor", ColorStateList.valueOf(palette.item))) {
                    setBackground(view, palette.item)
                }
                childSurface = palette.item
            }

            className.contains("Toolbar") || className.contains("AppBarLayout") -> {
                setBackground(view, palette.gray)
                childSurface = palette.gray
            }

            className.contains("BottomNavigationView") || className.contains("NavigationBarView") -> {
                setBackground(view, palette.darker)
                invokeColorState(view, "setItemIconTintList", ColorStateList.valueOf(palette.icon))
                invokeColorState(view, "setItemTextColor", ColorStateList.valueOf(palette.text))
                childSurface = palette.darker
            }
        }

        if (view is TextView) {
            val text = view.text
            val iconLike = text != null && text.isNotEmpty() &&
                text.all { it.code in 0xE000..0xF8FF }

            if (iconLike) {
                view.setTextColor(readable(palette.icon, childSurface))
            } else if (view is Button) {
                setBackgroundTint(view, palette.item)
                view.setTextColor(readable(palette.text, palette.item))
            } else if (className.contains("Chip")) {
                setBackgroundTint(view, palette.item)
                view.setTextColor(readable(palette.text, palette.item))
            } else {
                val small = view.textSize <= 12f * view.resources.displayMetrics.scaledDensity
                val preferred = if (small) palette.secondaryText else palette.text
                view.setTextColor(readable(preferred, childSurface))
            }
        } else if (view is ImageButton) {
            setBackgroundTint(view, palette.item)
            tintImage(view, readable(palette.icon, palette.item))
        } else if (view is ImageView) {
            val drawable = view.drawable
            if (drawable != null && isIconDrawable(drawable)) {
                tintImage(view, readable(palette.icon, childSurface))
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index), childSurface, palette)
            }
        }
    }

    private fun setBackground(view: View, color: Int) {
        val state = applied[view]
        if (state != null && state.generation == generation && state.surface == color) return
        view.setBackgroundColor(color)
        applied[view] = Applied(generation, color)
    }

    private fun setBackgroundTint(view: View, color: Int) {
        invokeColorState(view, "setBackgroundTintList", ColorStateList.valueOf(color))
    }

    private fun tintImage(view: ImageView, color: Int) {
        runCatching { view.imageTintList = ColorStateList.valueOf(color) }
    }

    private fun invokeColorState(view: View, method: String, value: ColorStateList): Boolean =
        runCatching {
            view.javaClass.getMethod(method, ColorStateList::class.java).invoke(view, value)
        }.isSuccess

    private fun isCard(className: String): Boolean =
        className == "androidx.cardview.widget.CardView" ||
            className.endsWith(".MaterialCardView") ||
            className.contains("CardView")

    private fun isIconDrawable(drawable: Drawable): Boolean {
        if (drawable is BitmapDrawable || drawable is ColorDrawable) return false
        val name = drawable.javaClass.name
        return name.contains("Vector") || name.contains("AnimatedVector")
    }

    /** Tercih edilen rengi, arka planla yeterli kontrast yoksa siyah/beyaza cevirir. */
    private fun readable(preferred: Int, background: Int): Int {
        if (contrast(preferred, background) >= 2.2) return preferred
        return if (luminance(background) > 0.5) Color.BLACK else Color.WHITE
    }

    private fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
