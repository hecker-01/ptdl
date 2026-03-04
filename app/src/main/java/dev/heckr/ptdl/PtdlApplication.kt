package dev.heckr.ptdl

import android.app.Application
import android.net.Uri
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.settings.SettingsManager

class PtdlApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure Coil with a generous disk cache so images load instantly
        // on repeat visits without hitting the file system again
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .build()
            }
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)

        // Pre-warm repository caches on app start
        val prefs = getSharedPreferences("ptdl_prefs", MODE_PRIVATE)
        val rootUriStr = prefs.getString(SettingsManager.KEY_ROOT_URI, "") ?: ""
        if (rootUriStr.isNotBlank()) {
            PatreonRepository.warmUp(this, Uri.parse(rootUriStr))
        }
    }
}
