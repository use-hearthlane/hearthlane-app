package org.hearthlane.core.relay

import org.hearthlane.core.connectivity.HttpBytesResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRelayClientTest {

    /** Records requests and replays a canned response. */
    private class RecordingTransport(
        var response: HttpBytesResult,
    ) : RelayHttpTransport {

        data class Request(
            val method: String,
            val url: String,
            val contentType: String?,
            val body: String?,
            val headers: Map<String, String>,
        )

        val requests = mutableListOf<Request>()

        override suspend fun request(
            method: String,
            url: String,
            contentType: String?,
            body: String?,
            headers: Map<String, String>,
            timeoutMs: Long,
        ): HttpBytesResult {
            requests.add(Request(method, url, contentType, body, headers))
            return response
        }
    }

    private fun ok(body: String) = HttpBytesResult(
        statusCode = 200,
        contentType = "application/json",
        finalUrl = "http://relay",
        body = body.toByteArray(Charsets.UTF_8),
    )

    @Test
    fun publishLocation_buildsContractPathAndBody() = runTest {
        val transport = RecordingTransport(HttpBytesResult(204, null, "http://relay", ByteArray(0)))
        val client = HttpRelayClient(transport, "http://192.168.1.10:8000/")

        client.publishLocation(
            "d1",
            DeviceLocation(-23.5, -46.6, 12.5f, 1700000000000L),
        )

        val request = transport.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("http://192.168.1.10:8000/devices/d1/location", request.url)
        assertEquals("application/json", request.contentType)
        assertEquals(
            """{"latitude":-23.5,"longitude":-46.6,"accuracy":12.5,"recordedAtEpochMs":1700000000000}""",
            request.body,
        )
        assertTrue(request.headers.isEmpty())
    }

    @Test
    fun noRequestCarriesAnAuthorizationHeader() = runTest {
        val transport = RecordingTransport(ok("""{"devices":[]}"""))
        val client = HttpRelayClient(transport, "http://relay")

        client.publishLocation("d1", DeviceLocation(0.0, 0.0, 1f, 1L))
        transport.response = ok("""{"latitude":0.0,"longitude":0.0,"accuracy":1.0,"recordedAtEpochMs":1}""")
        client.listDevices()
        client.getLocation("d1")
        transport.response = HttpBytesResult(204, null, "http://relay", ByteArray(0))
        client.setNickname("d1", "Hall")

        assertTrue("all relay calls must be recorded", transport.requests.size == 4)
        for (request in transport.requests) {
            assertTrue(
                "no request may carry an Authorization header: ${request.headers}",
                request.headers["Authorization"] == null,
            )
            assertTrue(request.headers.isEmpty())
        }
    }

    @Test
    fun publishLocation_throwsOnFailure() = runTest {
        val transport = RecordingTransport(HttpBytesResult(503, null, "http://relay", ByteArray(0)))
        val client = HttpRelayClient(transport, "http://relay")

        var thrown: RelayException? = null
        try {
            client.publishLocation("d1", DeviceLocation(0.0, 0.0, 1f, 1L))
        } catch (e: RelayException) {
            thrown = e
        }
        assertTrue(thrown?.message?.contains("503") == true)
    }

    @Test
    fun publishLocation_acceptsEvery2xxStatus() = runTest {
        val transport = RecordingTransport(HttpBytesResult(200, null, "http://relay", ByteArray(0)))
        val client = HttpRelayClient(transport, "http://relay")

        assertEquals(200, client.publishLocation("d1", DeviceLocation(1.0, 2.0, 5f, 1L)))
        transport.response = HttpBytesResult(201, null, "http://relay", ByteArray(0))
        assertEquals(201, client.publishLocation("d1", DeviceLocation(1.0, 2.0, 5f, 1L)))
        transport.response = HttpBytesResult(204, null, "http://relay", ByteArray(0))
        assertEquals("an empty-body 204 must be a success", 204, client.publishLocation("d1", DeviceLocation(1.0, 2.0, 5f, 1L)))
    }

    @Test
    fun publishLocation_distinguishesHttpStatusFromTransportFailure() = runTest {
        val statuses = listOf(400, 404, 405, 500, 503)

        for (status in statuses) {
            val transport = RecordingTransport(HttpBytesResult(status, null, "http://relay", ByteArray(0)))
            val c = HttpRelayClient(transport, "http://relay")
            var thrown: RelayException? = null
            try {
                c.publishLocation("d1", DeviceLocation(0.0, 0.0, 1f, 1L))
            } catch (e: RelayException) {
                thrown = e
            }
            assertTrue("HTTP $status must surface as a relay HTTP error, not a generic network error", thrown != null)
            assertTrue("the status code must be reported: ${thrown?.message}", thrown?.message?.contains("HTTP $status") == true)
        }
    }

    @Test
    fun listDevices_parsesDeviceNames() = runTest {
        val transport = RecordingTransport(ok("""{"devices":[{"deviceId":"d1","nickname":"Hall"}]}"""))
        val client = HttpRelayClient(transport, "http://relay")

        val devices = client.listDevices()

        assertEquals(1, devices.size)
        assertEquals("d1", devices.single().deviceId)
        assertEquals("Hall", devices.single().nickname)
    }

    @Test
    fun getLocation_returnsNullOn404() = runTest {
        val transport = RecordingTransport(HttpBytesResult(404, null, "http://relay", ByteArray(0)))
        val client = HttpRelayClient(transport, "http://relay")

        assertNull(client.getLocation("d1"))
    }

    @Test
    fun getLocation_parsesBodyOn200() = runTest {
        val transport = RecordingTransport(
            ok("""{"latitude":-23.5,"longitude":-46.6,"accuracy":12.5,"recordedAtEpochMs":1700000000000,"publishedAtEpochMs":1700000000500}"""),
        )
        val client = HttpRelayClient(transport, "http://relay")

        val location = client.getLocation("d1")

        assertEquals(-23.5, location?.latitude ?: 0.0, 0.0001)
        assertEquals(12.5f, location?.accuracy ?: 0f, 0.0001f)
        assertEquals(1700000000000L, location?.recordedAtEpochMs)
    }

    @Test
    fun setNickname_putsNicknameBody() = runTest {
        val transport = RecordingTransport(HttpBytesResult(204, null, "http://relay", ByteArray(0)))
        val client = HttpRelayClient(transport, "http://relay/")

        client.setNickname("d1", "Hall")

        val request = transport.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("http://relay/devices/d1/nickname", request.url)
        assertEquals("""{"nickname":"Hall"}""", request.body)
    }
}