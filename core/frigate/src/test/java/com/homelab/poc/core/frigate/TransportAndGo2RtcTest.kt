package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportAndGo2RtcTest {

    private val config = FrigateConfig(
        localBaseUrl = "http://frigate:5000",
        tailscaleBaseUrl = "http://frigate:5000",
    )

    @Test
    fun `tailscale transport routes media requests only through the gateway`() = runTest {
        val gateway = RecordingGateway()
        val getter = bytesGetterFor(TransportKind.TAILSCALE, gateway)

        assertTrue("TAILSCALE must use the tsnet getter", getter is TsnetHttpBytesGetter)
        val result = getter.getBytes("http://frigate:5000/api/go2rtc/api/stream.m3u8?src=back", 5000)

        assertEquals("the gateway must serve exactly one request", 1, gateway.httpGetBytesCalls)
        assertEquals("http://frigate:5000/api/go2rtc/api/stream.m3u8?src=back", gateway.lastUrl)
        assertNotNull(result.body)
    }

    @Test
    fun `local transport uses the HttpURLConnection getter and never the gateway`() {
        val gateway = RecordingGateway()
        val getter = bytesGetterFor(TransportKind.LOCAL, gateway)

        assertTrue("LOCAL must use the HttpURLConnection getter", getter is HttpUrlConnectionBytesGetter)
        assertEquals("the gateway must never be used on the local path", 0, gateway.httpGetBytesCalls)
    }

    @Test
    fun `streamNameForCamera resolves the selected camera's stream by exact id`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(
                200,
                "application/json",
                url,
                """{"backyard":{},"hall":{},"garage":{}}""".toByteArray(),
            )
        }
        val streams = Go2RtcStreams(getter)

        assertEquals("backyard", streams.streamNameForCamera(config.tailscaleBaseUrl, "backyard", 5000))
        assertEquals("hall", streams.streamNameForCamera(config.tailscaleBaseUrl, "hall", 5000))
        assertEquals(
            "http://frigate:5000/api/go2rtc/api/stream.m3u8?src=backyard&mp4",
            streams.hlsUrl(config.tailscaleBaseUrl, "backyard"),
        )
    }

    @Test
    fun `streamNameForCamera resolves a second selected camera by its own id`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(
                200,
                "application/json",
                url,
                """{"front_door":{},"backyard":{},"hall":{}}""".toByteArray(),
            )
        }
        val streams = Go2RtcStreams(getter)

        assertEquals(
            "selecting front_door must resolve the front_door stream",
            "front_door",
            streams.streamNameForCamera(config.tailscaleBaseUrl, "front_door", 5000),
        )
        assertEquals(
            "selecting backyard must resolve the backyard stream",
            "backyard",
            streams.streamNameForCamera(config.tailscaleBaseUrl, "backyard", 5000),
        )
    }

    @Test
    fun `streamNameForCamera is independent of the stream list order`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(
                200,
                "application/json",
                url,
                """{"gate":{},"hall":{},"backyard":{}}""".toByteArray(),
            )
        }
        val streams = Go2RtcStreams(getter)

        assertEquals(
            "the selected camera must win regardless of its position in the payload",
            "backyard",
            streams.streamNameForCamera(config.tailscaleBaseUrl, "backyard", 5000),
        )
    }

    @Test
    fun `streamNameForCamera returns null when the camera has no stream`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(200, "application/json", url, """{"backyard":{},"hall":{}}""".toByteArray())
        }
        val streams = Go2RtcStreams(getter)

        assertNull(
            "a camera without a matching stream resolves to nothing, not to another stream",
            streams.streamNameForCamera(config.tailscaleBaseUrl, "front_door", 5000),
        )
    }

    @Test
    fun `streamNameForCamera returns null when no streams exist`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(200, "application/json", url, "{}".toByteArray())
        }
        val streams = Go2RtcStreams(getter)

        assertNull(streams.streamNameForCamera(config.localBaseUrl, "backyard", 5000))
    }

    @Test
    fun `streamNameForCamera propagates a non-2xx response`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(403, "application/json", url, ByteArray(0))
        }
        val streams = Go2RtcStreams(getter)

        var thrown: Exception? = null
        try {
            streams.streamNameForCamera(config.localBaseUrl, "backyard", 5000)
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull("a non-2xx stream listing must raise", thrown)
        assertTrue(thrown!!.message.orEmpty().contains("HTTP 403"))
    }

    @Test
    fun `streamNames returns all top-level stream names in document order`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(
                200,
                "application/json",
                url,
                """{"back":{"name":"back","src":"rtsp://x"},"hall":[1,2],"gate":"rtmp://y"}""".toByteArray(),
            )
        }
        val streams = Go2RtcStreams(getter)

        val names = streams.streamNames(config.tailscaleBaseUrl, 5000)

        assertEquals(setOf("back", "hall", "gate"), names)
    }

    @Test
    fun `streamNames is empty for an empty streams object`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(200, "application/json", url, "{}".toByteArray())
        }

        val names = Go2RtcStreams(getter).streamNames(config.localBaseUrl, 5000)

        assertEquals(emptySet<String>(), names)
    }

    @Test
    fun `streamNames propagates a non-2xx response`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(500, "text/plain", url, ByteArray(0))
        }
        val streams = Go2RtcStreams(getter)

        var thrown: Exception? = null
        try {
            streams.streamNames(config.localBaseUrl, 5000)
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull("a non-2xx streams response must raise", thrown)
        assertTrue(thrown!!.message.orEmpty().contains("HTTP 500"))
    }

    @Test
    fun `go2rtc discovery propagates non-2xx as an error`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(403, "application/json", url, ByteArray(0))
        }
        val streams = Go2RtcStreams(getter)

        var thrown: Exception? = null
        try {
            streams.streamNames(config.localBaseUrl, 5000)
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull("a non-2xx discovery response must raise", thrown)
    }

    @Test
    fun `go2rtc session resolution returns the media playlist URL of the master`() = runTest {
        val master = "#EXTM3U\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=192000,CODECS=\"avc1.64001F,mp4a.40.2\"\n" +
            "hls/playlist.m3u8?id=AbC123"
        var requests = 0
        val getter = HttpBytesGetter { url, _ ->
            requests++
            HttpBytesResult(200, "application/vnd.apple.mpegurl", url, master.toByteArray())
        }
        val streams = Go2RtcStreams(getter)

        val mediaUrl = streams.resolveMediaPlaylistUrl(config.localBaseUrl, "back", 5000)

        assertEquals("session resolution must request the HLS master playlist", 1, requests)
        assertEquals(
            "http://frigate:5000/api/go2rtc/api/hls/playlist.m3u8?id=AbC123",
            mediaUrl,
        )
    }

    @Test
    fun `go2rtc session resolution rejects an empty master body`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(200, "application/vnd.apple.mpegurl", url, ByteArray(0))
        }
        val streams = Go2RtcStreams(getter)

        var thrown: Exception? = null
        try {
            streams.resolveMediaPlaylistUrl(config.localBaseUrl, "back", 5000)
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull("an empty master (go2rtc 200 without a consumer) must raise", thrown)
        assertTrue(thrown!!.message.orEmpty().contains("not an m3u8"))
    }

    @Test
    fun `go2rtc session resolution propagates non-2xx`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(404, "text/plain", url, ByteArray(0))
        }
        val streams = Go2RtcStreams(getter)

        var thrown: Exception? = null
        try {
            streams.resolveMediaPlaylistUrl(config.localBaseUrl, "back", 5000)
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull("a non-2xx master must raise", thrown)
        assertTrue(thrown!!.message.orEmpty().contains("HTTP 404"))
    }

    @Test
    fun `media playlist reference resolves against the master URL`() {
        assertEquals(
            "http://frigate:5000/api/go2rtc/api/hls/playlist.m3u8?id=AbC123",
            Go2RtcStreams.resolveMediaPlaylistUrl(
                "http://frigate:5000/api/go2rtc/api/stream.m3u8?src=back&mp4",
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=192000\nhls/playlist.m3u8?id=AbC123",
            ),
        )
        assertNull(
            Go2RtcStreams.resolveMediaPlaylistUrl(
                "http://frigate:5000/api/go2rtc/api/stream.m3u8?src=back&mp4",
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=192000\n",
            ),
        )
    }

    @Test
    fun `topLevelKeys collects every key, not only the first`() {
        assertEquals(
            setOf("back", "hall", "gate"),
            Go2RtcStreams.topLevelKeys("""{"back":{"name":"back"},"hall":{},"gate":"rtmp://y"}"""),
        )
        assertEquals(emptySet<String>(), Go2RtcStreams.topLevelKeys("{}"))
        assertEquals(emptySet<String>(), Go2RtcStreams.topLevelKeys("[]"))
        assertEquals(
            setOf("a", "b"),
            Go2RtcStreams.topLevelKeys("""{"a":{"nested":{"deep":[1,2,3]}},"b":"x"}"""),
        )
    }

    /** Records how many media/playback requests hit the gateway. */
    private class RecordingGateway : TsnetGateway {
        var httpGetBytesCalls: Int = 0
            private set
        var lastUrl: String? = null
        var onBytes: HttpBytesResult = HttpBytesResult(200, "application/json", "http://frigate:5000/x", ByteArray(0))

        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun httpGet(url: String, timeoutMs: Long): String = "0.17.1"

        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult {
            httpGetBytesCalls++
            lastUrl = url
            return onBytes.copy(finalUrl = url)
        }
    }
}
