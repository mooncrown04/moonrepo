package com.mooncrown04.mooncrown

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mooncrown04.fontplugin.FontSettingsDialog
import com.mooncrown04.m3u.M3UStore
import com.mooncrown04.m3u.M3USettingsDialog
import kotlin.math.roundToInt

/**
 * Birlesik eklentinin disli ekrani.
 *
 * Iki bolum var: font/tema/kumanda tuslari ve M3U listeleri. Alt ekranlar
 * kendi dialoglaridir; buradan acilir.
 */
object MooncrownSettingsDialog {

    fun show(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        var dialog: AlertDialog? = null

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        body.addView(
            TextView(context).apply {
                text = "Bu eklenti iki bolumden olusur: yazi tipi/tema/kumanda tuslari ve " +
                    "M3U listeleri. Ikisi de asagidaki dugmelerden ayarlanir."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(10))
            },
            matchWidth()
        )

        body.addView(
            Button(context).apply {
                text = "Yazı tipi, tema ve kumanda tuşları"
                setOnClickListener {
                    dialog?.dismiss()
                    FontSettingsDialog.show(context)
                }
            },
            matchWidth()
        )

        val playlistCount = M3UStore.playlists().size
        body.addView(
            Button(context).apply {
                text = if (playlistCount == 0) {
                    "M3U listeleri (henüz liste yok)"
                } else {
                    "M3U listeleri ($playlistCount liste)"
                }
                setOnClickListener {
                    dialog?.dismiss()
                    M3USettingsDialog.show(context)
                }
            },
            matchWidth()
        )

        body.addView(
            TextView(context).apply {
                text = "M3U notu: Her liste ayri bir saglayici olur. Listenin her kategorisi ana " +
                    "sayfada bir dizi karti gibi gorunur, kanallar bolum listesi olur. Yayindayken " +
                    "kumandada yukari/asagi (kumanda tuslari bolumunde \"onceki/sonraki bolum\" " +
                    "olarak atanmis) kanal degistirir."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(12), 0, 0)
            },
            matchWidth()
        )

        val scroll = ScrollView(context).apply { addView(body) }

        dialog = AlertDialog.Builder(context)
            .setTitle("Eklenti ayarları")
            .setView(scroll)
            .setNegativeButton("Kapat", null)
            .create()

        dialog.show()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
