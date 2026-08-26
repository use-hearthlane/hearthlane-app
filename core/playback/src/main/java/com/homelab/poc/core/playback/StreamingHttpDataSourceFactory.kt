package com.homelab.poc.core.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.homelab.poc.core.connectivity.HttpStreamGetter

/**
 * [DataSource.Factory] that always creates [StreamingHttpDataSource] instances
 * bound to the given [HttpStreamGetter]. Stateless and safe to share across
 * requests; a fresh DataSource is created per playback.
 */
@UnstableApi
class StreamingHttpDataSourceFactory(
    private val getter: HttpStreamGetter,
    private val connectTimeoutMs: Long,
    private val onBytes: (Long) -> Unit = {},
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        StreamingHttpDataSource(getter, connectTimeoutMs, onBytes)
}