package com.mooncrown04.m3u

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.Plugin
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Ayarlardan eklenen M3U listelerini saklar ve her liste icin ayri bir
 * saglayiciyi (provider) CloudStream'e kaydeder.
 *
 * Boylece CloudStream'in saglayici listesinde "Spor", "Haber" gibi ayri
 * eklenti girdileri olusur; her M3U ayri bir eklenti gibi davranir.
 */
object M3UStore {

    private const val PREFS_NAME = "m3u_plugin_store"
    private const val KEY_PLAYLISTS = "playlists"

    data class Playlist(
        val id: String = "",
        val name: String = "",
        val url: String = "",
    )

    @Volatile
    private var prefs: SharedPreferences? = null

    private var plugin: Plugin? = null

    /** Kayitli saglayicilar: playlist id -> provider */
    private val active = LinkedHashMap<String, M3UProvider>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun bind(plugin: Plugin) {
        this.plugin = plugin
    }

    fun playlists(): List<Playlist> {
        val raw = prefs?.getString(KEY_PLAYLISTS, null)
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").trim()
                val name = item.optString("name").trim()
                if (url.isEmpty() || name.isEmpty()) {
                    null
                } else {
                    Playlist(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        url = url,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(name: String, url: String) {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            url = url.trim(),
        )
        save(playlists() + playlist)
    }

    fun remove(id: String) {
        save(playlists().filterNot { it.id == id })
    }

    private fun save(list: List<Playlist>) {
        val array = JSONArray()
        list.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("url", playlist.url)
            )
        }
        prefs?.edit()?.putString(KEY_PLAYLISTS, array.toString())?.apply()
    }

    /**
     * Kayitli listelerle calisan saglayicilari esitler:
     * yeni listeleri kaydeder, silinenlerin saglayicisini kaldirir.
     */
    fun sync() {
        val host = plugin ?: return
        val saved = playlists()
        val savedIds = saved.map { it.id }.toSet()

        active.entries.filter { it.key !in savedIds }.toList().forEach { (id, provider) ->
            runCatching { APIHolder.allProviders.remove(provider) }
            runCatching { APIHolder.removePluginMapping(provider) }
            active.remove(id)
        }

        saved.forEach { playlist ->
            val existing = active[playlist.id]
            if (existing == null) {
                val provider = M3UProvider(playlist)
                active[playlist.id] = provider
                runCatching { host.registerMainAPI(provider) }
            } else {
                existing.applyPlaylist(playlist)
            }
        }
    }
}
