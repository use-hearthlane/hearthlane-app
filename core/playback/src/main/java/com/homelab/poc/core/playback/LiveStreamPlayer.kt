package com.homelab.poc.core.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.homelab.poc.core.connectivity.HttpBytesGetter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the [ExoPlayer] instance and its HLS media source for the Phase 4
 * spike. Every HLS request (playlist, media segments) flows through an
 * [HttpBytesDataSource] bound to the injected [HttpBytesGetter].
 *
 * The UI binds [player] to a PlayerView and observes [state]; it never talks
 * to Media3 internals.
 */
@UnstableApi
class LiveStreamPlayer(
    context: Context,
    private val getter: HttpBytesGetter,
    private val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
) {

    @UnstableApi
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val state: StateFlow<PlaybackStatus> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(PlayerMetrics())
    val metrics: StateFlow<PlayerMetrics> = _metrics.asStateFlow()

    // Wall-clock anchor for the time-to-first-frame measurement; reset on every
    // play() so a recovered session reports its own T2FF.
    private var preparedAtMs = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    // ExoPlayer drops to IDLE right after onPlayerError; keep
                    // the error visible so the UI does not bounce back to
                    // "starting". A later play() call resets the state.
                    if (_state.value !is PlaybackStatus.Error) {
                        _state.value = PlaybackStatus.Idle
                    }
                }
                Player.STATE_BUFFERING -> _state.value = PlaybackStatus.Loading
                Player.STATE_READY -> _state.value = PlaybackStatus.Playing
                Player.STATE_ENDED -> Log.i(TAG, "live playback ended")
            }
        }

        override fun onRenderedFirstFrame() {
            val elapsed = SystemClock.elapsedRealtime() - preparedAtMs
            Log.i(TAG, "first frame rendered ${elapsed}ms after play request")
            _metrics.update { it.copy(firstFrameElapsedMs = elapsed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Include the parser/loader cause when present: for a malformed
            // manifest the top-level message is just "Source Error".
            val cause = error.cause?.message?.takeIf { it.isNotBlank() }
            val message = listOfNotNull(
                error.errorCodeName,
                error.message,
                cause,
            ).distinct().joinToString(": ")
            _metrics.update { it.copy(errorCount = it.errorCount + 1) }
            Log.e(TAG, "playback error (count=${_metrics.value.errorCount}): $message", error)
            _state.value = PlaybackStatus.Error(message)
        }
    }

    init {
        player.addListener(listener)
    }

    /** Starts (or replaces) HLS playback. Call [release] when the screen leaves. */
    fun play(hlsUrl: String) {
        preparedAtMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "player preparing: $hlsUrl via ${getter::class.simpleName}")
        val source = HlsMediaSource.Factory(
            HttpBytesDataSourceFactory(getter, requestTimeoutMs, this::onBytesTransferred),
        ).createMediaSource(MediaItem.fromUri(hlsUrl))
        // Clear any previous error: a fresh media source starts from IDLE and
        // would otherwise keep the last error visible forever.
        _state.value = PlaybackStatus.Loading
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    private fun onBytesTransferred(bytes: Long) {
        _metrics.update { it.copy(bytesTransferred = it.bytesTransferred + bytes) }
    }

    /**
     * Releases the HLS media source so no further requests are made and no
     * bytes flow. Used by the Stop control and when the app goes to the
     * background (screen off): a `playWhenReady = false` would NOT stop the
     * network activity, because media3 keeps refreshing the HLS playlist
     * while the media source is loaded, which also keeps the go2rtc session
     * alive. Playback is re-established on the next [play] with a fresh
     * go2rtc session.
     */
    fun stop() {
        Log.i(TAG, "live playback stopped; media source released")
        player.stop()
    }

    fun release() {
        player.removeListener(listener)
        val m = _metrics.value
        Log.i(
            TAG,
            "player released: first frame=${m.firstFrameElapsedMs?.let { "${it}ms" } ?: "not rendered"}, " +
                "errors=${m.errorCount}, bytes transferred=${m.bytesTransferred}",
        )
        player.release()
    }

    private companion object {
        const val TAG = "PocCamera"
        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
    }
}
