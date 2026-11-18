package com.mooncrown04.m3u

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainActivity
import kotlin.math.roundToInt

/**
 * Eklenti ayarlari: M3U listesi ekleme / silme.
 *
 * CloudStream'de "Ayarlar -> Eklentiler -> M3UPlugin -> disli" yolundan acilir.
 * XML layout kullanmaz (requiresResources = false ile de calisir).
 */
object M3USettingsDialog {

    fun show(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        fun render() {
            body.removeAllViews()

            body.addView(
                TextView(context).apply {
                    text = "Her eklediğin M3U listesi, sağlayıcı listesinde ayrı bir eklenti " +
                        "girdisi olur. Listenin her kategorisi ana sayfada bir dizi kartı gibi görünür; " +
                        "kategoriye girince kanallar bölüm listesi olur. Yayındayken kumandada " +
                        "yukarı/aşağı (FontPlugin'de önceki/sonraki bölüm) kanal değiştirir."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, 0, 0, dp(10))
                },
                matchWidth()
            )

            body.addView(
                Button(context).apply {
                    text = "＋ Yeni M3U listesi ekle"
                    setOnClickListener {
                        addDialog(context) { render() }
                    }
                },
                matchWidth()
            )

            val saved = M3UStore.playlists()
            if (saved.isEmpty()) {
                body.addView(
                    TextView(context).apply {
                        text = "Henüz liste eklenmedi. Örnek: https://.../liste.m3u"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setPadding(0, dp(14), 0, 0)
                    },
                    matchWidth()
                )
            }

            saved.forEach { playlist ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(12), 0, 0)
                }

                row.addView(
                    TextView(context).apply {
                        text = "${playlist.name}\n${playlist.url}"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                )

                row.addView(
                    Button(context).apply {
                        text = "Sil"
                        setOnClickListener {
                            M3UStore.remove(playlist.id)
                            M3UStore.sync()
                            refreshHost()
                            toast(context, "\"${playlist.name}\" silindi")
                            render()
                        }
                    }
                )

                body.addView(row, matchWidth())
            }

            body.addView(
                TextView(context).apply {
                    text = "Not: Liste adresi http(s) linki, file:// yolu veya /sdcard/... gibi " +
                        "yerel bir dosya yolu olabilir. Liste değişiklikleri 10 dakika önbelleğe alınır; " +
                        "listeyi yenilemek için uygulamayı yeniden başlat."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(16), 0, 0)
                },
                matchWidth()
            )
        }

        val scroll = ScrollView(context).apply { addView(body) }
        render()

        AlertDialog.Builder(context)
            .setTitle("M3U ayarları")
            .setView(scroll)
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun addDialog(context: Context, onSaved: () -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val nameInput = EditText(context).apply {
            hint = "Liste adı (ör. Spor)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val urlInput = EditText(context).apply {
            hint = "https://ornek.com/liste.m3u"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipboardText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (!clipboardText.isNullOrBlank() && isSupportedSource(clipboardText.trim())) {
            urlInput.setText(clipboardText.trim())
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(TextView(context).apply {
                text = "Liste adı"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            addView(nameInput, matchWidth())
            addView(TextView(context).apply {
                text = "M3U adresi"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(10), 0, 0)
            })
            addView(urlInput, matchWidth())
        }

        AlertDialog.Builder(context)
            .setTitle("Yeni M3U listesi")
            .setView(layout)
            .setPositiveButton("Kaydet") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()

                when {
                    name.isEmpty() -> toast(context, "Liste adı boş olamaz")

                    !isSupportedSource(url) -> toast(context, "Geçerli bir http(s) linki veya dosya yolu gir")

                    M3UStore.playlists().any { it.name.equals(name, ignoreCase = true) } ->
                        toast(context, "Bu isimde bir liste zaten var")

                    else -> {
                        M3UStore.add(name, url)
                        M3UStore.sync()
                        refreshHost()
                        toast(context, "\"$name\" eklendi")
                        onSaved()
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun isSupportedSource(source: String): Boolean {
        if (source.isBlank()) return false
        return source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true) ||
            source.startsWith("file://", ignoreCase = true) ||
            source.startsWith("/")
    }

    /** Yeni saglayici listesinin ana sayfaya yansimasi icin. */
    private fun refreshHost() {
        runCatching { APIHolder.initAll() }
        runCatching { MainActivity.reloadHomeEvent.invoke(true) }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
