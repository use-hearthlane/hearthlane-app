package org.hearthlane.core.frigate

/**
 * Family-facing camera event model. The fields come exclusively from Frigate's
 * `/api/events` payload; Frigate remains the source of truth.
 *
 * The model carries only what the "recent events -> event -> playback"
 * experience needs. Frigate-specific payload details (bounding boxes, scores,
 * model metadata, path data, plus_id, etc.) belong to the integration layer
 * ([FrigateEventApi] and the parser) and are deliberately absent here.
 *
 * [endTime] is null while the event is still being recorded (Frigate reports
 * `end_time = null` for in-progress events). [hasClip] and [hasSnapshot]
 * indicate whether the corresponding Frigate resources exist for this event;
 * they must never be assumed true. [zones] is the (possibly empty) set of
 * Frigate zones the event was detected in.
 */
data class Event(
    /** Stable Frigate event id (unique across cameras). */
    val id: String,

    /** Frigate camera key the event belongs to. */
    val cameraId: String,

    /** Detected object label (e.g. "person", "car"); null when absent. */
    val label: String?,

    /** Event start as an epoch timestamp (seconds, with sub-second precision). */
    val startTime: Double,

    /** Event end as an epoch timestamp, or null while the event is ongoing. */
    val endTime: Double?,

    /** Whether a playable recording clip exists for this event. */
    val hasClip: Boolean,

    /** Whether a snapshot/thumbnail exists for this event. */
    val hasSnapshot: Boolean,

    /** Frigate zones this event was detected in; empty when none. */
    val zones: List<String>,
)
