package com.mooncrown04.fontplugin

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Font spec -> Typeface. Sonuc (null dahil) bellekte cache'lenir; boylece her
 * activity resume'unda disk/URI okunmaz.
 */
object FontLoader {

    private const val SYSTEM_PREFIX = "system:"
    private const val FILE_PREFIX = "file:"
    private const val URI_PREFIX = "uri:"

    /** Ayarlarda listelenecek sistem font aileleri: spec degeri -> gorunen ad. */
    val systemFamilies: List<Pair<String, String>> = listOf(
        "sans-serif" to "Sans Serif",
        "sans-serif-condensed" to "Sans Serif Condensed",
        "sans-serif-medium" to "Sans Serif Medium",
        "serif" to "Serif",
        "monospace" to "Monospace",
        "casual" to "Casual",
        "cursive" to "Cursive",
    )

    private val cache = HashMap<String, Typeface?>()

    fun load(context: Context, spec: String?): Typeface? {
        if (spec.isNullOrBlank()) return null
        if (cache.containsKey(spec)) return cache[spec]

        val loaded = runCatching { loadUncached(context, spec) }.getOrNull()
        cache[spec] = loaded
        return loaded
    }

    private fun loadUncached(context: Context, spec: String): Typeface? = when {
        spec.startsWith(SYSTEM_PREFIX) -> {
            val family = spec.removePrefix(SYSTEM_PREFIX)
            if (systemFamilies.any { it.first == family }) {
                Typeface.create(family, Typeface.NORMAL)
            } else {
                null
            }
        }

        spec.startsWith(FILE_PREFIX) -> {
            val file = File(spec.removePrefix(FILE_PREFIX))
            if (file.isFile && file.canRead()) Typeface.createFromFile(file) else null
        }

        spec.startsWith(URI_PREFIX) -> importFromUri(context, spec.removePrefix(URI_PREFIX))

        else -> null
    }

    /**
     * content:// URI'sini uygulamanin ozel dizinine kopyalar ve oradan yukler.
     * Boylece URI izni kalici olmasa bile font okunabilir kalir.
     */
    private fun importFromUri(context: Context, uriString: String): Typeface? {
        val target = File(context.filesDir, "imported_font_${uriString.hashCode()}.ttf")
        if (!target.exists()) {
            val input = context.contentResolver.openInputStream(Uri.parse(uriString))
                ?: return null
            input.use { source ->
                target.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            }
        }
        return Typeface.createFromFile(target)
    }

    /**
     * TV box / telefon icin pratik: kullanicinin klasore attigi fontlari listeler.
     * CloudStream'in all-files izni varsa bu klasorler okunabilir.
     */
    fun localFontFiles(): List<File> {
        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "Cloudstream3/fonts"),
            File(Environment.getExternalStorageDirectory(), "Cloudstream3/plugins"),
        )

        return roots
            .flatMap { dir ->
                dir.listFiles()?.filter { file ->
                    file.isFile && file.canRead() &&
                        (file.name.endsWith(".ttf", true) || file.name.endsWith(".otf", true))
                } ?: emptyList()
            }
            .distinctBy { it.absolutePath }
            .sortedBy { it.name.lowercase() }
    }

    fun displayName(spec: String?): String = when {
        spec.isNullOrBlank() -> "Sistem varsayılanı"
        spec.startsWith(SYSTEM_PREFIX) -> systemFamilies
            .firstOrNull { it.first == spec.removePrefix(SYSTEM_PREFIX) }
            ?.second
            ?: spec.removePrefix(SYSTEM_PREFIX)

        spec.startsWith(FILE_PREFIX) -> File(spec.removePrefix(FILE_PREFIX)).name
        spec.startsWith(URI_PREFIX) -> "İçe aktarılan font"
        else -> spec
    }

    fun systemSpec(family: String): String = SYSTEM_PREFIX + family
    fun fileSpec(path: String): String = FILE_PREFIX + path
    fun uriSpec(uri: String): String = URI_PREFIX + uri

    fun clearCache() {
        cache.clear()
    }
}
