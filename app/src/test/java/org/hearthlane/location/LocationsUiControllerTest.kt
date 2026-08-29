package org.hearthlane.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LocationsUiController]: a single selection state shared by the
 * selector and the map markers, and focus requests emitted only for devices
 * that actually have coordinates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationsUiControllerTest {

    @Test
    fun `selecting a device updates the selected id`() = runTest {
        val controller = LocationsUiController(backgroundScope)

        controller.selectDevice("d1", hasCoordinates = true)

        assertEquals("d1", controller.selectedDeviceId.value)
    }

    @Test
    fun `selecting another device replaces the selection`() = runTest {
        val controller = LocationsUiController(backgroundScope)

        controller.selectDevice("d1", hasCoordinates = true)
        controller.selectDevice("d2", hasCoordinates = true)

        assertEquals("d2", controller.selectedDeviceId.value)
    }

    @Test
    fun `selecting a device with coordinates requests a focus`() = runTest {
        val controller = LocationsUiController(backgroundScope)

        controller.selectDevice("d1", hasCoordinates = true)

        val request = controller.focusRequests.value
        assertEquals("d1", request?.deviceId)
        assertEquals(1L, request?.epoch)
    }

    @Test
    fun `selecting a device without coordinates does not request a focus`() = runTest {
        val controller = LocationsUiController(backgroundScope)

        controller.selectDevice("offline-device", hasCoordinates = false)

        assertNull("a fake position must never be requested", controller.focusRequests.value)
        assertEquals("offline-device", controller.selectedDeviceId.value)
    }

    @Test
    fun `re-selecting the same device re-emits a focus request`() = runTest {
        val controller = LocationsUiController(backgroundScope)

        controller.selectDevice("d1", hasCoordinates = true)
        val firstEpoch = controller.focusRequests.value?.epoch
        controller.selectDevice("d1", hasCoordinates = true)

        val request = controller.focusRequests.value
        assertEquals("d1", request?.deviceId)
        assertEquals("the epoch must advance on re-selection", firstEpoch!! + 1, request?.epoch)
    }

    @Test
    fun `selection is cleared when the selected device disappears`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.onMarkersChanged(setOf("d2", "d3"))

        assertNull("the selection must not linger on a gone device", controller.selectedDeviceId.value)
    }

    @Test
    fun `selection survives when the device stays present`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.onMarkersChanged(setOf("d1", "d2"))

        assertEquals("d1", controller.selectedDeviceId.value)
    }

    @Test
    fun `selecting a device opens the details panel`() = runTest {
        val controller = LocationsUiController(backgroundScope)

        controller.selectDevice("d1", hasCoordinates = true)

        assertTrue("a marker tap must open the details panel", controller.detailsVisible.value)
    }

    @Test
    fun `empty map tap closes the details panel`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.hideDetails()

        assertFalse("an empty map tap must close the details", controller.detailsVisible.value)
    }

    @Test
    fun `closing the details panel keeps the selection`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.hideDetails()

        assertEquals("d1", controller.selectedDeviceId.value)
        assertFalse(controller.detailsVisible.value)
    }

    @Test
    fun `selecting another device with the panel open swaps its content`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.selectDevice("d2", hasCoordinates = true)

        assertTrue("the panel must stay open", controller.detailsVisible.value)
        assertEquals("the panel content must switch to the new device", "d2", controller.selectedDeviceId.value)
    }

    @Test
    fun `clearSelection clears selection and details`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.clearSelection()

        assertNull(controller.selectedDeviceId.value)
        assertFalse(controller.detailsVisible.value)
    }

    @Test
    fun `selection cleared on a gone device also closes details`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.onMarkersChanged(setOf("d2", "d3"))

        assertNull(controller.selectedDeviceId.value)
        assertFalse(controller.detailsVisible.value)
    }

    @Test
    fun `clearSelection drops the selection`() = runTest {
        val controller = LocationsUiController(backgroundScope)
        controller.selectDevice("d1", hasCoordinates = true)

        controller.clearSelection()

        assertNull(controller.selectedDeviceId.value)
    }
}