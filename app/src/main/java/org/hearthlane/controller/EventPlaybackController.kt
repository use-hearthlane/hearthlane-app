package org.hearthlane.controller

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.common.util.UnstableApi
import org.hearthlane.core.connectivity.HttpStreamGetter
import org.hearthlane.core.playback.EventPlayer
import org.hearthlane.core.playback.PlaybackStatus
import org.hearthlane.core.playback.PlayerMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates [EventPlayer] for the event-playback screen.
 *
 * Owns the player lifecycle and resolves the clip URL through the injected
 * [clipUrl] provider (the URL is constructed by FrigateEventApi, not here). The
 * transport is selected by the composition root: this controller receives the
 * already-resolved [HttpStreamGetter] and never decides LOCAL vs TAILSCALE.
 *
 * Exposes [state] as the single source of truth the screen renders, following
 * the Controller -> StateFlow -> Screen pattern.
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
class EventPlaybackController(
    context: Context,
    private val getter: HttpStreamGetter,
    private val clipUrl: () -> String,
) {

    val player = EventPlayer(context.applicationContext, getter)
    val state: StateFlow<PlaybackStatus> = player.state
    val metrics: StateFlow<PlayerMetrics> = player.metrics

    /** Starts (or replays/retries) playback of the resolved clip URL. */
    fun play() = player.play(clipUrl())

    /** Stops playback and releases the media source. */
    fun stop() = player.stop()

    /** Releases the player. Call when the screen leaves. */
    fun release() = player.release()
}