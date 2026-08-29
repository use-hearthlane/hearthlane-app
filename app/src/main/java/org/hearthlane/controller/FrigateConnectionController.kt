package org.hearthlane.controller

import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import org.hearthlane.core.frigate.FrigateConfig
import org.hearthlane.core.frigate.FrigateConnection
import org.hearthlane.core.frigate.FrigateConnectionManager
import org.hearthlane.core.frigate.LocalTransport
import org.hearthlane.core.frigate.TailscaleTransport
import org.hearthlane.core.frigate.TransportKind
import org.hearthlane.core.connectivity.TsnetGateway
import org.hearthlane.settings.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared connection/session state for the app, extracted from the POC
 * HomeScreen. Owns the transparent connect strategy and the network-driven
 * re-probe lifecycle; the UI only consumes the exposed state flows and never
 * drives the Tailscale node lifecycle.
 *
 * The strategy itself is unchanged: [FrigateConnectionManager] probes the
 * normal Android network first and only starts the embedded Tailscale node as
 * a fallback (LOCAL -> Tailscale, and back to LOCAL when the LAN is reachable
 * again). Networking and playback code is untouched; this class only relocates
 * where the state lives.
 */
class FrigateConnectionController internal constructor(
    val gateway: TsnetGateway,
    private val settings: AppSettings,
    private val connectivityManager: ConnectivityManager,
    private val scope: CoroutineScope,
    private val connector: suspend (baseUrl: String) -> FrigateConnection,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Production constructor. Builds the proven transparent connection strategy
     * from the injected [gateway].
     */
    constructor(
        gateway: TsnetGateway,
        settings: AppSettings,
        connectivityManager: ConnectivityManager,
        scope: CoroutineScope,
    ) : this(
        gateway = gateway,
        settings = settings,
        connectivityManager = connectivityManager,
        scope = scope,
        connector = { baseUrl -> defaultConnect(baseUrl, gateway) },
        ioDispatcher = Dispatchers.IO,
    )

    /** Last connect result; null until the first attempt completes. */
    private val _connection = MutableStateFlow<FrigateConnection?>(null)
    val connection: StateFlow<FrigateConnection?> = _connection.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    /** Last transport reported by a successful probe; used for switch tracking. */
    private val _lastProbedTransport = MutableStateFlow<TransportKind?>(null)
    val lastProbedTransport: StateFlow<TransportKind?> = _lastProbedTransport.asStateFlow()

    /** How many times the transport switched; traced on-device (logcat). */
    private val _transportSwitchCount = MutableStateFlow(0)
    val transportSwitchCount: StateFlow<Int> = _transportSwitchCount.asStateFlow()

    /**
     * Bumped only by an explicit connect request (initial load, Connect
     * button). Forces the live view to re-establish playback even when the
     * transport is unchanged.
     */
    private val _connectAttempt = MutableStateFlow(0)
    val connectAttempt: StateFlow<Int> = _connectAttempt.asStateFlow()

    /**
     * Bumped after a network-callback probe succeeds. The live view uses this
     * only to recover a dead session, never to restart a healthy one, so
     * cellular handovers stop churning the HLS session.
     */
    private val _networkTick = MutableStateFlow(0)
    val networkTick: StateFlow<Int> = _networkTick.asStateFlow()

    /** Last failure message, kept for the Diagnostics report (V1.5). */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var probeJob: Job? = null
    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleReProbe("onAvailable")
        override fun onLost(network: Network) = scheduleReProbe("onLost")
    }

    /**
     * Starts the network-change listener. Called once by the composition root
     * and paired with [stop].
     */
    fun start() {
        if (callbackRegistered) return
        callbackRegistered = true
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
            .onFailure { Log.w(TAG, "network callback registration failed", it) }
    }

    /** Stops the network-change listener and cancels any pending re-probe. */
    fun stop() {
        if (!callbackRegistered) return
        callbackRegistered = false
        probeJob?.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            .onFailure { Log.w(TAG, "network callback unregistration failed", it) }
    }

    /**
     * Runs the transparent connect strategy. With [restartPlayback] true the
     * live view is forced to re-establish playback; false only refreshes the
     * connection state (network re-probes).
     */
    fun connect(restartPlayback: Boolean) {
        if (_connecting.value) return
        _connecting.value = true
        val baseUrl = settings.frigateBaseUrl.value
        Log.i(TAG, "connect requested (baseUrl=$baseUrl, restartPlayback=$restartPlayback)")
        scope.launch {
            try {
                val result = withContext(ioDispatcher) { runConnect(baseUrl) }
                if (result is FrigateConnection.Connected) {
                    val previous = _lastProbedTransport.value
                    val switched = previous != null && previous != result.transport
                    if (switched) {
                        _transportSwitchCount.update { it + 1 }
                        Log.i(
                            TAG,
                            "transport switched #${_transportSwitchCount.value}: $previous -> ${result.transport}",
                        )
                    } else if (previous == null) {
                        Log.i(TAG, "transport selected: ${result.transport}")
                    }
                    _lastProbedTransport.value = result.transport
                    _lastError.value = null
                } else {
                    (result as? FrigateConnection.Failed)?.let { _lastError.value = it.error }
                }
                _connection.value = result
                if (restartPlayback) _connectAttempt.update { it + 1 } else _networkTick.update { it + 1 }
            } finally {
                _connecting.value = false
            }
        }
    }

    /**
     * Clears the embedded node identity so the next probe re-enrolls
     * interactively, and resets the shared session state. Used by the
     * administrator Settings reset. The node keeps its hostname (the
     * persisted per-installation suffix is untouched) but loses its enrolled
     * identity; the transport counters are preserved as diagnostics history.
     */
    fun resetTailscale() {
        scope.launch {
            runCatching { gateway.reset() }
                .onFailure { Log.w(TAG, "tailscale identity reset failed", it) }
            _connection.value = null
            _lastProbedTransport.value = null
            _lastError.value = null
        }
    }

    /**
     * Runs the proven [FrigateConnectionManager] strategy for [baseUrl] and
     * returns the raw result. Used by the V1.1 setup flow: it reuses exactly
     * the same probe path as [connect] without touching the shared
     * [connection]/[connecting] flows or the playback ticks, so a setup test
     * never disturbs normal session state. Failures are recorded in
     * [lastError] for the Diagnostics report.
     */
    suspend fun testConnection(baseUrl: String): FrigateConnection {
        val result = withContext(ioDispatcher) { runConnect(baseUrl) }
        if (result is FrigateConnection.Failed) _lastError.value = result.error
        return result
    }

    /** The single construction of the transparent connection strategy, shared
     *  by [connect] and [testConnection] so the probe path is never forked. */
    private suspend fun runConnect(baseUrl: String): FrigateConnection = connector(baseUrl)

    companion object {

        /** Default connector used in production; extracted so tests can inject a fake. */
        private suspend fun defaultConnect(
            baseUrl: String,
            gateway: TsnetGateway,
        ): FrigateConnection {
            val config = FrigateConfig(
                localBaseUrl = baseUrl,
                tailscaleBaseUrl = baseUrl,
            )
            val manager = FrigateConnectionManager(
                config = config,
                localTransport = LocalTransport(config),
                tailscaleTransport = TailscaleTransport(gateway, config),
                tailscaleGateway = gateway,
            )
            return manager.connect()
        }

        const val TAG = "Hearthlane"

        // Coalesces the rapid onLost/onAvailable bursts that accompany a
        // network switch before re-probing, so a handover does not churn the
        // connection.
        const val NETWORK_SETTLE_MS = 1_000L
    }

    /**
     * Re-probes whenever the network changes (for example home Wi-Fi dropped):
     * re-probing local-first falls back to the embedded Tailscale path without
     * user interaction. A short settle delay coalesces the rapid
     * onLost/onAvailable bursts that accompany a network switch.
     */
    private fun scheduleReProbe(reason: String) {
        Log.i(TAG, "network transition detected: $reason")
        probeJob?.cancel()
        probeJob = scope.launch {
            delay(NETWORK_SETTLE_MS)
            connect(restartPlayback = false)
        }
    }
}
