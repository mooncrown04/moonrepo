package com.mooncrown04.fontplugin

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.WeakHashMap

/**
 * View agacini dolasip TextView'larin yalnizca typeface degerini degistirir.
 *
 * Orijinal typeface WeakHashMap'te tutulur:
 *  - ayni TextView tekrar islenirse orijinal korunur,
 *  - ayar kapatilinca restore() ile ilk haline donulur.
 *
 * Layout dinleyicisi her geciste apply() cagirdigi icin uygulama idempotent olmali.
 * Her view, gecerli font "generation"i icin yalnizca bir kez uygulanir; boylece
 * her karede setTypeface cagrilmaz ve ekran titremez.
 */
object FontApplier {

    private data class Original(val typeface: Typeface?)

    private data class Applied(val generation: Int, val requested: Typeface)

    private val originals = WeakHashMap<TextView, Original>()
    private val applied = WeakHashMap<TextView, Applied>()

    private var generation = 0

    /** Font ayari degistiginde cagrilir; tum view'lar yeniden uygulanir. */
    fun bumpGeneration() {
        generation++
    }

    fun apply(root: View?, typeface: Typeface?) {
        root ?: return
        if (typeface == null) {
            restore(root)
            return
        }

        val current = generation
        walk(root) { textView ->
            val state = applied[textView]
            if (state != null && state.generation == current && state.requested === typeface) {
                return@walk
            }

            if (originals[textView] == null) {
                originals[textView] = Original(textView.typeface)
            }

            textView.typeface = typeface
            applied[textView] = Applied(current, typeface)
        }
    }

    fun restore(root: View?) {
        root ?: return
        walk(root) { textView ->
            val original = originals.remove(textView) ?: return@walk
            applied.remove(textView)
            textView.typeface = original.typeface
        }
    }

    fun forget() {
        originals.clear()
        applied.clear()
    }

    private fun walk(view: View, action: (TextView) -> Unit) {
        if (view is TextView && !isIconLike(view)) action(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index), action)
            }
        }
    }

    /**
     * Material ikon fontlari genelde Private Use Area (U+E000..U+F8FF) karakterleri kullanir.
     * Bunlara dokunmamak, ikonlarin bozulmasini engeller.
     */
    private fun isIconLike(textView: TextView): Boolean {
        val text = textView.text ?: return false
        if (text.isEmpty()) return false
        return text.all { character -> character.code in 0xE000..0xF8FF }
    }
}
