package com.mooncrown04.fontplugin

/** Bir kumanda tusuna atanabilecek islemler. */
enum class KeyAction(
    val id: String,
    val label: String,
    val navId: String? = null,
    val episode: EpisodeDirection? = null,
) {
    NONE("none", "Kapalı"),
    HOME("home", "Ana sayfa", "navigation_home"),
    SEARCH("search", "Arama", "navigation_search"),
    QUICK_SEARCH("quick_search", "Hızlı arama", "navigation_quick_search"),
    SETTINGS("settings", "Ayarlar", "navigation_settings"),
    GENERAL_SETTINGS("general", "Genel ayarlar", "navigation_settings_general"),
    EXTENSIONS("extensions", "Depo / eklentiler", "navigation_settings_extensions"),
    ACCOUNT("account", "Hesap", "navigation_settings_account"),
    PREV_EPISODE("prev_episode", "Önceki bölüm", episode = EpisodeDirection.PREV),
    NEXT_EPISODE("next_episode", "Sonraki bölüm", episode = EpisodeDirection.NEXT);

    companion object {
        fun fromId(id: String?): KeyAction? = entries.firstOrNull { it.id == id }
    }
}

enum class EpisodeDirection { PREV, NEXT }

/** Bir fiziksel tus ve varsayilan davranisi. */
data class KeySpec(
    val code: Int,
    val label: String,
    val defaultAction: KeyAction,
    val defaultEnabled: Boolean = true,
)
