package com.homelab.poc.core.frigate

/**
 * Internal parsed subset of a Frigate event entry. The shape intentionally
 * mirrors only the fields required by the Hearthlane event experience; all
 * other payload fields (data, box, region, score, model metadata, plus_id,
 * etc.) are ignored.
 */
internal data class EventDto(
    val id: String,
    val cameraId: String,
    val label: String?,
    val startTime: Double?,
    val endTime: Double?,
    val hasClip: Boolean,
    val hasSnapshot: Boolean,
    val zones: List<String>,
)

/**
 * Parses Frigate `/api/events` payloads into the minimal Hearthlane event
 * model. The parser is tolerant to missing/extra fields and to nested objects
 * or arrays of arbitrary depth, mirroring the approach used for camera
 * config parsing.
 *
 * - [parseList] parses an array of events, skipping entries that are not
 *   objects or that lack the required [EventDto.id]/[EventDto.startTime]
 *   fields, so a single malformed entry never discards the whole page.
 * - [parseSingle] parses one event object and returns null when it is not a
 *   valid event (used by the event-detail path, which treats a null result as
 *   a malformed response).
 * - Frigate's `end_time` is parsed as a nullable number (null while the event
 *   is still being recorded). `has_clip`/`has_snapshot` default to false when
 *   absent, and `zones` defaults to an empty list.
 *
 * Malformed input (a payload that is not a JSON array/object at all) raises
 * [IllegalArgumentException] so the caller can surface a controlled error.
 */
internal object FrigateEventParser {

    fun parseList(json: String): List<Event> =
        CameraConfigJson.arrayElements(json).mapNotNull(::parseEvent)

    fun parseSingle(json: String): Event? = parseEvent(json)

    private fun parseEvent(json: String): Event? {
        if (!CameraConfigJson.isObject(json)) return null
        val id = CameraConfigJson.stringValue(
            CameraConfigJson.memberValue(json, ID) ?: return null,
        )?.takeIf { it.isNotBlank() } ?: return null
        val startTime = CameraConfigJson.numberValue(
            CameraConfigJson.memberValue(json, START_TIME) ?: return null,
        ) ?: return null
        val camera = CameraConfigJson.stringValue(
            CameraConfigJson.memberValue(json, CAMERA) ?: return null,
        ) ?: ""
        val label = CameraConfigJson.memberValue(json, LABEL)
            ?.let(CameraConfigJson::stringValue)
        val endTime = CameraConfigJson.memberValue(json, END_TIME)
            ?.let(CameraConfigJson::numberValue)
        val hasClip = CameraConfigJson.memberValue(json, HAS_CLIP)
            ?.let(CameraConfigJson::booleanValue) ?: false
        val hasSnapshot = CameraConfigJson.memberValue(json, HAS_SNAPSHOT)
            ?.let(CameraConfigJson::booleanValue) ?: false
        val zones = parseZones(CameraConfigJson.memberValue(json, ZONES))
        return EventDto(
            id = id,
            cameraId = camera,
            label = label,
            startTime = startTime,
            endTime = endTime,
            hasClip = hasClip,
            hasSnapshot = hasSnapshot,
            zones = zones,
        ).let(::toDomain)
    }

    /** Maps a parsed DTO to the domain model. */
    fun toDomain(dto: EventDto): Event = Event(
        id = dto.id,
        cameraId = dto.cameraId,
        label = dto.label,
        startTime = dto.startTime ?: 0.0,
        endTime = dto.endTime,
        hasClip = dto.hasClip,
        hasSnapshot = dto.hasSnapshot,
        zones = dto.zones,
    )

    /** Parses a JSON array of zone-name strings; empty when absent or invalid. */
    private fun parseZones(raw: String?): List<String> {
        if (raw == null) return emptyList()
        return runCatching { CameraConfigJson.arrayElements(raw) }
            .getOrDefault(emptyList())
            .mapNotNull { CameraConfigJson.stringValue(it) }
    }

    private const val ID = "id"
    private const val CAMERA = "camera"
    private const val LABEL = "label"
    private const val START_TIME = "start_time"
    private const val END_TIME = "end_time"
    private const val HAS_CLIP = "has_clip"
    private const val HAS_SNAPSHOT = "has_snapshot"
    private const val ZONES = "zones"
}
