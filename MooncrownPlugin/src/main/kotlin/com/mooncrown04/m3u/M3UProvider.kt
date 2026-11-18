package com.mooncrown04.m3u

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Tek bir M3U listesi icin saglayici.
 *
 * Kategoriler "dizi" gibi davranir:
 *  - Ana sayfada her kategori bir dizi kartidir.
 *  - Kategoriye girince listenin kanallari bolum listesi olur (1. bolum, 2. bolum ...).
 *  - Oynat icindeyken kumandanin yukari/asagi tuslari (FontPlugin'de "onceki
 *    bolum / sonraki bolum" olarak atanmis) kanal degistirir.
 */
class M3UProvider(playlist: M3UStore.Playlist) : MainAPI() {

    override var name = playlist.name
    override var mainUrl = playlist.url
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    // Bolume basinca ilk kaynak dogrudan acilsin (canli yayin).
    override val instantLinkLoading = true

    private var cached: List<M3UEntry> = emptyList()
    private var cachedAt = 0L

    fun applyPlaylist(playlist: M3UStore.Playlist) {
        name = playlist.name
        mainUrl = playlist.url
        cached = emptyList()
        cachedAt = 0L
    }

    private suspend fun loadPlaylist(force: Boolean = false): List<M3UEntry> {
        val now = System.currentTimeMillis()
        if (!force && cached.isNotEmpty() && now - cachedAt < CACHE_MS) return cached

        val parsed = runCatching { M3UParser.parse(readSource()) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) {
            cached = parsed
            cachedAt = now
            return parsed
        }
        return cached
    }

    private fun readSource(): String {
        val source = mainUrl.trim()
        val bytes: ByteArray
        val contentType: String?

        if (source.startsWith("/") || source.startsWith("file://")) {
            bytes = if (source.startsWith("file://")) File(URI(source)).readBytes() else File(source).readBytes()
            contentType = null
        } else {
            val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                bytes = stream?.use { it.readBytes() }
                    ?: throw IllegalStateException("HTTP $code")
                contentType = connection.contentType
            } finally {
                connection.disconnect()
            }
        }

        return decode(bytes, contentType)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val groups = grouped()

        if (groups.isEmpty()) {
            return newHomePageResponse(
                listOf(HomePageList("Liste boş", emptyList(), true)),
                hasNext = false
            )
        }

        val cards = groups.map { (group, items) -> categoryCard(group, items) }

        return newHomePageResponse(
            listOf(HomePageList("Kategoriler", cards, true)),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        return grouped()
            .filter { (group, items) ->
                group.contains(needle, ignoreCase = true) ||
                    items.any { it.name.contains(needle, ignoreCase = true) }
            }
            .map { (group, items) -> categoryCard(group, items) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val ref = CategoryJson.decode(url) ?: CategoryRef(group = url.trim())
        val group = ref.group.ifBlank { M3UParser.DEFAULT_GROUP }

        val items = grouped()[group].orEmpty()
        val poster = ref.poster ?: items.firstNotNullOfOrNull { it.logo }

        val episodes = items.mapIndexed { index, entry ->
            newEpisode(ChannelJson.encode(entry.toChannelRef())) {
                this.name = entry.name.ifBlank { "Kanal ${index + 1}" }
                this.episode = index + 1
                this.season = 1
                this.posterUrl = entry.logo
                this.description = group
            }
        }

        return newTvSeriesLoadResponse(group, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = if (items.isEmpty()) {
                "Bu kategoride kanal bulunamadı."
            } else {
                "${items.size} canlı kanal • kumandada yukarı/aşağı kanal değiştirir"
            }
            this.seasonNames = listOf(SeasonData(1, group))
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val ref = ChannelJson.decode(data) ?: return false
        val linkUrl = ref.url.trim()
        if (linkUrl.isEmpty()) return false

        val type = when {
            linkUrl.contains(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
            linkUrl.contains(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
            linkUrl.contains(".mp4", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> INFER_TYPE
        }

        val link = newExtractorLink(
            source = this.name,
            name = ref.name.ifBlank { this.name },
            url = linkUrl,
            type = type,
        ) {
            this.referer = ref.headers["Referer"].orEmpty()
            this.quality = Qualities.Unknown.value
            this.headers = ref.headers
        }
        callback.invoke(link)
        return true
    }

    /** Kategori adi -> o kategorideki kanallar (liste sirasi korunur). */
    private suspend fun grouped(): LinkedHashMap<String, List<M3UEntry>> {
        val groups = LinkedHashMap<String, MutableList<M3UEntry>>()
        loadPlaylist().forEach { entry ->
            val group = entry.group.ifBlank { M3UParser.DEFAULT_GROUP }
            groups.getOrPut(group) { mutableListOf() }.add(entry)
        }
        return LinkedHashMap(groups)
    }

    private fun categoryCard(group: String, items: List<M3UEntry>): SearchResponse {
        val poster = items.firstNotNullOfOrNull { it.logo }
        return newTvSeriesSearchResponse(
            group,
            CategoryJson.encode(group, poster),
            TvType.TvSeries,
            false,
        ) {
            this.posterUrl = poster
            this.episodes = items.size
        }
    }

    private fun M3UEntry.toChannelRef(): ChannelRef = ChannelRef(
        url = url,
        name = name.ifBlank { "Kanal" },
        group = group.ifBlank { M3UParser.DEFAULT_GROUP },
        poster = logo,
        headers = headers,
    )

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = Regex("""charset=([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(contentType.orEmpty())
            ?.groupValues
            ?.getOrNull(1)

        if (declared != null) {
            runCatching { return String(bytes, charset(declared.trim())) }
        }

        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8

        return runCatching { String(bytes, charset("windows-1254")) }.getOrDefault(utf8)
    }

    private companion object {
        const val CACHE_MS = 10 * 60 * 1000L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) CloudStream"
    }
}

/** SearchResponse.url icinde tasinan kategori bilgisinin JSON karsiligi. */
internal data class CategoryRef(
    val group: String = "",
    val poster: String? = null,
)

internal object CategoryJson {

    fun encode(group: String, poster: String?): String = JSONObject().apply {
        put("group", group)
        put("poster", poster.orEmpty())
    }.toString()

    fun decode(json: String): CategoryRef? = runCatching {
        val obj = JSONObject(json)
        CategoryRef(
            group = obj.optString("group"),
            poster = obj.optString("poster").ifBlank { null },
        )
    }.getOrNull()
}

/** SearchResponse.url icinde tasinan kanal bilgisinin JSON karsiligi. */
internal object ChannelJson {

    fun encode(ref: ChannelRef): String = JSONObject().apply {
        put("url", ref.url)
        put("name", ref.name)
        put("group", ref.group)
        put("poster", ref.poster.orEmpty())
        put("headers", JSONObject(ref.headers as Map<*, *>))
    }.toString()

    fun decode(json: String): ChannelRef? = runCatching {
        val obj = JSONObject(json)
        val headersObj = obj.optJSONObject("headers")
        val headers = if (headersObj == null) {
            emptyMap()
        } else {
            headersObj.keys().asSequence().associateWith { headersObj.optString(it) }
        }

        ChannelRef(
            url = obj.optString("url"),
            name = obj.optString("name"),
            group = obj.optString("group"),
            poster = obj.optString("poster").ifBlank { null },
            headers = headers,
        )
    }.getOrNull()
}
