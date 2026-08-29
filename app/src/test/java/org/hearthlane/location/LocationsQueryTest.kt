package org.hearthlane.location

import org.hearthlane.core.relay.DeviceLocation
import org.hearthlane.core.relay.LocationStatus
import org.hearthlane.test.FakeRelayClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Locations query: last-known semantics, freshness derivation
 * from the publication age and the distinct failure outcomes.
 */
class LocationsQueryTest {

    private fun queryFor(
        relay: FakeRelayClient,
        clockMs: Long = 1_000L,
    ) = LocationsQuery(
        client = { relay },
        clockMs = { clockMs },
    )

    @Test
    fun `empty relay resolves to OK with no markers`() = runTest {
        val result = queryFor(FakeRelayClient()).run()

        assertEquals(MapQueryStatus.OK, result.status)
        assertTrue(result.markers.isEmpty())
    }

    @Test
    fun `device with location gets a marker labelled by nickname`() = runTest {
        val relay = FakeRelayClient()
        relay.setNickname("d1", "Mom")
        relay.publishLocation(
            "d1",
            DeviceLocation(latitude = -23.5, longitude = -46.6, accuracy = 15f, recordedAtEpochMs = 500L),
        )

        val result = queryFor(relay, clockMs = 1_000L).run()

        assertEquals(MapQueryStatus.OK, result.status)
        assertEquals(1, result.markers.size)
        val marker = result.markers[0]
        assertEquals("d1", marker.deviceId)
        assertEquals("Mom", marker.label)
        assertEquals(-23.5, marker.latitude, 0.0)
        assertEquals(15f, marker.accuracyMeters, 0f)
    }

    @Test
    fun `device id is the label when no nickname is configured`() = runTest {
        val relay = FakeRelayClient()
        relay.publishLocation(
            "d1",
            DeviceLocation(latitude = -23.5, longitude = -46.6, accuracy = 15f, recordedAtEpochMs = 500L),
        )

        val result = queryFor(relay).run()

        assertEquals("d1", result.markers.single().label)
    }

    @Test
    fun `devices without a location are omitted`() = runTest {
        val relay = FakeRelayClient()
        relay.publishLocation(
            "d1",
            DeviceLocation(latitude = -23.5, longitude = -46.6, accuracy = 15f, recordedAtEpochMs = 500L),
        )
        // d2 was never published: only d1 becomes a marker.

        val result = queryFor(relay).run()

        assertEquals(listOf("d1"), result.markers.map { it.deviceId })
    }

    @Test
    fun `freshness derives from publication age`() = runTest {
        val relay = FakeRelayClient()
        relay.publishLocation("d1", DeviceLocation(-1.0, 2.0, 10f, 100L))
        // publishedAtEpochMs is stamped by the fake at publish time; use a clock
        // 5 minutes after publication -> AVAILABLE.
        val result = queryFor(
            relay,
            clockMs = publishClock(relay, 5 * 60_000L),
        ).run()

        assertEquals(LocationStatus.AVAILABLE, result.markers.single().status)
    }

    @Test
    fun `long silence degrades the status to stale then unavailable`() = runTest {
        val relay = FakeRelayClient()
        relay.publishLocation("d1", DeviceLocation(-1.0, 2.0, 10f, 0L))

        val stale = queryFor(relay, clockMs = publishClock(relay, 30 * 60_000L)).run()
        val gone = queryFor(relay, clockMs = publishClock(relay, 90 * 60_000L)).run()

        assertEquals(LocationStatus.STALE, stale.markers.single().status)
        assertEquals(LocationStatus.UNAVAILABLE, gone.markers.single().status)
    }

    @Test
    fun `relay unavailable resolves to RELAY_UNREACHABLE`() = runTest {
        val result = LocationsQuery(client = { null }).run()

        assertEquals(MapQueryStatus.RELAY_UNREACHABLE, result.status)
        assertTrue(result.markers.isEmpty())
    }

    @Test
    fun `relay request failure resolves to ERROR with message`() = runTest {
        val failing = FakeRelayClient().apply { failReads = true }
        val result = queryFor(failing).run()

        assertEquals(MapQueryStatus.ERROR, result.status)
        assertTrue(!result.errorMessage.isNullOrBlank())
    }

    private suspend fun publishClock(relay: FakeRelayClient, offsetMs: Long): Long {
        val published = relay.getLocation("d1")!!.publishedAtEpochMs!!
        return published + offsetMs
    }
}