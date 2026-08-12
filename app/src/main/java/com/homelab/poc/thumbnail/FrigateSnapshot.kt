package com.homelab.poc.thumbnail

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.frigate.TransportKind

/**
 * Coil model for a Frigate camera snapshot.
 *
 * The thumbnail is fetched through the same [HttpBytesGetter] that the rest of
 * the app uses for the selected transport, so a snapshot over TAILSCALE is
 * routed through the embedded Tailscale path and never falls back to the OS
 * network. The Home screen does not see the getter; it only passes the camera
 * identity and the current base URL.
 *
 * @param cameraId Stable Frigate camera key / go2rtc stream name.
 * @param baseUrl Frigate server URL (already trimmed).
 * @param refreshKey Bumped by the discovery controller on refresh to bust
 *   stale snapshots without exposing transport details to the UI.
 * @param transport Transport kind used to reach Frigate; part of the stable
 *   Coil cache key so LOCAL and TAILSCALE never share the same thumbnail entry.
 * @param getter Transport-scoped HTTP getter used by the custom Coil fetcher.
 */
internal data class FrigateSnapshot(
    val cameraId: String,
    val baseUrl: String,
    val refreshKey: Int,
    val transport: TransportKind,
    val getter: HttpBytesGetter,
)

/**
 * Builds the best-effort snapshot URL for a camera. The `h` query parameter is
 * a cache-busting timestamp derived from the refresh key so manual refresh
 * always fetches a fresh image; it is not a secret or a credential.
 */
internal fun FrigateSnapshot.snapshotUrl(): String =
    "${baseUrl.trimEnd('/')}/api/$cameraId/latest.jpg?h=$refreshKey"
