package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FrigateEventApiTest {

    private val baseUrl = "http://frigate:5000"

    private val twoEvents = """
        [
            {"id":"2-b","camera":"backyard","label":"person","start_time":2.0,"end_time":3.0,"has_clip":true,"has_snapshot":true,"zones":[]},
            {"id":"1-a","camera":"backyard","label":"car","start_time":1.0,"end_time":2.0,"has_clip":false,"has_snapshot":false,"zones":["yard"]}
        ]
    """.trimIndent()

    private fun getter(
        body: String = twoEvents,
        statusCode: Int = 200,
        contentType: String = "application/json",
    ): Pair<FrigateEventApi, RecordingGetter> {
        val g = RecordingGetter(body, statusCode, contentType)
        return FrigateEventApi(g) to g
    }

    @Test
    fun `recentEvents requests the events endpoint and parses the list`() = runTest {
        val (api, g) = getter()

        val events = api.recentEvents(baseUrl, "backyard", 20)

        assertEquals(2, events.size)
        assertEquals(listOf("2-b", "1-a"), events.map { it.id })
        assertTrue("must request the events endpoint", g.lastUrl!!.startsWith("$baseUrl/api/events"))
        assertTrue("must filter by camera", g.lastUrl!!.contains("camera=backyard"))
        assertTrue("must apply the limit", g.lastUrl!!.contains("limit=20"))
    }

    @Test
    fun `recentEvents maps each event to the domain model`() = runTest {
        val (api, _) = getter()

        val events = api.recentEvents(baseUrl, "backyard", 10)

        val second = events[1]
        assertEquals("1-a", second.id)
        assertEquals("backyard", second.cameraId)
        assertEquals("car", second.label)
        assertEquals(1.0, second.startTime, 0.0)
        assertEquals(2.0, second.endTime!!, 0.0)
        assertEquals(false, second.hasClip)
        assertEquals(false, second.hasSnapshot)
        assertEquals(listOf("yard"), second.zones)
    }

    @Test
    fun `olderEvents adds the before cursor`() = runTest {
        val (api, g) = getter()
        val before = 1787066856.782408

        api.olderEvents(baseUrl, "hall", 15, before)

        assertTrue(g.lastUrl!!.contains("camera=hall"))
        assertTrue(g.lastUrl!!.contains("limit=15"))
        val beforeValue = g.lastUrl!!.substringAfter("before=")
        assertEquals(
            "the before cursor must round-trip to the exact startTime",
            before,
            beforeValue.toDouble(),
            0.0,
        )
    }

    @Test
    fun `event returns the parsed single event`() = runTest {
        val single = """{"id":"9-z","camera":"gate","label":"car","start_time":9.0,"end_time":10.0,"has_clip":true,"has_snapshot":true,"zones":[]}"""
        val (api, g) = getter(body = single)

        val event = api.event(baseUrl, "9-z")

        assertEquals("9-z", event.id)
        assertEquals("gate", event.cameraId)
        assertEquals("$baseUrl/api/events/9-z", g.lastUrl)
    }

    @Test
    fun `event throws EventNotFoundException on HTTP 404`() = runTest {
        val (api, _) = getter(statusCode = 404, body = """"Event not found"""")

        var thrown: Exception? = null
        try {
            api.event(baseUrl, "missing")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(
            "a 404 on the event detail must surface as not-found",
            thrown is EventNotFoundException,
        )
    }

    @Test
    fun `list throws IOException on non-2xx response`() = runTest {
        val (api, _) = getter(statusCode = 500)

        var thrown: Exception? = null
        try {
            api.recentEvents(baseUrl, "backyard", 10)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("a non-2xx list must raise", thrown is IOException)
        assertTrue(thrown!!.message.orEmpty().contains("HTTP 500"))
    }

    @Test
    fun `event throws IOException on a non-404 non-2xx response`() = runTest {
        val (api, _) = getter(statusCode = 500)

        var thrown: Exception? = null
        try {
            api.event(baseUrl, "9-z")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is IOException)
        assertTrue(thrown!!.message.orEmpty().contains("HTTP 500"))
    }

    @Test
    fun `list throws IOException on a malformed payload`() = runTest {
        val (api, _) = getter(body = "not json")

        var thrown: Exception? = null
        try {
            api.recentEvents(baseUrl, "backyard", 10)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("a malformed payload must raise", thrown is IOException)
        assertTrue(thrown!!.message.orEmpty().contains("invalid events payload"))
    }

    @Test
    fun `event throws IOException on a malformed single payload`() = runTest {
        val (api, _) = getter(body = "not json")

        var thrown: Exception? = null
        try {
            api.event(baseUrl, "9-z")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("a malformed detail payload must raise", thrown is IOException)
    }

    @Test
    fun `transport failure propagates without mapping to an HTTP error`() = runTest {
        val failing = HttpBytesGetter { _, _ -> throw IOException("connection refused") }
        val api = FrigateEventApi(failing)

        var thrown: Exception? = null
        try {
            api.recentEvents(baseUrl, "backyard", 10)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is IOException)
        assertTrue(
            "a transport failure must not be reported as an HTTP error",
            !thrown!!.message.orEmpty().contains("HTTP"),
        )
    }

    @Test
    fun `thumbnailUrl resolves the thumbnail resource endpoint`() {
        val (api, _) = getter()

        assertEquals(
            "$baseUrl/api/events/1-a/thumbnail.jpg",
            api.thumbnailUrl(baseUrl, "1-a"),
        )
    }

    @Test
    fun `snapshotUrl resolves the snapshot resource endpoint`() {
        val (api, _) = getter()

        assertEquals(
            "$baseUrl/api/events/1-a/snapshot.jpg",
            api.snapshotUrl(baseUrl, "1-a"),
        )
    }

    @Test
    fun `clipUrl resolves the clip resource endpoint`() {
        val (api, _) = getter()

        assertEquals(
            "$baseUrl/api/events/1-a/clip.mp4",
            api.clipUrl(baseUrl, "1-a"),
        )
    }

    private class RecordingGetter(
        private val body: String,
        private val statusCode: Int,
        private val contentType: String,
    ) : HttpBytesGetter {
        var lastUrl: String? = null

        override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult {
            lastUrl = url
            return HttpBytesResult(statusCode, contentType, url, body.toByteArray())
        }
    }
}
