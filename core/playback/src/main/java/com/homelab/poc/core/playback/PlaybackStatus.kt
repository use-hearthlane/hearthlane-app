package com.homelab.poc.core.playback

/** Coarse live-playback status surfaced to the UI by the playback module. */
sealed interface PlaybackStatus {
    /** No stream requested yet. */
    data object Idle : PlaybackStatus

    /** Preparing or buffering the stream. */
    data object Loading : PlaybackStatus

    /** Playback is active. */
    data object Playing : PlaybackStatus

    /** Playback failed; [message] is safe to display. */
    data class Error(val message: String) : PlaybackStatus
}
