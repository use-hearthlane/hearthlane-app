package com.homelab.poc.thumbnail

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache

/**
 * Builds the Coil [ImageLoader] used for Frigate camera thumbnails.
 *
 * The loader is configured with a custom [FrigateSnapshotFetcher] and a stable
 * [FrigateSnapshotKeyer]. Every snapshot request travels through the app's
 * transport-scoped HTTP getter, and thumbnails are held only in memory cache.
 * Disk cache is intentionally disabled in V1.3; persistent snapshot caching is
 * deferred to a later milestone.
 */
object FrigateSnapshotImageLoader {

    fun create(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(FrigateSnapshotKeyer())
                add(FrigateSnapshotFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .build()
}
