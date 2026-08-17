package com.homelab.poc.ui

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.homelab.poc.R
import com.homelab.poc.controller.PlaybackSnapshotStore
import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.frigate.Go2RtcStreams
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway
import com.homelab.poc.core.frigate.bytesGetterFor
import com.homelab.poc.core.playback.LiveStreamPlayer
import com.homelab.poc.core.playback.PlaybackStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "PocCamera"

/**
 * Family-facing live view for a single selected camera. Receives the [cameraId]
 * chosen on Home and plays the go2rtc stream whose name equals that id (the
 * proven camera.id == stream name relation), never a "first stream". Every
 * media request goes through
 * [com.homelab.poc.core.connectivity.HttpBytesGetter] selected by [transport],
 * so the Tailscale path can never touch the Android network.
 *
 * V1.6: all technical details (transport label, metrics, error codes) are
 * removed from the family-facing UI. Only product states are shown. When the
 * LOCAL recovery budget is exhausted, a simple "Connection lost" /
 * "Try again" state is surfaced.
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
internal fun LiveView(
    cameraId: String,
    baseUrl: String,
    gateway: TsnetGateway,
    transport: TransportKind,
    connectAttempt: Int,
    networkTick: Int,
    modifier: Modifier = Modifier,
    playbackSnapshotStore: PlaybackSnapshotStore? = null,
    testGetter: HttpBytesGetter? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val getter = testGetter ?: remember(transport, gateway) { bytesGetterFor(transport, gateway) }
    // Keyed by transport: the getter is transport-specific, so a network-driven
    // transport switch must rebuild the player on the new getter instead of
    // continuing on the dead one.
    val player = remember(transport, gateway) {
        Log.i(TAG, "player created for transport=$transport")
        LiveStreamPlayer(context.applicationContext, getter)
    }
    val scope = rememberCoroutineScope()

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var playbackStatus by remember { mutableStateOf<PlaybackStatus>(PlaybackStatus.Idle) }
    val metrics by player.metrics.collectAsState()
    var resumeTick by remember { mutableStateOf(0) }
    var streamResolved by remember { mutableStateOf(false) }
    var recoveryCount by remember { mutableStateOf(0) }
    var autoRecovery by remember { mutableStateOf(0) }
    var playingSince by remember { mutableStateOf<Long?>(null) }
    // True when LOCAL recovery budget is exhausted and the player is dead.
    // Shows "Connection lost" / "Try again" instead of "Playing" on black.
    // Cleared by manual retry or successful re-establishment.
    var recoveryExhausted by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose {
            Log.i(TAG, "player released (live view left or transport switched)")
            playbackSnapshotStore?.record(player.state.value, player.metrics.value, recoveryCount)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_RESUME -> resumeTick++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var discoveryJob: Job? = null
    var discoveryRetryJob: Job? = null
    fun discoverAndPlay() {
        discoveryJob?.cancel()
        discoveryRetryJob?.cancel()
        discoveryJob = scope.launch {
            try {
                unavailable = false
                val streams = Go2RtcStreams(getter)
                val name = if (streamResolved) {
                    cameraId
                } else {
                    streams.streamNameForCamera(baseUrl, cameraId, STREAMS_TIMEOUT_MS)
                }
                if (name == null) {
                    unavailable = true
                } else {
                    val url = streams.resolveMediaPlaylistUrl(baseUrl, name, STREAMS_TIMEOUT_MS)
                    Log.i(TAG, "live stream resolved: camera=$cameraId stream=$name -> $url via $transport")
                    streamResolved = true
                    streamUrl = url
                    recoveryExhausted = false
                    player.play(url)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                streamResolved = false
                Log.e(TAG, "stream discovery failed", e)
                val stable = playingSince?.let { SystemClock.elapsedRealtime() - it }
                if (stable != null && stable > STABLE_PLAY_MS) autoRecovery = 0
                val canRecover = transport == TransportKind.TAILSCALE || autoRecovery < MAX_AUTO_RECOVERY
                if (canRecover) {
                    val attempt = autoRecovery + 1
                    if (transport != TransportKind.TAILSCALE) autoRecovery++
                    recoveryCount++
                    discoveryRetryJob?.cancel()
                    discoveryRetryJob = scope.launch {
                        Log.w(
                            TAG,
                            "stream discovery failed; retrying in ${DISCOVERY_RETRY_DELAY_MS}ms " +
                                "(${if (transport == TransportKind.TAILSCALE) "unbounded TAILSCALE recovery" else "attempt $attempt/$MAX_AUTO_RECOVERY"})",
                        )
                        delay(DISCOVERY_RETRY_DELAY_MS)
                        discoverAndPlay()
                    }
                } else {
                    recoveryExhausted = true
                }
            }
        }
    }

    var lastTransport by remember { mutableStateOf<TransportKind?>(null) }
    var lastResumeTick by remember { mutableStateOf(0) }
    var lastConnectAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(transport, gateway, resumeTick, connectAttempt, networkTick) {
        val transportChanged = lastTransport != transport
        val resumeChanged = lastResumeTick != resumeTick
        val connectChanged = lastConnectAttempt != connectAttempt
        lastTransport = transport
        lastResumeTick = resumeTick
        lastConnectAttempt = connectAttempt
        val dead = playbackStatus is PlaybackStatus.Error ||
            player.player.playbackState == Player.STATE_IDLE
        if (transportChanged || resumeChanged || connectChanged || dead) {
            recoveryExhausted = false
            discoverAndPlay()
        }
    }

    LaunchedEffect(player) {
        player.state.collectLatest { playbackStatus = it }
    }

    // Auto-recovery for mid-playback failures. The budget renews only after a
    // session played stably. Over TAILSCALE every error triggers a fresh-session
    // recovery with no budget. LOCAL stays bounded because a genuine failure
    // there is not expected.
    LaunchedEffect(playbackStatus, streamUrl, transport) {
        when (val status = playbackStatus) {
            is PlaybackStatus.Playing -> {
                playingSince = SystemClock.elapsedRealtime()
                recoveryExhausted = false
            }
            is PlaybackStatus.Error ->
                if (streamUrl != null) {
                    val stable = playingSince?.let { SystemClock.elapsedRealtime() - it }
                    if (stable != null && stable > STABLE_PLAY_MS) autoRecovery = 0
                    val canRecover = transport == TransportKind.TAILSCALE ||
                        autoRecovery < MAX_AUTO_RECOVERY
                    if (canRecover) {
                        val attempt = autoRecovery + 1
                        if (transport != TransportKind.TAILSCALE) autoRecovery++
                        recoveryCount++
                        Log.w(
                            TAG,
                            "live HLS playback failed (HTTP ${status.statusCode ?: "n/a"}): ${status.message}; " +
                                "auto-recovering with a fresh go2rtc session " +
                                "(${if (transport == TransportKind.TAILSCALE) "unbounded TAILSCALE recovery" else "attempt $attempt/$MAX_AUTO_RECOVERY"})",
                        )
                        discoverAndPlay()
                    } else {
                        recoveryExhausted = true
                    }
                }
            else -> Unit
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val url = streamUrl
        when {
            // Recovery exhausted: show simple product state, not "Playing" on black.
            recoveryExhausted && !unavailable -> {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.live_view_connection_lost),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        recoveryExhausted = false
                        autoRecovery = 0
                        discoverAndPlay()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.home_try_again))
                }
            }
            url != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setPlayer(player.player)
                        }
                    },
                    update = { view -> view.player = player.player },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }
            unavailable -> {
                Text(
                    text = stringResource(R.string.live_view_camera_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { discoverAndPlay() }) {
                    Text(stringResource(R.string.home_try_again))
                }
            }
            else -> Text(
                text = stringResource(R.string.live_view_discovering),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!recoveryExhausted) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = playbackLabel(playbackStatus, recoveryExhausted),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (url != null && !recoveryExhausted) {
            val idle = player.player.playbackState == Player.STATE_IDLE
            if (idle) {
                Button(onClick = { discoverAndPlay() }) {
                    Text(stringResource(R.string.live_view_play))
                }
            } else {
                Button(onClick = { player.stop() }) {
                    Text(stringResource(R.string.live_view_stop))
                }
            }
        }
    }
}

@Composable
private fun playbackLabel(status: PlaybackStatus, recoveryExhausted: Boolean): String =
    when {
        recoveryExhausted -> stringResource(R.string.live_view_connection_lost)
        status is PlaybackStatus.Idle -> stringResource(R.string.live_view_starting)
        status is PlaybackStatus.Loading -> stringResource(R.string.live_view_starting)
        status is PlaybackStatus.Playing -> stringResource(R.string.live_view_playing)
        status is PlaybackStatus.Error -> stringResource(R.string.live_view_playing)
        else -> stringResource(R.string.live_view_starting)
    }

private const val STREAMS_TIMEOUT_MS = 10_000L
private const val MAX_AUTO_RECOVERY = 2
private const val STABLE_PLAY_MS = 10_000L
private const val DISCOVERY_RETRY_DELAY_MS = 1_500L
