package org.hearthlane.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot camera focus request for the map. The [epoch] increases on every
 * selection, so re-selecting the same device re-triggers the focus even though
 * the [deviceId] is unchanged.
 */
data class FocusRequest(val deviceId: String, val epoch: Long)

/**
 * Selection state for the Locations screen.
 *
 * [selectedDeviceId] is the single source of truth shared by the device
 * selector and the map markers: tapping a chip or a marker both route through
 * [selectDevice], so there is never a second, independent selection state.
 *
 * Selection and "details panel open" are deliberately different concepts:
 * [detailsVisible] is a separate presentation flag. Tapping a marker (or chip)
 * selects the device AND opens the details; tapping empty map or pressing Back
 * closes only the details while keeping the device selected (the highlighted
 * marker stays). Selecting another device while the panel is open swaps its
 * content without closing it.
 *
 * Focusing the map is a separate concern: [focusRequests] only carries devices
 * that actually have coordinates, so an unavailable device (or one without a
 * plotted marker) is selectable but never produces a fake camera move.
 */
class LocationsUiController(private val scope: CoroutineScope) {

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    val selectedDeviceId: StateFlow<String?> = _selectedDeviceId.asStateFlow()

    private val _detailsVisible = MutableStateFlow(false)
    val detailsVisible: StateFlow<Boolean> = _detailsVisible.asStateFlow()

    private val _focusRequests = MutableStateFlow<FocusRequest?>(null)
    val focusRequests: StateFlow<FocusRequest?> = _focusRequests.asStateFlow()

    private var epoch = 0L

    /**
     * Selects [deviceId], opens the details panel and, only when
     * [hasCoordinates] is true, requests the map to center on it. A device
     * without coordinates stays selected (the selector highlights it) but never
     * moves the camera to a fake position.
     */
    fun selectDevice(deviceId: String, hasCoordinates: Boolean) {
        _selectedDeviceId.value = deviceId
        _detailsVisible.value = true
        if (hasCoordinates) {
            epoch += 1
            _focusRequests.value = FocusRequest(deviceId, epoch)
        }
    }

    /** Closes the details panel; the selection (highlighted marker) is kept. */
    fun hideDetails() {
        _detailsVisible.value = false
    }

    /** Drops the current selection (e.g. when the device disappears). */
    fun clearSelection() {
        _selectedDeviceId.value = null
        _detailsVisible.value = false
    }

    /** Drops the selection when the selected device is no longer present. */
    fun onMarkersChanged(deviceIds: Set<String>) {
        val selected = _selectedDeviceId.value
        if (selected != null && selected !in deviceIds) {
            _selectedDeviceId.value = null
            _detailsVisible.value = false
        }
    }
}