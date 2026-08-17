package com.homelab.poc.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
 * When playing, only the video is shown — no status labels or stop controls.
 * A fullscreen toggle overlay is available on the player in all modes.
 *
 * @param fullscreen when true the player fills the entire screen with no
 *   chrome. The fullscreen toggle overlay allows exiting back to normal mode.
 * @param onToggleFullscreen called when the user taps the fullscreen toggle.
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
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val getter = testGetter ?: remember(transport, gateway) { bytesGetterFor(transport, gateway) }
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

    val onRetry: () -> Unit = {
        recoveryExhausted = false
        autoRecovery = 0
        discoverAndPlay()
    }

    if (fullscreen) {
        LiveViewFullscreen(
            player = player,
            streamUrl = streamUrl,
            unavailable = unavailable,
            recoveryExhausted = recoveryExhausted,
            onRetry = onRetry,
            onToggleFullscreen = onToggleFullscreen,
        )
    } else if (isLandscape) {
        LiveViewLandscape(
            player = player,
            streamUrl = streamUrl,
            unavailable = unavailable,
            recoveryExhausted = recoveryExhausted,
            onRetry = onRetry,
            onToggleFullscreen = onToggleFullscreen,
            modifier = modifier,
        )
    } else {
        LiveViewPortrait(
            player = player,
            streamUrl = streamUrl,
            unavailable = unavailable,
            recoveryExhausted = recoveryExhausted,
            onRetry = onRetry,
            onToggleFullscreen = onToggleFullscreen,
            modifier = modifier,
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun LiveViewPortrait(
    player: LiveStreamPlayer,
    streamUrl: String?,
    unavailable: Boolean,
    recoveryExhausted: Boolean,
    onRetry: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        when {
            recoveryExhausted && !unavailable -> {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.live_view_connection_lost),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.home_try_again))
                }
            }
            streamUrl != null -> {
                Box {
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
                    FullscreenToggle(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    )
                }
            }
            unavailable -> {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.live_view_camera_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.home_try_again))
                }
            }
            else -> Text(
                text = stringResource(R.string.live_view_discovering),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun LiveViewLandscape(
    player: LiveStreamPlayer,
    streamUrl: String?,
    unavailable: Boolean,
    recoveryExhausted: Boolean,
    onRetry: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            recoveryExhausted && !unavailable -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.live_view_connection_lost),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.home_try_again))
                    }
                }
            }
            streamUrl != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setPlayer(player.player)
                        }
                    },
                    update = { view -> view.player = player.player },
                    modifier = Modifier.fillMaxSize(),
                )
                FullscreenToggle(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
            unavailable -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.live_view_camera_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.home_try_again))
                    }
                }
            }
            else -> Text(
                text = stringResource(R.string.live_view_discovering),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun LiveViewFullscreen(
    player: LiveStreamPlayer,
    streamUrl: String?,
    unavailable: Boolean,
    recoveryExhausted: Boolean,
    onRetry: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            recoveryExhausted && !unavailable -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.live_view_connection_lost),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.home_try_again))
                    }
                }
            }
            streamUrl != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setPlayer(player.player)
                        }
                    },
                    update = { view -> view.player = player.player },
                    modifier = Modifier.fillMaxSize(),
                )
                FullscreenToggle(
                    onClick = onToggleFullscreen,
                    isFullscreen = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
            unavailable -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.live_view_camera_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.home_try_again))
                    }
                }
            }
            else -> Text(
                text = stringResource(R.string.live_view_discovering),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun FullscreenToggle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = if (isFullscreen) {
                Icons.Filled.FullscreenExit
            } else {
                Icons.Filled.Fullscreen
            },
            contentDescription = if (isFullscreen) {
                stringResource(R.string.exit_fullscreen)
            } else {
                stringResource(R.string.enter_fullscreen)
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

private const val STREAMS_TIMEOUT_MS = 10_000L
private const val MAX_AUTO_RECOVERY = 2
private const val STABLE_PLAY_MS = 10_000L
private const val DISCOVERY_RETRY_DELAY_MS = 1_500L
