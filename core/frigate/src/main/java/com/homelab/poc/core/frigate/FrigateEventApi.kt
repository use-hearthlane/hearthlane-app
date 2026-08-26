package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Raised when a requested event does not exist (HTTP 404 on `/api/events/{id}`).
 * Distinct from a transport failure and from other HTTP errors so the UI can
 * surface "event not found" without conflating it with a connectivity problem.
 */
class EventNotFoundException(eventId: String) :
    Exception("event not found: $eventId")

/**
 * Focused Frigate event integration, following the existing `core/frigate`
 * pattern: transport-agnostic via the injected [HttpBytesGetter], so the same
 * class serves the LOCAL and the TAILSCALE path (selected by the app via
 * [bytesGetterFor]); it never starts or drives the Tailscale node.
 *
 * Only the operations required by the "recent events -> event" experience are
 * exposed. There is no generic Frigate client.
 *
 * Error policy (consistent with [Go2RtcStreams]): a transport failure surfaces
 * as whatever exception the [HttpBytesGetter] throws; a non-2xx HTTP response
 * is surfaced as an [IOException]; a 404 on the event-detail endpoint is
 * surfaced as [EventNotFoundException].
 *
 * Event playback is not performed through this API: a playable recording is
 * identified through [Event.hasClip], its URL is resolved with [clipUrl], and
 * the clip body is consumed progressively by the playback module through the
 * streaming transport.
 */
class FrigateEventApi(private val getter: HttpBytesGetter) {

    /**
     * Returns the most recent events for a camera, newest first.
     *
     * @param cameraId Frigate camera key.
     * @param limit Page size (the Frigate default is 100).
     * @throws Exception on transport failure or a non-2xx response.
     */
    suspend fun recentEvents(baseUrl: String, cameraId: String, limit: Int): List<Event> =
        listEvents("$baseUrl/api/events?camera=$cameraId&limit=$limit")

    /**
     * Returns events strictly older than [before] for a camera, newest first.
     *
     * Used to paginate backwards from the `startTime` of the last event of the
     * previous page (`before` is exclusive on `start_time`).
     *
     * @param before Epoch `startTime` cursor, of the same type as [Event.startTime].
     * @throws Exception on transport failure or a non-2xx response.
     */
    suspend fun olderEvents(baseUrl: String, cameraId: String, limit: Int, before: Double): List<Event> =
        listEvents("$baseUrl/api/events?camera=$cameraId&limit=$limit&before=$before")

    /**
     * Returns a single event by its id.
     *
     * @throws EventNotFoundException when the event does not exist (HTTP 404).
     * @throws Exception on transport failure, another non-2xx response, or a
     *   malformed payload.
     */
    suspend fun event(baseUrl: String, eventId: String): Event {
        val url = "$baseUrl/api/events/$eventId"
        val result = get(url)
        when (result.statusCode) {
            404 -> throw EventNotFoundException(eventId)
            !in 200..299 -> throw IOException("GET $url -> HTTP ${result.statusCode}")
        }
        val body = result.body.toString(Charsets.UTF_8)
        return FrigateEventParser.parseSingle(body)
            ?: throw IOException("invalid event payload for $eventId")
    }

    /** Thumbnail resource endpoint for an event (best-effort small image). */
    fun thumbnailUrl(baseUrl: String, eventId: String): String =
        "$baseUrl/api/events/$eventId/thumbnail.jpg"

    /** Snapshot resource endpoint for an event (full-frame image). */
    fun snapshotUrl(baseUrl: String, eventId: String): String =
        "$baseUrl/api/events/$eventId/snapshot.jpg"

    /**
     * Playable clip resource endpoint for an event (`clip.mp4`). The clip is
     * consumed progressively by the playback module (never fetched through this
     * API); this resolver keeps the URL construction here, following the
     * [thumbnailUrl]/[snapshotUrl] pattern.
     */
    fun clipUrl(baseUrl: String, eventId: String): String =
        "$baseUrl/api/events/$eventId/clip.mp4"

    private suspend fun listEvents(url: String): List<Event> {
        val result = get(url)
        if (result.statusCode !in 200..299) {
            throw IOException("GET $url -> HTTP ${result.statusCode}")
        }
        val body = result.body.toString(Charsets.UTF_8)
        return try {
            FrigateEventParser.parseList(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("invalid events payload: ${e.message ?: "parse error"}")
        }
    }

    private suspend fun get(url: String): HttpBytesResult =
        getter.getBytes(url, REQUEST_TIMEOUT_MS)

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
