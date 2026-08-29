package org.hearthlane.diagnostics

import org.hearthlane.core.relay.RelayConnection
import org.hearthlane.location.LocationDiagnosticsMonitor
import org.hearthlane.location.LocationForegroundService
import org.hearthlane.location.LocationPermissionSnapshot
import org.hearthlane.location.LocationReadStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sanitized, presentation-ready observability snapshot for the location
 * capability, shown in the technical (English) Diagnostics screen.
 *
 * The snapshot only ever contains safe metadata: states, modes, intervals,
 * timestamps, the device id/nickname and classified results. It never carries
 * coordinates, payloads, accuracy or any secret.
 */
data class LocationDiagnosticsSnapshot(
    val sharingEnabled: String,
    val foregroundPermission: String,
    val backgroundPermission: String,
    val locationServices: String,
    val foregroundService: String,
    val publisherState: String,
    val publisherMode: String,
    val locationCheckIntervalLabel: String,
    val minPublishIntervalLabel: String,
    val movementThresholdLabel: String,
    val maxPublishIntervalLabel: String,
    val mapActiveIntervalLabel: String,
    val lastRead: String?,
    val lastReadResult: String?,
    val lastPublishAttempt: String?,
    val lastPublishResult: String?,
    val lastSuccessfulPublish: String?,
    val pendingLocation: String,
    val relay: String,
    val deviceId: String,
    val deviceNickname: String,
)

/**
 * Builds the [LocationDiagnosticsSnapshot] from the production state holders:
 * the persisted sharing preference, the real permission/location snapshot, the
 * publishing metadata reported by the foreground service monitor, the relay
 * connectivity and the device identity. Pure and testable without Android.
 */
fun buildLocationDiagnosticsSnapshot(
    sharingEnabled: Boolean,
    permissions: LocationPermissionSnapshot,
    backgroundPermissionRequired: Boolean,
    publishing: LocationDiagnosticsMonitor.PublishingState,
    relay: RelayConnection?,
    deviceId: String,
    deviceNickname: String,
    locationCheckIntervalMs: Long = LocationForegroundService.BACKGROUND_INTERVAL_MS,
    minPublishIntervalMs: Long = LocationForegroundService.MIN_PUBLISH_INTERVAL_MS,
    distanceThresholdMeters: Double = LocationForegroundService.DISTANCE_THRESHOLD_METERS,
    maxPublishIntervalMs: Long = LocationForegroundService.MAX_PUBLISH_INTERVAL_MS,
    mapActiveIntervalMs: Long = LocationForegroundService.ACTIVE_INTERVAL_MS,
): LocationDiagnosticsSnapshot = LocationDiagnosticsSnapshot(
    sharingEnabled = yesNo(sharingEnabled),
    foregroundPermission = if (permissions.foregroundGranted) "Granted" else "Denied",
    backgroundPermission = when {
        !backgroundPermissionRequired -> "Not required"
        permissions.backgroundGranted -> "Granted"
        else -> "Denied"
    },
    locationServices = if (permissions.locationEnabled) "Enabled" else "Disabled",
    foregroundService = if (publishing.serviceRunning) "Running" else "Stopped",
    publisherState = publisherStateLabel(publishing),
    publisherMode = publisherModeLabel(sharingEnabled, publishing.intervalMs, mapActiveIntervalMs),
    locationCheckIntervalLabel = intervalLabel(locationCheckIntervalMs),
    minPublishIntervalLabel = intervalLabel(minPublishIntervalMs),
    movementThresholdLabel = "${distanceThresholdMeters.toInt()} m",
    maxPublishIntervalLabel = intervalLabel(maxPublishIntervalMs),
    mapActiveIntervalLabel = intervalLabel(mapActiveIntervalMs),
    lastRead = timeLabel(publishing.lastReadAtMs),
    lastReadResult = classifyReadResult(publishing.lastReadResult),
    lastPublishAttempt = timeLabel(publishing.lastPublishAttemptAtMs),
    lastPublishResult = classifyResult(publishing.lastPublishResult),
    lastSuccessfulPublish = timeLabel(publishing.lastPublishAtMs),
    pendingLocation = yesNo(publishing.hasPendingLocation),
    relay = when (relay) {
        is RelayConnection.Connected -> "Reachable"
        is RelayConnection.Failed -> "Unreachable"
        else -> "Unknown"
    },
    deviceId = deviceId,
    deviceNickname = deviceNickname.ifBlank { "(unset)" },
)

/** "Waiting" while the loop is up and the last publish succeeded, "Error" when
 *  the last publish failed, "Idle" when the loop is down. A failure is a
 *  transient operational state: after a successful publish it becomes Waiting. */
private fun publisherStateLabel(publishing: LocationDiagnosticsMonitor.PublishingState): String = when {
    !publishing.publisherRunning -> "Idle"
    publishing.lastPublishResult != null && publishing.lastPublishResult != "Success" -> "Error"
    else -> "Waiting"
}

/** Mode reflects the actual active interval: Disabled when sharing is off,
 *  Map active while the map requests the active cadence, else Background. */
private fun publisherModeLabel(sharingEnabled: Boolean, intervalMs: Long, mapActiveIntervalMs: Long): String = when {
    !sharingEnabled -> "Disabled"
    intervalMs == mapActiveIntervalMs -> "Map active"
    else -> "Background"
}

/** Formats a duration as "5 min" or "30 sec" from the real configured value. */
private fun intervalLabel(ms: Long): String = when {
    ms % 60_000L == 0L -> "${ms / 60_000L} min"
    else -> "${ms / 1_000L} sec"
}

/** Classifies a read status name into a safe, human label. */
fun classifyReadResult(statusName: String?): String? = when (statusName) {
    null -> null
    LocationReadStatus.SUCCESS.name -> "Success"
    LocationReadStatus.NO_POSITION.name -> "Unavailable"
    LocationReadStatus.NO_PERMISSION.name -> "Permission denied"
    LocationReadStatus.LOCATION_DISABLED.name -> "Location disabled"
    LocationReadStatus.TIMEOUT.name -> "Timeout"
    else -> "Error"
}

/**
 * Classifies a raw publish outcome into a sanitized label. Network/HTTP/DNS
 * failures are collapsed so Diagnostics never leaks hostnames, paths or
 * payloads.
 */
fun classifyResult(raw: String?): String? = when {
    raw == null -> null
    raw == "Success" -> "Success"
    raw == "Location unavailable" -> "Location unavailable"
    raw.contains("HTTP 4") -> "HTTP 4xx"
    raw.contains("HTTP 5") -> "HTTP 5xx"
    raw.contains("timed out") || raw.contains("timeout") -> "Timeout"
    raw.contains("resolve") || raw.contains("UnknownHost") -> "DNS error"
    else -> "Network error"
}

/** "HH:mm:ss" local time for a wall-clock timestamp, or null when never. */
private fun timeLabel(atMs: Long?): String? = atMs?.let {
    TIME_FORMAT.format(Date(it))
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)