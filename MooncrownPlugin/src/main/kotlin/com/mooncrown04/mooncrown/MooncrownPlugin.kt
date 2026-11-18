package com.mooncrown04.mooncrown

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.mooncrown04.fontplugin.FontController
import com.mooncrown04.fontplugin.FontSettingsDialog
import com.mooncrown04.fontplugin.FontSettingsEntry
import com.mooncrown04.m3u.M3UStore
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Birlesik eklenti: FontPlugin (v13) + M3UPlugin (v2).
 *
 * - Font/tema/kumanda tuslari bolumu FontController uzerinden calisir.
 * - M3U listeleri eklenti ayarlarindan eklenir; her liste ayri bir saglayici olur,
 *   her kategori bir dizi karti, kanallar bolum olarak listelenir.
 * - Disli (openSettings) birlesik ayar ekranini acar.
 */
@CloudstreamPlugin
class MooncrownPlugin : Plugin() {

    override fun load(context: Context) {
        // 1) Font, tema ve kumanda tuslari
        try {
            FontSettingsEntry.setOpener { settingsContext ->
                FontSettingsDialog.show(settingsContext)
            }
            FontController.install(context)
            Log.i(TAG, "Font bolumu yuklendi")
        } catch (t: Throwable) {
            Log.e(TAG, "Font bolumu yuklenemedi", t)
        }

        // 2) M3U listeleri (ayarlardan eklenen her liste ayri saglayici)
        try {
            M3UStore.init(context)
            M3UStore.bind(this)
            M3UStore.sync()

            if (M3UStore.playlists().isEmpty()) {
                Toast.makeText(
                    context,
                    "M3U: liste eklemek için eklenti ayarlarını (dişli) açın",
                    Toast.LENGTH_LONG
                ).show()
            }
            Log.i(TAG, "M3U bolumu yuklendi")
        } catch (t: Throwable) {
            Log.e(TAG, "M3U bolumu yuklenemedi", t)
        }

        // 3) Tek birlesik ayar ekrani
        openSettings = { settingsContext ->
            MooncrownSettingsDialog.show(settingsContext)
        }

        Log.i(TAG, "MooncrownPlugin loaded")
    }

    private companion object {
        const val TAG = "MooncrownPlugin"
    }
}
