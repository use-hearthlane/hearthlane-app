package com.homelab.poc.core.frigate

/**
 * Family-facing camera model. `id`, `displayName` and `enabled` come
 * exclusively from Frigate's `/api/config` payload; `playable` is resolved by
 * discovery against `/api/go2rtc/streams` (Frigate remains the source of
 * truth). The Frigate key is the stable technical identifier and the friendly
 * name comes from the same payload.
 *
 * V1.2 keeps the model intentionally small: only the fields needed to prove
 * camera discovery, the enabled/disabled rule from docs/V1.md section 6.4, and
 * the per-camera playable resolution against the go2rtc streams. `snapshotUrl`
 * is deferred to V1.3 (Home thumbnails).
 */
data class Camera(
    /** Frigate config key / go2rtc stream name. */
    val id: String,

    /**
     * Friendly name from Frigate when present and non-empty; otherwise the
     * [id] itself. No local overrides and no string transformations.
     */
    val displayName: String,

    /** `enabled` flag as reported by Frigate for this camera. */
    val enabled: Boolean,

    /**
     * True only when a go2rtc stream whose name equals [id] exists in
     * `/api/go2rtc/streams`. A camera enabled in the config but missing its
     * stream stays in the result with `playable = false` (future Home shows
     * "Camera unavailable"); it is never removed.
     */
    val playable: Boolean,
)
