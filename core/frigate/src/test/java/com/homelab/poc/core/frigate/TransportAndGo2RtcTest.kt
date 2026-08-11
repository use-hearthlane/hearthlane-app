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
        val result = getter.getBytes("http://frigate:5000/go2rtc/api/stream.m3u8?src=back", 5000)

        assertEquals("the gateway must serve exactly one request", 1, gateway.httpGetBytesCalls)
        assertEquals("http://frigate:5000/go2rtc/api/stream.m3u8?src=back", gateway.lastUrl)
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
    fun `go2rtc discovery parses the first stream name in document order`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(
                200,
                "application/json",
                url,
                """{"back":{},"hall":{},"garage":{}}""".toByteArray(),
            )
        }
        val streams = Go2RtcStreams(getter)

        val name = streams.firstStreamName(config.tailscaleBaseUrl, 5000)

        assertEquals("back", name)
        assertEquals(
            "http://frigate:5000/go2rtc/api/stream.m3u8?src=back&mp4",
            streams.hlsUrl(config.tailscaleBaseUrl, "back"),
        )
    }

    @Test
    fun `go2rtc discovery reports no streams as null`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(200, "application/json", url, "{}".toByteArray())
        }
        val streams = Go2RtcStreams(getter)

        assertNull(streams.firstStreamName(config.localBaseUrl, 5000))
    }

    @Test
    fun `go2rtc discovery propagates non-2xx as an error`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(403, "application/json", url, ByteArray(0))
        }
        val streams = Go2RtcStreams(getter)

        var thrown: Exception? = null
        try {
            streams.firstStreamName(config.localBaseUrl, 5000)
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull("a non-2xx discovery response must raise", thrown)
    }

    @Test
    fun `firstTopLevelKey is deterministic across depth and value shapes`() {
        assertEquals(
            "back",
            Go2RtcStreams.firstTopLevelKey("""{"back":{"name":"back","src":"rtsp://x"},"hall":[1,2],"gate":"rtmp://y"}"""),
        )
        assertNull(Go2RtcStreams.firstTopLevelKey("{}"))
        assertNull(Go2RtcStreams.firstTopLevelKey("[]"))
        assertEquals("only", Go2RtcStreams.firstTopLevelKey("""{"only":"string-value"}"""))
        assertEquals("num", Go2RtcStreams.firstTopLevelKey("""{"num":42}"""))
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
