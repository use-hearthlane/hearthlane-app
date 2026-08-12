package com.homelab.poc.core.frigate

/**
 * Internal parsed subset of a Frigate camera entry. The shape intentionally
 * mirrors only the fields required by camera discovery; all other payload
 * fields are ignored.
 */
internal data class CameraConfigDto(
    val id: String,
    val friendlyName: String?,
    val enabled: Boolean,
)

/**
 * Parses the Frigate `GET /api/config` payload into a minimal list of camera
 * DTOs. The parser is tolerant to missing/extra fields and to nested objects
 * of arbitrary depth.
 *
 * The camera key is the `cameras` dictionary key, which is the stable
 * identifier used by go2rtc. The inner `name` field is ignored because the
 * dictionary key is authoritative.
 */
internal object FrigateCameraConfigParser {

    fun parse(json: String): List<CameraConfigDto> {
        val cameras = CameraConfigJson.memberValue(json, CAMERAS) ?: return emptyList()
        if (!CameraConfigJson.isObject(cameras)) return emptyList()
        return CameraConfigJson.objectKeys(cameras).mapNotNull { key ->
            parseCamera(key, cameras)
        }
    }

    private fun parseCamera(key: String, cameras: String): CameraConfigDto? {
        if (key.isBlank()) return null
        val camera = CameraConfigJson.memberValue(cameras, key) ?: return null
        val enabled = CameraConfigJson.booleanValue(
            CameraConfigJson.memberValue(camera, ENABLED) ?: "true",
        ) ?: true
        val friendlyName = CameraConfigJson.memberValue(camera, FRIENDLY_NAME)
            ?.let(CameraConfigJson::stringValue)
        return CameraConfigDto(
            id = key,
            friendlyName = friendlyName,
            enabled = enabled,
        )
    }

    /** Maps a parsed DTO to the domain model; disabled cameras are excluded later. */
    fun toDomain(dto: CameraConfigDto, playable: Boolean = false): Camera = Camera(
        id = dto.id,
        displayName = displayName(dto.id, dto.friendlyName),
        enabled = dto.enabled,
        playable = playable,
    )

    /**
     * Display-name rule from docs/V1.md section 6.3: the friendly name is used
     * when present and non-empty; otherwise the camera key is used with no
     * transformation.
     */
    fun displayName(id: String, friendlyName: String?): String =
        if (friendlyName.isNullOrBlank()) id else friendlyName

    private const val CAMERAS = "cameras"
    private const val ENABLED = "enabled"
    private const val FRIENDLY_NAME = "friendly_name"
}
