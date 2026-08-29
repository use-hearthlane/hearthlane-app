package org.hearthlane.core.frigate

import org.hearthlane.core.connectivity.HttpBytesGetter
import kotlinx.coroutines.CancellationException

/**
 * Discovery state exposed to the consuming layer.
 *
 * The list of cameras is terminal; [Loading] is only emitted by the controller
 * while a discovery request is in flight.
 */
sealed interface CameraDiscoveryState {
    data object Loading : CameraDiscoveryState

    /**
     * Successful discovery of at least one enabled camera. Each camera carries
     * its resolved `playable` flag (exact id match against the go2rtc streams).
     *
     * [streamsWarning] is non-null only when `/api/config` succeeded but
     * `/api/go2rtc/streams` failed: the discovered cameras are preserved with
     * `playable = false` so a go2rtc endpoint failure never loses the list.
     */
    data class Loaded(
        val cameras: List<Camera>,
        val streamsWarning: String? = null,
    ) : CameraDiscoveryState

    data object Empty : CameraDiscoveryState
    data class Error(val message: String) : CameraDiscoveryState
}

/**
 * Discovers the Frigate cameras from `GET /api/config` and resolves each
 * camera's `playable` flag against `GET /api/go2rtc/streams`.
 *
 * Transport-agnostic: the [HttpBytesGetter] is injected, so the same class
 * serves the LOCAL and the TAILSCALE path (selected by the app via
 * [bytesGetterFor]); it never starts or drives the Tailscale node.
 *
 * Rules (docs/V1.md sections 6.1/6.4):
 *
 * - Disabled cameras (`enabled: false`) are excluded from the result.
 * - A payload with no cameras (or only disabled ones) yields
 *   [CameraDiscoveryState.Empty], which is a valid state, never an exception.
 * - An enabled camera is `playable` only when a go2rtc stream whose name
 *   exactly equals the camera key exists in `/api/go2rtc/streams`. Matching is
 *   by camera id, never by order or by picking a first stream. A camera
 *   without its stream stays in [CameraDiscoveryState.Loaded] with
 *   `playable = false`; the absence of streams never turns the whole discovery
 *   into [CameraDiscoveryState.Empty].
 * - When `/api/config` works but `/api/go2rtc/streams` fails, the discovered
 *   cameras are preserved as `playable = false` and the failure is recorded in
 *   [CameraDiscoveryState.Loaded.streamsWarning] for diagnostics.
 */
class FrigateCameraDiscovery(private val getter: HttpBytesGetter) {

    private val streams = Go2RtcStreams(getter)

    suspend fun discover(baseUrl: String, timeoutMs: Long): CameraDiscoveryState {
        val result = try {
            getter.getBytes("$baseUrl/api/config", timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CameraDiscoveryState.Error(
                e.message ?: "camera discovery transport failed",
            )
        }

        if (result.statusCode !in 200..299) {
            return CameraDiscoveryState.Error("GET /api/config -> HTTP ${result.statusCode}")
        }

        val body = result.body.toString(Charsets.UTF_8)
        val dtos = try {
            FrigateCameraConfigParser.parse(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CameraDiscoveryState.Error(
                "invalid Frigate config payload: ${e.message ?: "parse error"}",
            )
        }

        val enabledCameras = dtos.filter { it.enabled }
        if (enabledCameras.isEmpty()) {
            return CameraDiscoveryState.Empty
        }

        val (streamNames, streamsWarning) = availableStreamNames(baseUrl, timeoutMs)
        val cameras = enabledCameras.map { dto ->
            FrigateCameraConfigParser.toDomain(dto, playable = dto.id in streamNames)
        }
        return CameraDiscoveryState.Loaded(cameras, streamsWarning)
    }

    /**
     * Resolves the set of go2rtc stream names. A failure here is controlled,
     * never fatal: the cameras already discovered from `/api/config` are kept
     * with `playable = false` and the failure message is reported for
     * diagnostics.
     */
    private suspend fun availableStreamNames(
        baseUrl: String,
        timeoutMs: Long,
    ): Pair<Set<String>, String?> {
        return try {
            streams.streamNames(baseUrl, timeoutMs) to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet<String>() to (e.message ?: "go2rtc streams unavailable")
        }
    }
}
