package org.hearthlane.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the map-active loop: immediate first query, fixed cadence and
 * stop semantics (the previous result is kept, no further query runs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapActiveQueryControllerTest {

    private fun deviceMarker(id: String) = DeviceMarker(
        deviceId = id,
        label = id,
        latitude = 1.0,
        longitude = 2.0,
        accuracyMeters = 10f,
        status = org.hearthlane.core.relay.LocationStatus.AVAILABLE,
        recordedAtEpochMs = 0L,
        publishedAtEpochMs = 1L,
    )

    @Test
    fun `start runs an immediate query then repeats at the interval`() = runTest {
        var counter = 0L
        val controller = MapActiveQueryController(
            query = {
                counter++
                MapQueryResult(
                    MapQueryStatus.OK,
                    markers = listOf(deviceMarker("d$counter")),
                )
            },
            intervalMs = { 1_000L },
            scope = backgroundScope,
        )

        controller.start()
        runCurrent()
        assertEquals(1, controller.state.value.updateCount)
        assertEquals("d1", controller.state.value.markers.single().deviceId)
        assertTrue(controller.state.value.status == MapQueryStatus.OK)
        assertNull(controller.state.value.lastError)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, controller.state.value.updateCount)
        assertEquals("d2", controller.state.value.markers.single().deviceId)
    }

    @Test
    fun `transient failure keeps the previous markers`() = runTest {
        var failNext = false
        val controller = MapActiveQueryController(
            query = {
                if (failNext) {
                    MapQueryResult(MapQueryStatus.ERROR, errorMessage = "boom")
                } else {
                    MapQueryResult(MapQueryStatus.OK, markers = listOf(deviceMarker("d1")))
                }
            },
            intervalMs = { 1_000L },
            scope = backgroundScope,
        )

        controller.start()
        runCurrent()
        assertEquals(1, controller.state.value.markers.size)

        failNext = true
        advanceTimeBy(1_000L)
        runCurrent()

        // Markers survive; the failure is recorded so the UI can surface it.
        assertEquals(1, controller.state.value.markers.size)
        assertEquals(controller.state.value.status, MapQueryStatus.ERROR)
        assertTrue(controller.state.value.lastError != null)
    }

    @Test
    fun `stop cancels the loop and no further queries run`() = runTest {
        var counter = 0
        val controller = MapActiveQueryController(
            query = {
                counter++
                MapQueryResult(MapQueryStatus.OK, markers = listOf(deviceMarker("d$counter")))
            },
            intervalMs = { 1_000L },
            scope = backgroundScope,
        )

        controller.start()
        runCurrent()
        val countAfterFirst = controller.state.value.updateCount

        controller.stop()
        runCurrent()
        assertFalse(controller.state.value.running)

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(countAfterFirst, controller.state.value.updateCount)
    }

    @Test
    fun `start while already running is a no-op`() = runTest {
        var counter = 0
        val controller = MapActiveQueryController(
            query = {
                counter++
                MapQueryResult(MapQueryStatus.OK, markers = listOf(deviceMarker("$counter")))
            },
            intervalMs = { 1_000L },
            scope = backgroundScope,
        )

        controller.start()
        runCurrent()
        controller.start()
        runCurrent()

        assertEquals(1, controller.state.value.updateCount)
    }
}