package com.homelab.poc.core.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.homelab.poc.core.connectivity.HttpStreamGetter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the [ExoPlayer] for a single Frigate event clip.
 *
 * The clip is played with [ProgressiveMediaSource] over
 * [StreamingHttpDataSourceFactory], so every byte flows from the injected
 * [HttpStreamGetter] (the transport is resolved outside this class — LOCAL and
 * TAILSCALE are decided by the composition root, never here) straight into the
 * player without materializing the file in memory.
 *
 * [play] is idempotent: calling it again (retry/replay) releases the previous
 * media source and starts a fresh one on the same player instance, so there is
 * never more than one active player or stream. [release] must be called when
 * the screen leaves; it also closes the streaming connection.
 */
@UnstableApi
class EventPlayer(
    context: Context,
    private val getter: HttpStreamGetter,
    private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val state: StateFlow<PlaybackStatus> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(PlayerMetrics())
    val metrics: StateFlow<PlayerMetrics> = _metrics.asStateFlow()

    private var preparedAtMs = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = playbackStatusForState(playbackState, _state.value)
        }

        override fun onRenderedFirstFrame() {
            val elapsed = SystemClock.elapsedRealtime() - preparedAtMs
            Log.i(TAG, "event playback first frame rendered ${elapsed}ms after play request")
            _metrics.update { it.copy(firstFrameElapsedMs = elapsed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause?.message?.takeIf { it.isNotBlank() }
            val message = listOfNotNull(
                error.errorCodeName,
                error.message,
                cause,
            ).distinct().joinToString(": ")
            _metrics.update { it.copy(errorCount = it.errorCount + 1) }
            Log.e(TAG, "event playback error: $message", error)
            _state.value = PlaybackStatus.Error(message, httpStatusFrom(error))
        }
    }

    init {
        player.addListener(listener)
    }

    /** Starts (or replaces) playback of [clipUrl]. Call [release] when done. */
    fun play(clipUrl: String) {
        preparedAtMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "event playback preparing via ${getter::class.simpleName}")
        val source = ProgressiveMediaSource.Factory(
            StreamingHttpDataSourceFactory(getter, connectTimeoutMs, this::onBytesTransferred),
        ).createMediaSource(MediaItem.fromUri(clipUrl))
        _state.value = PlaybackStatus.Loading
        // After an error (or an ended clip) the same player instance must start
        // from a clean slate, otherwise a retry can reuse a stale media source.
        if (player.playbackState == Player.STATE_IDLE) {
            player.stop()
            player.clearMediaItems()
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    /** Stops playback and releases the media source (no further requests). */
    fun stop() {
        player.stop()
    }

    /** Releases the player and removes the listener. Call when the screen leaves. */
    fun release() {
        player.removeListener(listener)
        Log.i(
            TAG,
            "event player released: errors=${_metrics.value.errorCount}, " +
                "bytes transferred=${_metrics.value.bytesTransferred}",
        )
        player.release()
    }

    private fun onBytesTransferred(bytes: Long) {
        _metrics.update { it.copy(bytesTransferred = it.bytesTransferred + bytes) }
    }

    private companion object {
        const val TAG = "PocCamera"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
    }
}

/**
 * Maps an ExoPlayer playback state to the app's [PlaybackStatus], preserving an
 * existing [PlaybackStatus.Error] when the player briefly drops to IDLE after a
 * fatal error (same rule as the live player). Extracted as a pure function so
 * the mapping (including [Player.STATE_ENDED]) is unit testable without a real
 * player.
 */
internal fun playbackStatusForState(
    playbackState: Int,
    current: PlaybackStatus,
): PlaybackStatus = when (playbackState) {
    Player.STATE_IDLE -> if (current is PlaybackStatus.Error) current else PlaybackStatus.Idle
    Player.STATE_BUFFERING -> PlaybackStatus.Loading
    Player.STATE_READY -> PlaybackStatus.Playing
    Player.STATE_ENDED -> PlaybackStatus.Ended
    else -> current
}