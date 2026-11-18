package com.mooncrown04.fontplugin

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Eklentinin ayar ekrani. CloudStream'in "Ayarlar -> Eklentiler -> <eklenti> -> disli"
 * yolundan acilir (openSettings).
 *
 * XML layout kullanmaz; requiresResources = false ile de calisir.
 */
object FontSettingsDialog {

    private const val PREVIEW_SP = 18f

    fun show(context: Context) {
        var pendingSpec = FontPreferences.spec(context)
        var pendingTheme = CloudStreamTheme.current(context)
        val initialTheme = pendingTheme

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val preview = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, PREVIEW_SP)
            setPadding(0, dp(12), 0, dp(12))
        }

        fun refreshPreview() {
            preview.text = "Örnek yazı: Kaliteli Türkçe altyazı 0123"
            preview.typeface = FontLoader.load(context, pendingSpec)
        }

        val fontButton = Button(context).apply {
            text = "Font: ${FontLoader.displayName(pendingSpec)}"
            setOnClickListener {
                showFontPicker(context, pendingSpec) { newSpec ->
                    pendingSpec = newSpec
                    text = "Font: ${FontLoader.displayName(newSpec)}"
                    refreshPreview()
                }
            }
        }

        val themeButton = Button(context).apply {
            text = "Tema: ${CloudStreamTheme.label(pendingTheme)}"
            setOnClickListener {
                showThemePicker(context) { newTheme ->
                    pendingTheme = newTheme
                    text = "Tema: ${CloudStreamTheme.label(newTheme)}"
                }
            }
        }

        val keySettingsButton = Button(context).apply {
            text = "Kumanda tuşları (${RemoteShortcuts.keys.size} tuş)"
            setOnClickListener { KeySettingsDialog.show(context) }
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(preview, matchWidth())
            addView(fontButton, matchWidth())
            addView(themeButton, matchWidth())
            addView(keySettingsButton, matchWidth())
            addView(
                TextView(context).apply {
                    text = "Not: Font ve özel temalar yalnızca klasik (View tabanlı) ekranlarda " +
                        "uygulanır; Compose ekranları etkilenmez. Yerleşik tema seçimi CloudStream'in " +
                        "kendi tema ayarını değiştirir. Özel temalar eklentinin kendi renkleridir. " +
                        "Kumanda tuşları düğmesinden her tuşu ayrı aç/kapat ve işlemini değiştir. " +
                        "DPAD üst/alt yalnızca oynatıcı açıkken ve kontroller gizliyken bölüm değiştirir."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(12), 0, 0)
                },
                matchWidth()
            )
        }

        val scroll = ScrollView(context).apply { addView(body) }
        refreshPreview()

        AlertDialog.Builder(context)
            .setTitle("Font ayarı")
            .setView(scroll)
            .setPositiveButton("Kaydet") { _, _ ->
                FontPreferences.setSpec(context, pendingSpec)
                FontController.refresh()

                if (pendingTheme != initialTheme) {
                    CloudStreamTheme.apply(context, pendingTheme)
                    FontController.recreateAll()
                }

                toast(context, "Font ayarı kaydedildi")
            }
            .setNeutralButton("Sıfırla") { _, _ ->
                FontPreferences.clear(context)
                FontController.reset()
                toast(context, "Varsayılan fonta dönüldü")
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showThemePicker(context: Context, onPick: (String) -> Unit) {
        val values = listOf(
            CloudStreamTheme.SYSTEM,
            CloudStreamTheme.LIGHT,
            CloudStreamTheme.DARK,
            CloudStreamTheme.AMOLED,
        ) + CustomThemes.all.map { CustomThemes.customValue(it) }
        val labels = values.map { CloudStreamTheme.label(it) }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Tema seç")
            .setItems(labels) { _, which -> onPick(values[which]) }
            .show()
    }

    private fun showFontPicker(context: Context, current: String, onPick: (String) -> Unit) {
        val labels = ArrayList<String>()
        val specs = ArrayList<String>()

        labels.add("Sistem varsayılanı")
        specs.add("")

        FontLoader.systemFamilies.forEach { (family, label) ->
            labels.add(label)
            specs.add(FontLoader.systemSpec(family))
        }

        FontLoader.localFontFiles().forEach { file ->
            labels.add("Dosya: ${file.name}")
            specs.add(FontLoader.fileSpec(file.absolutePath))
        }

        labels.add("Dosya yolundan yükle (.ttf / .otf)…")
        labels.add("content:// URI ile içe aktar…")

        val fileIndex = labels.size - 2
        val uriIndex = labels.size - 1

        AlertDialog.Builder(context)
            .setTitle("Font seç")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    fileIndex -> askFilePath(context, current, onPick)
                    uriIndex -> askUri(context, onPick)
                    else -> onPick(specs[which])
                }
            }
            .show()
    }

    private fun askFilePath(context: Context, current: String, onPick: (String) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "/sdcard/fonts/MyFont.ttf"
            if (current.startsWith("file:")) {
                setText(current.removePrefix("file:"))
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Font dosyası yolu")
            .setView(input)
            .setPositiveButton("Yükle") { _, _ ->
                val path = input.text.toString().trim()
                val lower = path.lowercase()

                when {
                    path.isEmpty() -> toast(context, "Yol boş olamaz")

                    !lower.endsWith(".ttf") && !lower.endsWith(".otf") ->
                        toast(context, "Yalnızca .ttf veya .otf destekleniyor")

                    else -> {
                        val spec = FontLoader.fileSpec(path)
                        if (FontLoader.load(context, spec) == null) {
                            toast(context, "Font yüklenemedi; yolu ve dosya iznini kontrol et")
                        } else {
                            onPick(spec)
                        }
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun askUri(context: Context, onPick: (String) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "content://com.example.documents/font/1234"
        }

        AlertDialog.Builder(context)
            .setTitle("content:// URI ile içe aktar")
            .setView(input)
            .setPositiveButton("İçe aktar") { _, _ ->
                val uri = input.text.toString().trim()
                if (uri.isEmpty()) {
                    toast(context, "URI boş olamaz")
                } else {
                    val spec = FontLoader.uriSpec(uri)
                    if (FontLoader.load(context, spec) == null) {
                        toast(context, "Font içe aktarılamadı; URI erişimini kontrol et")
                    } else {
                        onPick(spec)
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
