package com.mooncrown04.m3u

/** M3U listesindeki tek bir kanal. */
data class M3UEntry(
    val name: String = "",
    val url: String = "",
    val group: String = "",
    val logo: String? = null,
    val tvgId: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/** SearchResponse.url icinde tasinan, oynatma icin gereken bilgiler. */
data class ChannelRef(
    val url: String = "",
    val name: String = "",
    val group: String = "",
    val poster: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Basit ama saglam bir M3U / M3U8 (playlist) cozucu.
 *
 * Desteklenenler:
 *  - #EXTM3U basligi (opsiyonel; basliksiz duz listeler de okunur)
 *  - #EXTINF satiri: group-title, tvg-logo, tvg-id, tvg-name
 *  - #EXTGRP: kategori satiri
 *  - #EXTVLCOPT: http-user-agent, http-referrer
 *  - URL sonundaki "|User-Agent=...&Referer=..." parametreleri
 */
object M3UParser {

    const val DEFAULT_GROUP = "Diğer"

    private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(content: String): List<M3UEntry> {
        val result = ArrayList<M3UEntry>()
        var pending: Pending? = null
        var extGroup: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = Pending(line)
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substringAfter(':').trim().ifBlank { null }
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val current = pending
                    if (current != null) {
                        val option = line.substringAfter(':', "").trim()
                        val key = option.substringBefore('=').trim().lowercase()
                        val value = option.substringAfter('=', "").trim()
                        if (value.isNotEmpty()) {
                            when (key) {
                                "http-user-agent" -> current.headers["User-Agent"] = value
                                "http-referrer", "http-referer" -> current.headers["Referer"] = value
                            }
                        }
                    }
                }

                line.startsWith("#") -> Unit

                else -> {
                    val url = line.substringBefore('|').trim().removeSurrounding("\"")
                    if (url.isEmpty()) {
                        pending = null
                        return@forEach
                    }

                    val pipeParams = pipeParams(line.substringAfter('|', ""))
                    val headers = LinkedHashMap<String, String>()
                    pending?.headers?.let { headers.putAll(it) }
                    pipeParams["user-agent"]?.let { headers["User-Agent"] = it }
                    (pipeParams["referer"] ?: pipeParams["referrer"])?.let { headers["Referer"] = it }

                    val attrs = pending?.attrs ?: emptyMap()
                    val group = attrs["group-title"].orEmpty().ifBlank { extGroup ?: DEFAULT_GROUP }

                    result.add(
                        M3UEntry(
                            name = pending?.title.orEmpty().ifBlank { nameFromUrl(url) },
                            url = url,
                            group = group,
                            logo = attrs["tvg-logo"].orEmpty().ifBlank { null },
                            tvgId = attrs["tvg-id"].orEmpty().ifBlank { null },
                            headers = headers,
                        )
                    )
                    pending = null
                    extGroup = null
                }
            }
        }

        return result
    }

    private class Pending(extinf: String) {
        val attrs: Map<String, String> = ATTR_REGEX
            .findAll(extinf)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

        val title: String = titleOf(extinf)

        val headers = LinkedHashMap<String, String>()
    }

    /** "#EXTINF:-1 tvg-id="x",Kanal Adı" -> "Kanal Adı" */
    private fun titleOf(extinf: String): String {
        val lastQuote = extinf.lastIndexOf('"')
        val comma = extinf.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
        return if (comma >= 0) extinf.substring(comma + 1).trim() else ""
    }

    private fun pipeParams(params: String): Map<String, String> {
        if (params.isBlank()) return emptyMap()
        return params.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "").trim().lowercase()
                val value = part.substringAfter('=', "").trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toMap()
    }

    private fun nameFromUrl(url: String): String {
        val fileName = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        return withoutExtension.ifBlank { "Kanal" }
    }
}
