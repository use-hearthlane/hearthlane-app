package org.hearthlane.core.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayContractJsonTest {

    @Test
    fun parseLocation_readsAllFields() {
        val location = RelayContractJson.parseLocation(
            """{"latitude":-23.5,"longitude":-46.6,"accuracy":12.5,"recordedAtEpochMs":1700000000000,"publishedAtEpochMs":1700000000500}""",
        )
        assertEquals(-23.5, location?.latitude ?: 0.0, 0.0001)
        assertEquals(-46.6, location?.longitude ?: 0.0, 0.0001)
        assertEquals(12.5f, location?.accuracy ?: 0f, 0.0001f)
        assertEquals(1700000000000L, location?.recordedAtEpochMs)
        assertEquals(1700000000500L, location?.publishedAtEpochMs)
    }

    @Test
    fun parseLocation_allowsNullPublishedAt() {
        val location = RelayContractJson.parseLocation(
            """{"latitude":-23.5,"longitude":-46.6,"accuracy":12.5,"recordedAtEpochMs":1700000000000}""",
        )
        assertNull(location?.publishedAtEpochMs)
    }

    @Test
    fun parseLocation_returnsNullWhenCoordinateMissing() {
        assertNull(RelayContractJson.parseLocation("""{"latitude":-23.5}"""))
    }

    @Test
    fun parseDeviceList_readsIdsAndNicknames() {
        val devices = RelayContractJson.parseDeviceList(
            """{"devices":[{"deviceId":"d1","nickname":"Hall"},{"deviceId":"d2"}]}""",
        )
        assertEquals(2, devices.size)
        assertEquals("d1", devices[0].deviceId)
        assertEquals("Hall", devices[0].nickname)
        assertEquals("d2", devices[1].deviceId)
        assertNull(devices[1].nickname)
    }

    @Test
    fun parseDeviceList_returnsEmptyOnEmptyArray() {
        assertTrue(RelayContractJson.parseDeviceList("""{"devices":[]}""").isEmpty())
    }

    @Test
    fun parseDeviceList_handlesNestedEscapesAndWhitespace() {
        val devices = RelayContractJson.parseDeviceList(
            """{"devices":[ { "deviceId": "d1", "nickname": "John \"House\"" } ]}""",
        )
        assertEquals(1, devices.size)
        assertEquals("John \"House\"", devices[0].nickname)
    }

    @Test
    fun locationBody_matchesContractShape() {
        val body = RelayContractJson.locationBody(-23.5, -46.6, 12.5f, 1700000000000L)
        assertEquals(
            """{"latitude":-23.5,"longitude":-46.6,"accuracy":12.5,"recordedAtEpochMs":1700000000000}""",
            body,
        )
    }

    @Test
    fun nicknameBody_escapesQuotes() {
        assertEquals("""{"nickname":"Hall \"A\""}""", RelayContractJson.nicknameBody("Hall \"A\""))
    }

    @Test
    fun statusRules_deriveFromPublishedAge() {
        val now = 1700000000000L
        assertEquals(
            LocationStatus.AVAILABLE,
            LocationStatusRules.statusFor(now - 1_000, now),
        )
        assertEquals(
            LocationStatus.STALE,
            LocationStatusRules.statusFor(now - 20 * 60_000L, now),
        )
        assertEquals(
            LocationStatus.UNAVAILABLE,
            LocationStatusRules.statusFor(now - 2 * 60 * 60_000L, now),
        )
        assertEquals(
            LocationStatus.UNAVAILABLE,
            LocationStatusRules.statusFor(null, now),
        )
    }
}