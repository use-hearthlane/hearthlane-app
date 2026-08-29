package org.hearthlane.core.playback

/** Coarse live-playback status surfaced to the UI by the playback module. */
sealed interface PlaybackStatus {
    /** No stream requested yet. */
    data object Idle : PlaybackStatus

    /** Preparing or buffering the stream. */
    data object Loading : PlaybackStatus

    /** Playback is active. */
    data object Playing : PlaybackStatus

    /** Playback reached the end of the media. */
    data object Ended : PlaybackStatus

    /**
     * Playback failed; [message] is safe to display. [statusCode] is the HTTP
     * status of the response when the failure reached the server (null for
     * connection-level failures such as timeouts, DNS or refused connections);
     * the recovery path reports it in the log for diagnostics. Every error is
     * auto-retried with a fresh session.
     */
    data class Error(
        val message: String,
        val statusCode: Int? = null,
    ) : PlaybackStatus
}
