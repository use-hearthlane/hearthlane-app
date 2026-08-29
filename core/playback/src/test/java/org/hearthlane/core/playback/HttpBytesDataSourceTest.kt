package org.hearthlane.core.playback

import org.hearthlane.core.connectivity.HttpBytesResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HttpBytesDataSourceTest {

    @Test
    fun `non-m3u8 master payload is rejected with a body preview`() {
        var thrown: Exception? = null
        try {
            HttpBytesDataSource.validatePayload(
                "http://frigate:5000/api/go2rtc/api/stream.m3u8?src=back&mp4",
                HttpBytesResult(
                    200,
                    "application/vnd.apple.mpegurl",
                    "http://frigate:5000/api/go2rtc/api/stream.m3u8?src=back&mp4",
                    ByteArray(0),
                ),
            )
        } catch (e: IOException) {
            thrown = e
        }
        assertNotNull("an empty 2xx master must raise", thrown)
        assertTrue(thrown!!.message.orEmpty().contains("HLS manifest is not an m3u8 playlist"))
    }

    @Test
    fun `empty 2xx media segment is rejected`() {
        var thrown: Exception? = null
        try {
            HttpBytesDataSource.validatePayload(
                "http://frigate:5000/api/go2rtc/api/hls/segment.m4s?id=AbC123&n=0",
                HttpBytesResult(
                    200,
                    "video/iso.segment",
                    "http://frigate:5000/api/go2rtc/api/hls/segment.m4s?id=AbC123&n=0",
                    ByteArray(0),
                ),
            )
        } catch (e: IOException) {
            thrown = e
        }
        assertNotNull("an empty 2xx segment must raise", thrown)
        assertTrue(thrown!!.message.orEmpty().contains("empty"))
    }

    @Test
    fun `valid manifest and non-empty media payloads pass`() {
        HttpBytesDataSource.validatePayload(
            "http://frigate:5000/api/go2rtc/api/hls/playlist.m3u8?id=AbC123",
            HttpBytesResult(
                200,
                "application/vnd.apple.mpegurl",
                "http://frigate:5000/api/go2rtc/api/hls/playlist.m3u8?id=AbC123",
                "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n".toByteArray(),
            ),
        )
        HttpBytesDataSource.validatePayload(
            "http://frigate:5000/api/go2rtc/api/hls/init.mp4?id=AbC123",
            HttpBytesResult(
                200,
                "video/mp4",
                "http://frigate:5000/api/go2rtc/api/hls/init.mp4?id=AbC123",
                byteArrayOf(0, 0, 0, 1),
            ),
        )
    }
}
