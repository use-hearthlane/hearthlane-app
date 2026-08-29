package org.hearthlane.location

import org.hearthlane.core.relay.LocationStatus
import org.hearthlane.core.relay.LocationStatusRules
import org.hearthlane.core.relay.RelayClient
import kotlinx.coroutines.CancellationException

/** Presentation model for one family member / device on the Locations map. */
data class DeviceMarker(
    val deviceId: String,
    /** Nickname when configured, otherwise the device's own identifier. */
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Freshness derived from the relay's last publication timestamp. */
    val status: LocationStatus,
    val recordedAtEpochMs: Long,
    val publishedAtEpochMs: Long?,
)

/** Outcome of a single Locations query cycle. */
enum class MapQueryStatus {
    /** The relay answered; the marker list may still be empty (never shared). */
    OK,

    /** The relay is not reachable on the current path. */
    RELAY_UNREACHABLE,

    /** The relay answered but the request failed; see [MapQueryResult.errorMessage]. */
    ERROR,
}

data class MapQueryResult(
    val status: MapQueryStatus,
    val markers: List<DeviceMarker> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Executes one Locations query against the relay: lists the known devices,
 * fetches each one's last location (last-known semantics) and derives the
 * freshness per the relay's contract rules. Devices that never published a
 * location are omitted rather than plotted.
 */
class LocationsQuery(
    private val client: suspend () -> RelayClient?,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun run(): MapQueryResult {
        val relay = client() ?: return MapQueryResult(MapQueryStatus.RELAY_UNREACHABLE)
        val devices = try {
            relay.listDevices()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return MapQueryResult(
                MapQueryStatus.ERROR,
                errorMessage = e.message ?: "listing devices failed",
            )
        }
        val markers = mutableListOf<DeviceMarker>()
        for (device in devices) {
            val location = try {
                relay.getLocation(device.deviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return MapQueryResult(
                    MapQueryStatus.ERROR,
                    errorMessage = e.message ?: "fetching location failed",
                )
            } ?: continue
            markers += DeviceMarker(
                deviceId = device.deviceId,
                label = device.nickname ?: device.deviceId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                status = LocationStatusRules.statusFor(location.publishedAtEpochMs, clockMs()),
                recordedAtEpochMs = location.recordedAtEpochMs,
                publishedAtEpochMs = location.publishedAtEpochMs,
            )
        }
        return MapQueryResult(MapQueryStatus.OK, markers = markers)
    }
}