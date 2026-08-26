package com.homelab.poc.thumbnail

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.frigate.TransportKind

/**
 * Coil model for a Frigate snapshot or event thumbnail.
 *
 * The image is fetched through the same [HttpBytesGetter] that the rest of the
 * app uses for the selected transport, so a snapshot over TAILSCALE is routed
 * through the embedded Tailscale path and never falls back to the OS network.
 * Screens do not see the getter; they only pass the identity and base URL.
 *
 * @param cameraId Stable cache identity: the camera key for camera thumbnails,
 *   or the event id for event thumbnails.
 * @param baseUrl Frigate server URL (already trimmed).
 * @param refreshKey Bumped by the discovery controller on refresh to bust
 *   stale snapshots without exposing transport details to the UI.
 * @param transport Transport kind used to reach Frigate; part of the stable
 *   Coil cache key so LOCAL and TAILSCALE never share the same entry.
 * @param getter Transport-scoped HTTP getter used by the custom Coil fetcher.
 * @param resourceUrl When set, an explicit resource URL to fetch instead of the
 *   derived camera `latest.jpg` URL. Used for event thumbnails
 *   (`/api/events/{id}/thumbnail.jpg`).
 */
internal data class FrigateSnapshot(
    val cameraId: String,
    val baseUrl: String,
    val refreshKey: Int,
    val transport: TransportKind,
    val getter: HttpBytesGetter,
    val resourceUrl: String? = null,
)

/**
 * Builds the best-effort image URL. With a [FrigateSnapshot.resourceUrl] the
 * explicit resource wins (event thumbnails); otherwise the camera `latest.jpg`
 * URL is derived. The `h` query parameter on the camera URL is a cache-busting
 * timestamp derived from the refresh key so manual refresh always fetches a
 * fresh image; it is not a secret or a credential.
 */
internal fun FrigateSnapshot.snapshotUrl(): String =
    resourceUrl ?: "${baseUrl.trimEnd('/')}/api/$cameraId/latest.jpg?h=$refreshKey"
