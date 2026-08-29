package org.hearthlane.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.hearthlane.core.connectivity.HttpStream
import org.hearthlane.core.connectivity.HttpStreamGetter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Validates the Media3 pipeline construction:
 *
 * StreamingHttpDataSourceFactory → ProgressiveMediaSource.Factory → MediaItem/URI
 *
 * The factory must be accepted by Media3 and create the correct DataSource. No
 * real video is played (a JVM/Robolectric test cannot run the full extractor
 * pipeline); this proves the contract wiring Media3 requires.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamingHttpDataSourceMedia3IntegrationTest {

    private val clipUrl = "http://frigate:5000/api/events/evt-1/clip.mp4"

    @Test
    fun `ProgressiveMediaSource accepts the streaming factory and builds a source`() {
        val streamingFactory = StreamingHttpDataSourceFactory(RecordingGetter(), 2_000)

        val sourceFactory = ProgressiveMediaSource.Factory(streamingFactory)
        val source = sourceFactory.createMediaSource(MediaItem.fromUri(clipUrl))

        assertTrue(
            "the pipeline must produce a ProgressiveMediaSource",
            source is ProgressiveMediaSource,
        )
        assertEquals(
            "the source must carry the clip URI",
            clipUrl,
            source.getMediaItem().localConfiguration!!.uri.toString(),
        )
    }

    @Test
    fun `the factory creates a StreamingHttpDataSource`() {
        val streamingFactory = StreamingHttpDataSourceFactory(RecordingGetter(), 2_000)

        val dataSource = streamingFactory.createDataSource()

        assertTrue(dataSource is StreamingHttpDataSource)
    }

    private class RecordingGetter : HttpStreamGetter {
        override suspend fun open(url: String, connectTimeoutMs: Long): HttpStream =
            throw IOException("no network in this test; the pipeline is validated at construction time")
    }
}