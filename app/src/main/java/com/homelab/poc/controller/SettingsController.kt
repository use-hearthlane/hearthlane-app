package com.homelab.poc.controller

import com.homelab.poc.core.frigate.FrigateConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Product-facing summary of the current connection, derived from the shared
 * connection state without exposing infrastructure details (transport, LOCAL /
 * TAILSCALE, switch counters stay in Diagnostics).
 */
enum class ConnectionStatus {
    /** Frigate is reachable through the current path. */
    Connected,

    /** A connection attempt is in progress. */
    Connecting,

    /** Frigate is not reachable right now. */
    Unavailable,
}

/**
 * Screen state for the Settings screen. Only information that already exists
 * in the app is carried here: the configured server address, a product-facing
 * connection summary, the auto-play preference and the app version/build.
 */
data class SettingsState(
    val serverUrl: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unavailable,
    val autoPlayEventClips: Boolean = true,
    val appVersion: String,
    val appBuild: String,
)

/**
 * Shared observable state for the Settings screen, following the
 * Controller -> StateFlow -> Screen pattern used across the app.
 *
 * The controller only shapes presentation data: it reads the persisted server
 * address ([serverUrl]) and the shared connection flows, derives a friendly
 * [ConnectionStatus], exposes the auto-play preference, and delegates the
 * remote-access reconfigure action to the composition root. It never holds
 * Frigate/Tailscale/transport logic.
 */
class SettingsController(
    serverUrl: StateFlow<String>,
    connection: StateFlow<FrigateConnection?>,
    connecting: StateFlow<Boolean>,
    autoPlayEventClips: StateFlow<Boolean>,
    appVersion: String,
    appBuild: String,
    private val resetRemoteAccessAction: () -> Unit,
    private val setAutoPlayEventClipsAction: suspend (Boolean) -> Unit,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(
        SettingsState(
            serverUrl = serverUrl.value,
            connectionStatus = deriveConnectionStatus(connection.value, connecting.value),
            autoPlayEventClips = autoPlayEventClips.value,
            appVersion = appVersion,
            appBuild = appBuild,
        ),
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var collectJob: Job? = null

    init {
        collectJob = scope.launch {
            combine(serverUrl, connection, connecting, autoPlayEventClips) {
                    url,
                    conn,
                    isConnecting,
                    autoPlay,
                ->
                SettingsState(
                    serverUrl = url,
                    connectionStatus = deriveConnectionStatus(conn, isConnecting),
                    autoPlayEventClips = autoPlay,
                    appVersion = appVersion,
                    appBuild = appBuild,
                )
            }.collect { _state.value = it }
        }
    }

    /**
     * Persists the auto-play preference for event clips. The change applies to
     * future event-detail entries; a playback already in progress is untouched.
     */
    fun setAutoPlayEventClips(enabled: Boolean) {
        scope.launch { setAutoPlayEventClipsAction(enabled) }
    }

    /**
     * Reconfigures remote access: clears the device's remote-access identity so
     * the next connection attempt re-registers it. The composition root wires
     * this to the real reset and keeps the follow-up navigation.
     */
    fun resetRemoteAccess() = resetRemoteAccessAction()

    /** Stops observing the shared state. Call when the Settings screen leaves. */
    fun release() {
        collectJob?.cancel()
        collectJob = null
    }

    /** Maps the shared connection state to a product-facing [ConnectionStatus]. */
    fun deriveConnectionStatus(
        connection: FrigateConnection?,
        connecting: Boolean,
    ): ConnectionStatus = when {
        connecting -> ConnectionStatus.Connecting
        connection is FrigateConnection.Connected -> ConnectionStatus.Connected
        else -> ConnectionStatus.Unavailable
    }
}
