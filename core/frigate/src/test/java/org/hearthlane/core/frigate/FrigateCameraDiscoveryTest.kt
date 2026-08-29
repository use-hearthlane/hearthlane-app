package org.hearthlane.core.frigate

import org.hearthlane.core.connectivity.HttpBytesGetter
import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.connectivity.HttpUrlConnectionBytesGetter
import org.hearthlane.core.connectivity.TsnetGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateCameraDiscoveryTest {

    private val samplePayload = """
        {
            "version": "0.17.1-416a9b7",
            "cameras": {
                "backyard": {
                    "name": "backyard",
                    "friendly_name": "Quintal dos fundos",
                    "enabled": true
                },
                "hall": {
                    "name": "hall",
                    "friendly_name": "Corredor Lateral",
                    "enabled": true
                },
                "garage": {
                    "name": "garage",
                    "friendly_name": "Garagem",
                    "enabled": false
                },
                "gate": {
                    "name": "gate",
                    "enabled": true
                }
            }
        }
    """.trimIndent()

    @Test
    fun `discovery returns Loaded with enabled cameras only`() = runTest {
        val getter = router(
            configBody = samplePayload,
            streamsBody = """{"backyard":{},"hall":{},"gate":{}}""",
        )

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Loaded)
        val cameras = (result as CameraDiscoveryState.Loaded).cameras
        assertEquals(3, cameras.size)
        assertEquals(
            listOf("backyard", "hall", "gate"),
            cameras.map { it.id },
        )
        assertEquals(
            listOf("Quintal dos fundos", "Corredor Lateral", "gate"),
            cameras.map { it.displayName },
        )
        cameras.forEach { assertTrue(it.enabled) }
        cameras.forEach { assertTrue("every discovered stream must be playable", it.playable) }
        assertNull((result as CameraDiscoveryState.Loaded).streamsWarning)
    }

    @Test
    fun `enabled camera without a matching stream is playable=false and kept`() = runTest {
        val configBody = """{"cameras":{"backyard":{"enabled":true},"hall":{"enabled":true}}}"""
        val getter = router(configBody = configBody, streamsBody = """{"backyard":{}}""")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Loaded)
        val cameras = (result as CameraDiscoveryState.Loaded).cameras
        assertEquals(2, cameras.size)
        assertTrue(cameras.first { it.id == "backyard" }.playable)
        assertFalse("hall has no stream and must stay with playable=false", cameras.first { it.id == "hall" }.playable)
        assertNull((result as CameraDiscoveryState.Loaded).streamsWarning)
    }

    @Test
    fun `multiple cameras with a subset of streams resolve playable per camera`() = runTest {
        val configBody = """
            {"cameras":{
                "backyard":{"enabled":true},
                "hall":{"enabled":true},
                "gate":{"enabled":true}
            }}
        """.trimIndent()
        val getter = router(configBody = configBody, streamsBody = """{"hall":{},"gate":{}}""")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Loaded)
        val cameras = (result as CameraDiscoveryState.Loaded).cameras.associateBy { it.id }
        assertFalse("backyard has no stream", cameras["backyard"]!!.playable)
        assertTrue(cameras["hall"]!!.playable)
        assertTrue(cameras["gate"]!!.playable)
    }

    @Test
    fun `matching is by camera id not by stream order or first stream`() = runTest {
        val configBody = """{"cameras":{"backyard":{"enabled":true}}}"""
        val getter = router(configBody = configBody, streamsBody = """{"gate":{},"hall":{},"backyard":{}}""")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Loaded)
        val cameras = (result as CameraDiscoveryState.Loaded).cameras
        assertEquals(1, cameras.size)
        assertTrue(
            "backyard must be playable even though it is not the first stream",
            cameras.single().playable,
        )
    }

    @Test
    fun `disabled camera is excluded even when its stream exists`() = runTest {
        val configBody = """{"cameras":{"backyard":{"enabled":true},"garage":{"enabled":false}}}"""
        val getter = router(configBody = configBody, streamsBody = """{"backyard":{},"garage":{}}""")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Loaded)
        assertEquals(
            listOf("backyard"),
            (result as CameraDiscoveryState.Loaded).cameras.map { it.id },
        )
    }

    @Test
    fun `discovery returns Empty when no cameras are configured`() = runTest {
        val getter = router(configBody = """{"cameras": {}}""")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertEquals(CameraDiscoveryState.Empty, result)
    }

    @Test
    fun `discovery returns Empty when all cameras are disabled`() = runTest {
        val getter = router(
            configBody = """{"cameras":{"garage":{"friendly_name":"Garagem","enabled":false}}}""",
        )

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertEquals(CameraDiscoveryState.Empty, result)
    }

    @Test
    fun `go2rtc streams empty yields Loaded with playable=false`() = runTest {
        val configBody = """{"cameras":{"backyard":{"enabled":true},"hall":{"enabled":true}}}"""
        val getter = router(configBody = configBody, streamsBody = "{}")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue("an empty streams payload must not turn discovery into Empty", result is CameraDiscoveryState.Loaded)
        val cameras = (result as CameraDiscoveryState.Loaded).cameras
        assertEquals(2, cameras.size)
        cameras.forEach { assertFalse("no streams means no playable camera", it.playable) }
        assertNull("an empty streams payload is not a failure", (result as CameraDiscoveryState.Loaded).streamsWarning)
    }

    @Test
    fun `go2rtc streams failure preserves cameras as playable=false with a warning`() = runTest {
        val configBody = """{"cameras":{"backyard":{"enabled":true}}}"""
        val getter = HttpBytesGetter { url, _ ->
            when {
                url.endsWith("/api/config") ->
                    HttpBytesResult(200, "application/json", url, configBody.toByteArray())
                else -> HttpBytesResult(500, "text/plain", url, ByteArray(0))
            }
        }

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(
            "a streams endpoint failure must not lose the discovered cameras",
            result is CameraDiscoveryState.Loaded,
        )
        val loaded = result as CameraDiscoveryState.Loaded
        assertEquals(1, loaded.cameras.size)
        assertFalse(loaded.cameras.single().playable)
        assertTrue("the failure must be recorded for diagnostics", loaded.streamsWarning != null)
        assertTrue(loaded.streamsWarning!!.contains("HTTP 500"))
    }

    @Test
    fun `go2rtc streams getter throw preserves cameras as playable=false with a warning`() = runTest {
        val configBody = """{"cameras":{"backyard":{"enabled":true}}}"""
        val getter = HttpBytesGetter { url, _ ->
            when {
                url.endsWith("/api/config") ->
                    HttpBytesResult(200, "application/json", url, configBody.toByteArray())
                else -> error("streams connection refused")
            }
        }

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Loaded)
        val loaded = result as CameraDiscoveryState.Loaded
        assertEquals(1, loaded.cameras.size)
        assertFalse(loaded.cameras.single().playable)
        assertTrue(loaded.streamsWarning!!.contains("streams connection refused"))
    }

    @Test
    fun `streams endpoint is not queried when no enabled cameras exist`() = runTest {
        var requests = mutableListOf<String>()
        val getter = HttpBytesGetter { url, _ ->
            requests.add(url)
            HttpBytesResult(200, "application/json", url, """{"cameras":{}}""".toByteArray())
        }

        FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertEquals(listOf("http://frigate:5000/api/config"), requests)
    }

    @Test
    fun `discovery returns Error on non-2xx response`() = runTest {
        val getter = HttpBytesGetter { url, _ ->
            HttpBytesResult(403, "application/json", url, ByteArray(0))
        }

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Error)
        assertTrue((result as CameraDiscoveryState.Error).message.contains("HTTP 403"))
    }

    @Test
    fun `discovery returns Error when the getter throws`() = runTest {
        val getter = HttpBytesGetter { _, _ -> error("connection refused") }

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Error)
        assertTrue((result as CameraDiscoveryState.Error).message.contains("connection refused"))
    }

    @Test
    fun `discovery returns Error on an invalid payload`() = runTest {
        val getter = router(configBody = "not json")

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue(result is CameraDiscoveryState.Error)
        assertTrue((result as CameraDiscoveryState.Error).message.contains("invalid Frigate config"))
    }

    @Test
    fun `discovery requests the config endpoint`() = runTest {
        var requested: String? = null
        val getter = HttpBytesGetter { url, _ ->
            requested = url
            HttpBytesResult(200, "application/json", url, "{}".toByteArray())
        }

        FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertEquals("http://frigate:5000/api/config", requested)
    }

    @Test
    fun `LOCAL discovery path never touches the Tailscale gateway`() = runTest {
        val gateway = RecordingGateway("{}", "{}")
        val getter = bytesGetterFor(TransportKind.LOCAL, gateway)

        assertTrue("LOCAL must use the OS-network getter", getter is HttpUrlConnectionBytesGetter)
        assertEquals("gateway must not be used on the LOCAL discovery path", 0, gateway.httpGetBytesCalls)
    }

    @Test
    fun `TAILSCALE discovery routes config and streams through the existing gateway`() = runTest {
        val gateway = RecordingGateway(
            configBody = samplePayload,
            streamsBody = """{"backyard":{},"hall":{},"gate":{}}""",
        )
        val getter = bytesGetterFor(TransportKind.TAILSCALE, gateway)

        val result = FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)

        assertTrue("TAILSCALE must use the tsnet getter", getter is TsnetHttpBytesGetter)
        assertEquals(2, gateway.httpGetBytesCalls)
        assertEquals("http://frigate:5000/api/go2rtc/streams", gateway.lastUrl)
        assertTrue(result is CameraDiscoveryState.Loaded)
        val cameras = (result as CameraDiscoveryState.Loaded).cameras
        assertEquals(3, cameras.size)
        cameras.forEach { assertTrue("TAILSCALE must resolve playable streams", it.playable) }
    }

    @Test
    fun `discovery propagates cancellation`() = runTest {
        val getter = HttpBytesGetter { _, _ -> throw CancellationException("cancelled") }

        var thrown: CancellationException? = null
        try {
            FrigateCameraDiscovery(getter).discover("http://frigate:5000", 5000)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertNull("cancellation must not be swallowed into an Error state", thrown?.cause)
        assertEquals("cancelled", thrown?.message)
    }

    /** Serves the config payload for `/api/config` and the streams payload otherwise. */
    private fun router(configBody: String, streamsBody: String = "{}"): HttpBytesGetter =
        HttpBytesGetter { url, _ ->
            when {
                url.endsWith("/api/config") ->
                    HttpBytesResult(200, "application/json", url, configBody.toByteArray())
                url.endsWith("/api/go2rtc/streams") ->
                    HttpBytesResult(200, "application/json", url, streamsBody.toByteArray())
                else -> HttpBytesResult(404, "text/plain", url, ByteArray(0))
            }
        }

    private class RecordingGateway(
        private val configBody: String,
        private val streamsBody: String,
    ) : TsnetGateway {
        var httpGetBytesCalls: Int = 0
            private set
        var lastUrl: String? = null
            private set

        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun reset() = Unit
        override suspend fun httpGet(url: String, timeoutMs: Long): String = "0.17.1"

        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult {
            httpGetBytesCalls++
            lastUrl = url
            val body = when {
                url.endsWith("/api/config") -> configBody
                url.endsWith("/api/go2rtc/streams") -> streamsBody
                else -> "{}"
            }
            return HttpBytesResult(200, "application/json", url, body.toByteArray())
        }
    }
}
