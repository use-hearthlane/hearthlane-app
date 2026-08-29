package org.hearthlane.ui.locations

import org.hearthlane.core.relay.LocationStatus
import org.hearthlane.location.DeviceMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Locations detail formatting: the accuracy-circle decision, the
 * coordinate/accuracy labels and the clipboard text, all pure and locale-safe.
 */
class LocationDetailFormatTest {

    private fun marker(
        deviceId: String = "d1",
        status: LocationStatus = LocationStatus.AVAILABLE,
        accuracy: Float = 18.4f,
        latitude: Double = -23.550520,
        longitude: Double = -46.633308,
    ) = DeviceMarker(
        deviceId = deviceId,
        label = "Hall",
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        status = status,
        recordedAtEpochMs = 1_700_000_000_000L,
        publishedAtEpochMs = 1_700_000_000_500L,
    )

    @Test
    fun `positive accuracy yields a circle`() {
        assertTrue(shouldShowAccuracyCircle(18.4f))
    }

    @Test
    fun `null accuracy yields no circle`() {
        assertFalse(shouldShowAccuracyCircle(null))
    }

    @Test
    fun `zero accuracy yields no circle`() {
        assertFalse(shouldShowAccuracyCircle(0f))
    }

    @Test
    fun `negative accuracy yields no circle`() {
        assertFalse(shouldShowAccuracyCircle(-5f))
    }

    @Test
    fun `NaN accuracy yields no circle`() {
        assertFalse(shouldShowAccuracyCircle(Float.NaN))
    }

    @Test
    fun `infinite accuracy yields no circle`() {
        assertFalse(shouldShowAccuracyCircle(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `accuracy label rounds to whole meters`() {
        assertEquals("±18 m", formatAccuracy(18.4f))
        assertEquals("±31 m", formatAccuracy(30.6f))
    }

    @Test
    fun `accuracy label is null for missing or invalid accuracy`() {
        assertNull(formatAccuracy(null))
        assertNull(formatAccuracy(0f))
        assertNull(formatAccuracy(-1f))
        assertNull(formatAccuracy(Float.NaN))
    }

    @Test
    fun `coordinates are formatted with six decimals using a dot`() {
        assertEquals("-23.550520,-46.633308", formatCoordinates(-23.550520, -46.633308))
        assertEquals("0.000000,0.000000", formatCoordinates(0.0, 0.0))
    }

    @Test
    fun `clipboard text is exactly lat comma lon without extra space`() {
        val text = copyCoordinatesText(-23.550520, -46.633308)
        assertEquals("-23.550520,-46.633308", text)
        assertFalse(text.contains(' '))
    }

    @Test
    fun `available device exposes coordinates accuracy and copy`() {
        val detail = buildDeviceDetail(marker(status = LocationStatus.AVAILABLE))

        assertEquals(LocationStatus.AVAILABLE, detail.status)
        assertEquals("-23.550520,-46.633308", detail.coordinates)
        assertEquals("±18 m", detail.accuracyLabel)
        assertTrue(detail.canCopy)
        assertTrue(detail.showAccuracyCircle)
    }

    @Test
    fun `stale device keeps its last known location but stays stale`() {
        val detail = buildDeviceDetail(marker(status = LocationStatus.STALE))

        assertEquals("a stale marker must not be upgraded by accuracy", LocationStatus.STALE, detail.status)
        assertEquals("-23.550520,-46.633308", detail.coordinates)
        assertTrue(detail.canCopy)
        assertTrue(detail.showAccuracyCircle)
    }

    @Test
    fun `unavailable device offers no coordinates and no copy`() {
        val detail = buildDeviceDetail(marker(status = LocationStatus.UNAVAILABLE))

        assertEquals(LocationStatus.UNAVAILABLE, detail.status)
        assertNull("an unavailable device must not invent coordinates", detail.coordinates)
        assertFalse("copy must be disabled without a location", detail.canCopy)
    }

    @Test
    fun `invalid accuracy on a marker yields unavailable precision and no circle`() {
        val detail = buildDeviceDetail(marker(accuracy = Float.NaN))

        assertNull(detail.accuracyLabel)
        assertFalse(detail.showAccuracyCircle)
        assertEquals("-23.550520,-46.633308", detail.coordinates)
    }

    // ---- Accuracy circle planning (independent of selection/details) ----

    @Test
    fun `available device with valid accuracy warrants a circle`() {
        val devices = listOf(marker(accuracy = 18.4f))

        assertEquals(listOf("d1"), devicesWithAccuracyCircle(devices).map { it.deviceId })
    }

    @Test
    fun `a circle is planned regardless of selection`() {
        // The plan never receives a selection or details flag: a non-selected
        // device with a valid accuracy still warrants a circle.
        val devices = listOf(marker(deviceId = "d1", accuracy = 12f), marker(deviceId = "d2", accuracy = 30f))

        assertEquals(setOf("d1", "d2"), devicesWithAccuracyCircle(devices).map { it.deviceId }.toSet())
    }

    @Test
    fun `closing details never removes a circle`() {
        // detailsVisible is not part of the plan signature; the circle depends
        // only on location + accuracy, so the plan is unchanged by the panel.
        val devices = listOf(marker(accuracy = 18.4f))

        val first = planAccuracyCircles(previousWanted = emptySet(), devices = devices)
        assertEquals(listOf("d1"), first.toCreate.map { it.deviceId })
        val afterClosingDetails = planAccuracyCircles(previousWanted = first.toCreate.map { it.deviceId }.toSet(), devices = devices)
        assertEquals("a repeated poll after closing details must not recreate circles", emptyList<DeviceMarker>(), afterClosingDetails.toCreate)
    }

    @Test
    fun `switching selection keeps both devices circles`() {
        val devices = listOf(marker(deviceId = "d1", accuracy = 12f), marker(deviceId = "d2", accuracy = 30f))

        val plan = planAccuracyCircles(emptySet(), devices)

        assertEquals("both devices keep their circles regardless of selection", setOf("d1", "d2"), (plan.toCreate.map { it.deviceId }).toSet())
    }

    @Test
    fun `selection only affects visual emphasis not circle existence`() {
        // The plan is purely structural; selection emphasis is applied in the
        // overlay when painting, never in the existence decision.
        val devices = listOf(marker(deviceId = "d1", accuracy = 12f), marker(deviceId = "d2", accuracy = 30f))
        val plan = planAccuracyCircles(emptySet(), devices)

        assertEquals(setOf("d1", "d2"), devicesWithAccuracyCircle(devices).map { it.deviceId }.toSet())
        assertTrue(plan.toRemove.isEmpty())
    }

    @Test
    fun `invalid accuracy yields no circle`() {
        val devices = listOf(marker(accuracy = 0f), marker(deviceId = "d2", accuracy = Float.NaN))

        assertTrue(devicesWithAccuracyCircle(devices).isEmpty())
    }

    @Test
    fun `unavailable device yields no circle`() {
        val devices = listOf(marker(status = LocationStatus.UNAVAILABLE, accuracy = 18.4f))

        assertTrue("an unavailable device must not show a precision circle", devicesWithAccuracyCircle(devices).isEmpty())
    }

    @Test
    fun `stale device keeps its last accuracy circle`() {
        val devices = listOf(marker(status = LocationStatus.STALE, accuracy = 18.4f))

        assertEquals(listOf("d1"), devicesWithAccuracyCircle(devices).map { it.deviceId })
    }

    @Test
    fun `removing a device removes its circle from the plan`() {
        val previous = setOf("d1", "d2")
        val devices = listOf(marker(deviceId = "d1", accuracy = 12f))

        val plan = planAccuracyCircles(previous, devices)

        assertEquals(setOf("d2"), plan.toRemove)
        assertEquals(listOf("d1"), plan.toUpdate.map { it.deviceId })
    }

    @Test
    fun `successive identical polls never accumulate circles`() {
        val devices = listOf(marker(deviceId = "d1", accuracy = 12f), marker(deviceId = "d2", accuracy = 30f))
        val first = planAccuracyCircles(emptySet(), devices)

        val second = planAccuracyCircles(first.toCreate.map { it.deviceId }.toSet(), devices)

        assertEquals("no new polygons on a repeated poll", emptyList<DeviceMarker>(), second.toCreate)
        assertEquals("no removals on a repeated poll", emptySet<String>(), second.toRemove)
        assertEquals(setOf("d1", "d2"), second.toUpdate.map { it.deviceId }.toSet())
    }
}