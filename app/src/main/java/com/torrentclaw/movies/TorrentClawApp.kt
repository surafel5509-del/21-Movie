package com.torrentclaw.movies

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.torrentclaw.movies.data.download.DownloadManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TorrentClawApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()
        // A download that was interrupted by a process death picks up where it
        // stopped: HTTP transfers from their byte count, torrents by rejoining
        // the swarm (libtorrent re-checks what is already on disk).
        downloadManager.resumePending()
    }

    /**
     * Poster art dominates bandwidth in this app, so Coil gets an explicit
     * memory + disk cache sized for a phone and keeps decoded bitmaps at 60 % of
     * available memory instead of the default 25 %.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(220L * 1024 * 1024)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(220)
        .respectCacheHeaders(false)
        .build()
}
