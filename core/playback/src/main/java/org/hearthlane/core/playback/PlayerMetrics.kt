package org.hearthlane.core.playback

/**
 * Diagnostic counters for one [LiveStreamPlayer] session. POC-only: these
 * feed the acceptance measurements (time to first frame, stability, error
 * visibility) without adding a metrics backend.
 *
 * - [firstFrameElapsedMs]: wall time between [LiveStreamPlayer.play] and the
 *   first rendered frame, in milliseconds. Null until a frame is rendered.
 * - [errorCount]: playback errors surfaced by the player (including expected
 *   session-expiry 404s that the auto-recovery then re-establishes).
 * - [bytesTransferred]: bytes delivered to the player by the HLS data source,
 *   i.e. playlists, init and media segments. Reset per player instance.
 */
data class PlayerMetrics(
    val firstFrameElapsedMs: Long? = null,
    val errorCount: Int = 0,
    val bytesTransferred: Long = 0,
)
