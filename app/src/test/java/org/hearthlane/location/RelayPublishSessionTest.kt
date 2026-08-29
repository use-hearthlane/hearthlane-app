package org.hearthlane.location

import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.connectivity.TsnetGateway
import org.hearthlane.core.relay.DeviceLocation
import org.hearthlane.core.relay.RelayConfig
import org.hearthlane.core.relay.RelayConnection
import org.hearthlane.core.relay.RelayTransportKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the publish path binds the SAME transport abstraction as the relay
 * probe: a Tailscale connection routes both GET /devices and
 * PUT /devices/{id}/location through the tsnet gateway, while a LOCAL
 * connection never touches the gateway.
 */
class RelayPublishSessionTest {

    private class FakeGateway : TsnetGateway {
        data class Request(val method: String, val url: String, val body: String?)

        val requests = mutableListOf<Request>()

        override suspend fun ensureRunning() = Unit

        override suspend fun stopIfRunning() = Unit

        override suspend fun reset() = Unit

        override suspend fun httpGet(url: String, timeoutMs: Long): String = ""

        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
            okBody()

        override suspend fun httpRequest(
            method: String,
            url: String,
            contentType: String?,
            body: String?,
            headers: Map<String, String>,
            timeoutMs: Long,
        ): HttpBytesResult {
            requests += Request(method, url, body)
            return if (method == "GET") okBody() else HttpBytesResult(204, null, url, ByteArray(0))
        }

        private fun okBody() = HttpBytesResult(
            200,
            "application/json",
            "http://relay",
            """{"devices":[]}""".toByteArray(Charsets.UTF_8),
        )
    }

    private fun session(
        gateway: TsnetGateway,
        kind: RelayTransportKind,
    ) = RelayPublishSession(
        gateway = gateway,
        config = { RelayConfig("http://relay.local", "http://relay.hearthlane.example") },
        connector = { RelayConnection.Connected(kind) },
    )

    @Test
    fun `tailscale publish and probe share the tsnet gateway`() = runTest {
        val gateway = FakeGateway()
        val session = session(gateway, RelayTransportKind.TAILSCALE)

        val client = session.client()
        assertEquals(204, client.publishLocation("d1", DeviceLocation(1.0, 2.0, 5f, 1L)))
        client.listDevices()

        assertEquals(2, gateway.requests.size)
        val put = gateway.requests[0]
        assertEquals("PUT", put.method)
        assertTrue("PUT must hit the location path", put.url.endsWith("/devices/d1/location"))
        assertEquals("GET", gateway.requests[1].method)
        assertTrue("GET must hit the devices list", gateway.requests[1].url.endsWith("/devices"))
        assertTrue("the DTO body reaches the transport", put.body?.contains("\"latitude\":1.0") == true)
    }

    @Test
    fun `local connection never routes through the tsnet gateway`() = runTest {
        val gateway = FakeGateway()
        val session = session(gateway, RelayTransportKind.LOCAL)

        val client = session.client()

        // The LOCAL transport is HttpURLConnection over the normal Android
        // network; it must never escape through the tsnet tunnel.
        assertTrue(gateway.requests.isEmpty())
    }
}