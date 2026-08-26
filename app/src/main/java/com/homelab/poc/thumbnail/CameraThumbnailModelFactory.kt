package com.homelab.poc.thumbnail

import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.FrigateEventApi
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway
import com.homelab.poc.core.frigate.bytesGetterFor
import kotlinx.coroutines.flow.StateFlow

/**
 * Builds the Coil model for a camera or event thumbnail without exposing
 * transport or getter details to the UI. The factory observes the shared
 * connection and picks the same getter that playback uses, so thumbnails follow
 * the local-first / Tailscale-fallback policy automatically.
 */
class CameraThumbnailModelFactory(
    private val connection: StateFlow<FrigateConnection?>,
    private val gateway: TsnetGateway,
) {

    /**
     * Returns the Coil model for the given camera. If the shared connection is
     * not [FrigateConnection.Connected] yet, LOCAL is used as a safe default;
     * cards are only rendered once connected, so the default is rarely used.
     */
    fun create(cameraId: String, baseUrl: String, refreshKey: Int): Any {
        val transport = (connection.value as? FrigateConnection.Connected)?.transport
            ?: TransportKind.LOCAL
        val getter = bytesGetterFor(transport, gateway)
        return FrigateSnapshot(cameraId, baseUrl, refreshKey, transport, getter)
    }

    /**
     * Returns the Coil model for an event thumbnail
     * (`/api/events/{id}/thumbnail.jpg`), reusing the same transport-scoped
     * getter as the camera thumbnails.
     */
    fun eventThumbnail(eventId: String, baseUrl: String): Any {
        val transport = (connection.value as? FrigateConnection.Connected)?.transport
            ?: TransportKind.LOCAL
        val getter = bytesGetterFor(transport, gateway)
        val url = FrigateEventApi(getter).thumbnailUrl(baseUrl, eventId)
        return FrigateSnapshot(eventId, baseUrl, 0, transport, getter, resourceUrl = url)
    }

    /**
     * Returns the Coil model for an event snapshot (`/api/events/{id}/snapshot.jpg`),
     * the full-frame image shown on the event-detail screen. Same transport-scoped
     * getter and cache identity as [eventThumbnail].
     */
    fun eventSnapshot(eventId: String, baseUrl: String): Any {
        val transport = (connection.value as? FrigateConnection.Connected)?.transport
            ?: TransportKind.LOCAL
        val getter = bytesGetterFor(transport, gateway)
        val url = FrigateEventApi(getter).snapshotUrl(baseUrl, eventId)
        return FrigateSnapshot(eventId, baseUrl, 0, transport, getter, resourceUrl = url)
    }
}
