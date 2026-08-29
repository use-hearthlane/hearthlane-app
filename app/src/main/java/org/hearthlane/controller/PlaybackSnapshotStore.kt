package org.hearthlane.controller

import org.hearthlane.core.playback.PlaybackStatus
import org.hearthlane.core.playback.PlayerMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Playback diagnostics snapshot for the V1.5 Diagnostics screen.
 *
 * The live view owns its [LiveStreamPlayer] and its session-scoped
 * [PlayerMetrics]; those metrics die with the player. This store is the app
 * lifetime accumulator the Diagnostics screen reads: the live view records a
 * finished session into it on player release, so the counters survive leaving
 * the live view and describe the app session, not the last screen.
 *
 * Counters accumulate across sessions; the latest non-null first-frame value
 * and the most recent playback error are retained.
 */
data class PlaybackSnapshot(
    /** Coarse playback state label (idle / loading / playing / error). */
    val playbackState: String = "idle",
    /** Last playback error message, or null when none was surfaced. */
    val lastError: String? = null,
    val firstFrameElapsedMs: Long? = null,
    val errorCount: Int = 0,
    val bytesTransferred: Long = 0,
    val recoveryCount: Int = 0,
)

/** App-lifetime accumulator for [PlaybackSnapshot]; see the class doc. */
class PlaybackSnapshotStore {

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    /**
     * Records one finished live-view session. Counters are added to the
     * accumulator; the first-frame value and the error message keep the latest
     * non-null values.
     */
    fun record(status: PlaybackStatus, metrics: PlayerMetrics, recoveryCount: Int) {
        _snapshot.update { current ->
            current.copy(
                playbackState = statusLabel(status),
                lastError = (status as? PlaybackStatus.Error)?.message ?: current.lastError,
                firstFrameElapsedMs = metrics.firstFrameElapsedMs ?: current.firstFrameElapsedMs,
                errorCount = current.errorCount + metrics.errorCount,
                bytesTransferred = current.bytesTransferred + metrics.bytesTransferred,
                recoveryCount = current.recoveryCount + recoveryCount,
            )
        }
    }

    companion object {
        /** Stable label for [PlaybackStatus]; shared with the report builder. */
        fun statusLabel(status: PlaybackStatus): String = when (status) {
            PlaybackStatus.Idle -> "idle"
            PlaybackStatus.Loading -> "loading"
            PlaybackStatus.Playing -> "playing"
            PlaybackStatus.Ended -> "ended"
            is PlaybackStatus.Error -> "error"
        }
    }
}
