package com.homelab.poc.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.homelab.poc.R
import com.homelab.poc.core.frigate.FrigateConfig
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.FrigateConnectionManager
import com.homelab.poc.core.frigate.Go2RtcStreams
import com.homelab.poc.core.frigate.LocalTransport
import com.homelab.poc.core.frigate.TailscaleTransport
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway
import com.homelab.poc.core.frigate.bytesGetterFor
import com.homelab.poc.core.playback.LiveStreamPlayer
import com.homelab.poc.core.playback.PlaybackStatus
import com.homelab.poc.tailscale.TsnetGatewayImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PocCamera"

@Composable
fun HomeScreen(hostname: String, stateDir: String, frigateConfig: FrigateConfig) {
    val context = LocalContext.current
    val gateway = remember { TsnetGatewayImpl(hostname, stateDir, frigateConfig.tailscaleConnectTimeoutMs) }
    var baseUrl by rememberSaveable { mutableStateOf(frigateConfig.localBaseUrl) }
    var connection by remember { mutableStateOf<FrigateConnection?>(null) }
    var connecting by remember { mutableStateOf(false) }
    // Bumped only by an explicit connect request (initial load, Connect button).
    // Forces the live view to re-establish playback even when the transport is
    // unchanged.
    var connectAttempt by remember { mutableStateOf(0) }
    // Bumped after a network-callback probe succeeds. The live view uses this
    // only to recover a dead session, never to restart a healthy one, so
    // cellular handovers (which fire onLost/onAvailable frequently) stop
    // churning the HLS session.
    var networkTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun connect(restartPlayback: Boolean) {
        if (connecting) return
        connecting = true
        // The same URL is probed first over the home LAN and, on failure, over
        // the embedded Tailscale path where the hostname resolves through the
        // tailnet DNS (homelab DNS configured in the Tailscale admin console).
        val config = FrigateConfig(
            localBaseUrl = baseUrl.trim(),
            tailscaleBaseUrl = baseUrl.trim(),
        )
        Log.i(TAG, "connect requested (baseUrl=${config.localBaseUrl}, restartPlayback=$restartPlayback)")
        scope.launch {
            val manager = FrigateConnectionManager(
                config = config,
                localTransport = LocalTransport(config),
                tailscaleTransport = TailscaleTransport(gateway, config),
                tailscaleGateway = gateway,
            )
            val result = withContext(Dispatchers.IO) { manager.connect() }
            connection = result
            connecting = false
            if (restartPlayback) connectAttempt++ else networkTick++
        }
    }

    // Re-probe whenever the network changes (for example home Wi-Fi dropped):
    // re-probing local-first falls back to the embedded Tailscale path without
    // user interaction. The probe only refreshes the connection state; it never
    // restarts healthy playback (see networkTick), which would churn the HLS
    // session on cellular handovers. A short settle delay coalesces the rapid
    // onLost/onAvailable bursts that accompany a network switch.
    val currentConnect by rememberUpdatedState({ connect(restartPlayback = false) })
    val connectivityManager = LocalContext.current.getSystemService(ConnectivityManager::class.java)
    DisposableEffect(connectivityManager) {
        var probeJob: Job? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                probeJob?.cancel()
                probeJob = scope.launch {
                    delay(NETWORK_SETTLE_MS)
                    currentConnect()
                }
            }
            override fun onLost(network: Network) {
                probeJob?.cancel()
                probeJob = scope.launch {
                    delay(NETWORK_SETTLE_MS)
                    currentConnect()
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            probeJob?.cancel()
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    LaunchedEffect(Unit) { connect(restartPlayback = true) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.phase_two_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.url_settings_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.frigate_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.connection_state_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = connectionStateLabel(connection, connecting),
                    style = MaterialTheme.typography.titleLarge,
                )
                (connection as? FrigateConnection.Connected)?.let { connected ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.frigate_version_value,
                            connected.version,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    LiveView(
                        baseUrl = baseUrl.trim(),
                        gateway = gateway,
                        transport = connected.transport,
                        connectAttempt = connectAttempt,
                        networkTick = networkTick,
                    )
                }
                (connection as? FrigateConnection.Failed)?.let { failed ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.error_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val clipboard = LocalClipboardManager.current
                    SelectionContainer {
                        Text(
                            text = failed.error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(failed.error)) }) {
                        Text(stringResource(R.string.copy_error_button))
                    }
                    failed.authUrl?.let { authUrl ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.auth_url_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        SelectionContainer {
                            Text(
                                text = authUrl,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)),
                            )
                        }) {
                            Text(stringResource(R.string.open_auth_url_button))
                        }
                    }
                    if (failed.authRequired && failed.authUrl == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.auth_url_pending_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(onClick = { connect(restartPlayback = true) }, enabled = !connecting) {
                    Text(
                        stringResource(
                            if (connection is FrigateConnection.Failed) R.string.retry_button
                            else R.string.connect_button,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun connectionStateLabel(
    connection: FrigateConnection?,
    connecting: Boolean,
): String {
    if (connecting) return stringResource(R.string.connection_state_connecting)
    return when (val c = connection) {
        null -> stringResource(R.string.connection_state_idle)
        is FrigateConnection.Connected -> stringResource(
            R.string.connection_state_connected,
            when (c.transport) {
                TransportKind.LOCAL -> stringResource(R.string.transport_local)
                TransportKind.TAILSCALE -> stringResource(R.string.transport_tailscale)
            },
        )
        is FrigateConnection.Failed -> stringResource(R.string.connection_state_failed)
    }
}

/**
 * Phase 4 live-view spike: discovers the first go2rtc stream and plays its HLS
 * feed with ExoPlayer. Every media request goes through
 * [com.homelab.poc.core.connectivity.HttpBytesGetter] selected by [transport],
 * so the Tailscale path can never touch the Android network.
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun LiveView(
    baseUrl: String,
    gateway: TsnetGateway,
    transport: TransportKind,
    connectAttempt: Int,
    networkTick: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val getter = remember(transport, gateway) { bytesGetterFor(transport, gateway) }
    // Keyed by transport: the getter is transport-specific, so a network-driven
    // transport switch must rebuild the player on the new getter instead of
    // continuing on the dead one.
    val player = remember(transport, gateway) { LiveStreamPlayer(context.applicationContext, getter) }
    val scope = rememberCoroutineScope()

    val streamEmptyMessage = stringResource(R.string.live_view_stream_empty)

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var playbackStatus by remember { mutableStateOf<PlaybackStatus>(PlaybackStatus.Idle) }
    var resumeTick by remember { mutableStateOf(0) }
    // The user explicitly paused via the Pause button. Auto-triggers (unlock,
    // network switch, session recovery) must never restart playback while this
    // is set; only the Play button clears it. The lifecycle pause on ON_STOP is
    // separate and never sets this flag, so unlocking resumes playback.
    var userPaused by remember { mutableStateOf(false) }
    // Bounds automatic session recovery so a truly dead stream cannot loop
    // forever. A discovery is never silently dropped: the latest request
    // cancels and supersedes any in-flight one.
    var autoRecovery by remember { mutableStateOf(0) }
    // Timestamp of the last PLAYING transition; used to only renew the
    // recovery budget after the session was stable for a while.
    var playingSince by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Pause fetching while the screen is off and re-establish playback on
    // resume. The go2rtc HLS session dies on its ~5s keepalive, so unlocking
    // must refresh the media playlist URL (new session) unless the player is
    // still prepared.
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_RESUME -> resumeTick++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var discoveryJob: Job? = null
    fun discoverAndPlay() {
        // The latest discovery wins: cancel any in-flight one instead of
        // silently skipping, so a lifecycle/network resume is never swallowed
        // by a stale background attempt (which left a black player on unlock).
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            try {
                discoveryError = null
                val streams = Go2RtcStreams(getter)
                val name = streams.firstStreamName(baseUrl, STREAMS_TIMEOUT_MS)
                if (name == null) {
                    discoveryError = streamEmptyMessage
                } else {
                    // Creates the go2rtc HLS session (which starts the cold camera
                    // producer) and returns its media playlist URL, so ExoPlayer
                    // plays against a session that is already consuming. A fresh
                    // master request inside ExoPlayer would race the producer
                    // start; go2rtc answers that with an empty 200 when the
                    // consumer cannot attach.
                    val url = streams.resolveMediaPlaylistUrl(baseUrl, name, STREAMS_TIMEOUT_MS)
                    Log.i(TAG, "live stream resolved: $name -> $url via $transport")
                    streamUrl = url
                    player.play(url)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "stream discovery failed", e)
                discoveryError = e.message ?: e.toString()
            }
        }
    }

    // Re-establishes playback when something that invalidates the session
    // changes: transport/gateway (network path), resumeTick (screen unlock) or
    // connectAttempt (explicit reconnect). go2rtc drops an HLS session on its
    // ~5s keepalive, so a resume cannot trust the stale media playlist URL and
    // always needs a fresh session. networkTick only fires after a network
    // re-probe, so a healthy session is left alone (it churns on cellular
    // handovers); only a dead one is recovered. A paused user is never
    // restarted.
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
        if (userPaused) return@LaunchedEffect
        val dead = playbackStatus is PlaybackStatus.Error ||
            player.player.playbackState == Player.STATE_IDLE
        if (transportChanged || resumeChanged || connectChanged || dead) {
            discoverAndPlay()
        }
    }

    LaunchedEffect(player) {
        player.state.collectLatest { playbackStatus = it }
    }

    // Auto-recovery for mid-playback failures only: an error surfaced while
    // the user has paused is expected (the session dies on its ~5s keepalive
    // and a playlist refresh 404s), so it must never restart the video by
    // itself. The budget renews only after a session played stably, otherwise
    // a flapping stream stops retrying after MAX_AUTO_RECOVERY and surfaces
    // its error.
    LaunchedEffect(playbackStatus, streamUrl) {
        when (val status = playbackStatus) {
            is PlaybackStatus.Playing -> playingSince = SystemClock.elapsedRealtime()
            is PlaybackStatus.Error ->
                if (streamUrl != null && !userPaused) {
                    val stable = playingSince?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE
                    if (stable > STABLE_PLAY_MS) autoRecovery = 0
                    if (autoRecovery < MAX_AUTO_RECOVERY) {
                        autoRecovery++
                        Log.w(TAG, "live HLS playback failed (${status.message}); re-discovering a fresh go2rtc session")
                        discoverAndPlay()
                    }
                }
            else -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.live_view_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                R.string.live_view_transport,
                if (transport == TransportKind.LOCAL) {
                    stringResource(R.string.transport_local)
                } else {
                    stringResource(R.string.transport_tailscale)
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))

        val url = streamUrl
        when {
            url != null -> AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        // The controller is disabled: a paused live view must
                        // stay paused until the user presses the app's Play
                        // button (see LiveView's userPaused), and auto-recovery
                        // owns mid-playback failures.
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
            discoveryError != null -> CopyableError(text = discoveryError.orEmpty())
            else -> Text(
                text = stringResource(R.string.live_view_discovering),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        val status = playbackStatus
        // While paused a session-expiry error is expected (the go2rtc session
        // dies on its ~5s keepalive); showing it would look like a failure.
        // The session is re-established on resume instead.
        if (userPaused) {
            Text(
                text = stringResource(R.string.live_view_paused),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (status is PlaybackStatus.Error) {
            CopyableError(
                text = status.message,
                styledText = stringResource(R.string.live_view_error, status.message),
            )
        } else {
            Text(
                text = playbackLabel(status),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (url != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val idle = player.player.playbackState == Player.STATE_IDLE
                if (idle || userPaused) {
                    Button(onClick = {
                        userPaused = false
                        discoverAndPlay()
                    }) {
                        Text(stringResource(R.string.live_view_play))
                    }
                } else {
                    Button(onClick = {
                        player.player.pause()
                        userPaused = true
                    }) {
                        Text(stringResource(R.string.live_view_pause))
                    }
                }
                TextButton(onClick = { player.player.stop() }) {
                    Text(stringResource(R.string.live_view_stop))
                }
            }
        }
    }
}

@Composable
private fun playbackLabel(status: PlaybackStatus): String =
    when (status) {
        PlaybackStatus.Idle -> stringResource(R.string.live_view_starting)
        PlaybackStatus.Loading -> stringResource(R.string.live_view_starting)
        PlaybackStatus.Playing -> stringResource(R.string.live_view_playing)
        is PlaybackStatus.Error ->
            stringResource(R.string.live_view_error, status.message)
    }

/**
 * Shows an error that can be selected and copied verbatim, so on-device
 * diagnostics (playback errors, discovery failures) can be pasted back to the
 * developer without transcription.
 */
@Composable
private fun CopyableError(
    text: String,
    styledText: String = text,
) {
    val clipboard = LocalClipboardManager.current
    SelectionContainer {
        Text(
            text = styledText,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
        Text(stringResource(R.string.copy_error_button))
    }
}

private const val STREAMS_TIMEOUT_MS = 10_000L
private const val MAX_AUTO_RECOVERY = 2
private const val STABLE_PLAY_MS = 10_000L
// Coalesces the rapid onLost/onAvailable bursts that accompany a network
// switch before re-probing, so a handover does not churn the connection.
private const val NETWORK_SETTLE_MS = 1_000L
