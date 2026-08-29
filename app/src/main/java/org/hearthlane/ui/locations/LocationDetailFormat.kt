package org.hearthlane.ui.locations

import org.hearthlane.core.relay.LocationStatus
import org.hearthlane.location.DeviceMarker
import java.util.Locale

/**
 * Pure presentation formatting for the Locations detail experience: coordinate
 * text, accuracy label, the accuracy-circle decision and the clipboard text.
 * Kept free of Android/Compose types so the rules are unit-testable. The raw
 * domain values (latitude/longitude/accuracy) are never altered here.
 */

/**
 * Formats a coordinate pair as "lat,lon" with exactly six decimal places
 * (for example "-23.550520,-46.633308"), always using a dot decimal
 * separator regardless of the device locale.
 */
fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

/**
 * Accuracy label "±18 m" for display, or null when the accuracy is missing or
 * not a usable value (zero, negative, NaN/Infinite). The UI shows "Unavailable"
 * in that case rather than inventing a precision.
 */
fun formatAccuracy(accuracy: Float?): String? {
    if (accuracy == null || !accuracy.isFinite() || accuracy <= 0f) return null
    return "±${Math.round(accuracy)} m"
}

/**
 * Only a finite, positive accuracy yields a meaningful radius circle. Zero,
 * negative, NaN/Infinite or absent accuracy means no circle and no invented
 * value.
 */
fun shouldShowAccuracyCircle(accuracy: Float?): Boolean =
    accuracy != null && accuracy.isFinite() && accuracy > 0f

/** Clipboard text for a location: exactly "<latitude>,<longitude>" (6 decimals,
 *  no device id, no timestamp, no JSON, no trailing space). */
fun copyCoordinatesText(latitude: Double, longitude: Double): String =
    formatCoordinates(latitude, longitude)

/**
 * Every device with a valid location and a usable accuracy warrants an accuracy
 * circle, permanently — independent of selection and of the details panel. An
 * UNAVAILABLE device (no usable last position) gets no circle; a STALE device
 * keeps the circle for its last known accuracy.
 */
fun devicesWithAccuracyCircle(devices: List<DeviceMarker>): List<DeviceMarker> =
    devices.filter {
        it.status != LocationStatus.UNAVAILABLE && shouldShowAccuracyCircle(it.accuracyMeters)
    }

/**
 * Reconciliation plan for the accuracy circles between two polls. The overlay
 * keeps one polygon per device and applies only the deltas: create new ones,
 * update existing centers/radii in place, remove polygons for devices that no
 * longer warrant one. Successive identical polls produce an empty create/remove
 * plan, so overlays never accumulate.
 */
data class AccuracyCirclePlan(
    val toCreate: List<DeviceMarker>,
    val toUpdate: List<DeviceMarker>,
    val toRemove: Set<String>,
)

fun planAccuracyCircles(previousWanted: Set<String>, devices: List<DeviceMarker>): AccuracyCirclePlan {
    val wanted = devicesWithAccuracyCircle(devices)
    val wantedIds = wanted.map { it.deviceId }.toSet()
    return AccuracyCirclePlan(
        toCreate = wanted.filter { it.deviceId !in previousWanted },
        toUpdate = wanted.filter { it.deviceId in previousWanted },
        toRemove = previousWanted - wantedIds,
    )
}

/**
 * Pure content for the compact detail panel of the selected device.
 *
 * A STALE device keeps its (last-known) coordinates and accuracy circle but the
 * UI labels the freshness explicitly; an UNAVAILABLE device offers no
 * coordinates and no copy action. Accuracy never upgrades a stale/offline state.
 */
data class DeviceDetailUiState(
    val deviceId: String,
    val label: String,
    val status: LocationStatus,
    val accuracyLabel: String?,
    val coordinates: String?,
    val canCopy: Boolean,
    val showAccuracyCircle: Boolean,
)

/** Derives the detail state from a plotted marker (which always has a position). */
fun buildDeviceDetail(marker: DeviceMarker): DeviceDetailUiState = DeviceDetailUiState(
    deviceId = marker.deviceId,
    label = marker.label,
    status = marker.status,
    accuracyLabel = formatAccuracy(marker.accuracyMeters),
    coordinates = if (marker.status == LocationStatus.UNAVAILABLE) {
        null
    } else {
        formatCoordinates(marker.latitude, marker.longitude)
    },
    canCopy = marker.status != LocationStatus.UNAVAILABLE,
    showAccuracyCircle = shouldShowAccuracyCircle(marker.accuracyMeters),
)