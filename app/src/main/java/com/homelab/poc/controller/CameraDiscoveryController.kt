package com.homelab.poc.controller

import android.util.Log
import com.homelab.poc.core.frigate.CameraDiscoveryState
import com.homelab.poc.core.frigate.FrigateCameraDiscovery
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway
import com.homelab.poc.core.frigate.bytesGetterFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared observable camera-discovery state for the Home screen.
 *
 * Discovery is transport-agnostic: it runs through the same
 * [com.homelab.poc.core.frigate.HttpBytesGetter] selection that playback uses
 * ([bytesGetterFor]), so the LOCAL path never touches the embedded Tailscale
 * node and the TAILSCALE path reuses the existing gateway without duplicated
 * HTTP logic.
 *
 * Discovery runs when the shared connection becomes [FrigateConnection.Connected]
 * with a new transport (initial launch or a switch), and on explicit refresh.
 * It does not poll continuously; refresh is manual, matching docs/V1.md
 * section 7.
 */
class CameraDiscoveryController(
    private val connection: StateFlow<FrigateConnection?>,
    private val baseUrl: () -> String,
    private val scope: CoroutineScope,
    private val discoverer: suspend (transport: TransportKind, baseUrl: String) -> CameraDiscoveryState,
) {

    private val _state = MutableStateFlow<CameraDiscoveryState>(CameraDiscoveryState.Loading)
    val state: StateFlow<CameraDiscoveryState> = _state.asStateFlow()

    /**
     * Bumped every time [refresh] is called. Camera cards include this key in
     * the Coil snapshot model so a manual refresh fetches a fresh thumbnail.
     */
    private val _refreshKey = MutableStateFlow(0)
    val refreshKey: StateFlow<Int> = _refreshKey.asStateFlow()

    private var observing = false
    private var discoveryJob: Job? = null
    private var discoveredTransport: TransportKind? = null

    /** Watches the shared connection and triggers discovery on transport changes. */
    fun start() {
        if (observing) return
        observing = true
        scope.launch {
            connection.collect { conn ->
                val transport = (conn as? FrigateConnection.Connected)?.transport
                if (transport != null && transport != discoveredTransport) {
                    discoveredTransport = transport
                    discover(transport)
                }
            }
        }
    }

    /** Stops the connection observer and cancels any in-flight discovery. */
    fun stop() {
        observing = false
        discoveryJob?.cancel()
    }

    /** Re-runs discovery on the current transport without re-probing the connection. */
    fun refresh() {
        val transport = (connection.value as? FrigateConnection.Connected)?.transport ?: return
        _refreshKey.value += 1
        discoveredTransport = transport
        discover(transport)
    }

    private fun discover(transport: TransportKind) {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            _state.value = CameraDiscoveryState.Loading
            val url = baseUrl().trim()
            val result = try {
                discoverer(transport, url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                CameraDiscoveryState.Error(e.message ?: "camera discovery failed")
            }
            logResult(result, transport)
            _state.value = result
        }
    }

    private fun logResult(result: CameraDiscoveryState, transport: TransportKind) {
        val summary = when (result) {
            is CameraDiscoveryState.Loaded -> {
                result.streamsWarning?.let {
                    Log.w(
                        TAG,
                        "camera discovery: /api/go2rtc/streams failed, " +
                            "cameras reported playable=false: $it",
                    )
                }
                "found ${result.cameras.size} cameras"
            }
            CameraDiscoveryState.Empty -> "no cameras"
            is CameraDiscoveryState.Error -> "failed: ${result.message}"
            CameraDiscoveryState.Loading -> "loading"
        }
        Log.i(TAG, "camera discovery $summary (transport=$transport)")
    }

    companion object {
        private const val TAG = "CameraDiscovery"
        private const val CONFIG_TIMEOUT_MS = 10_000L

        /**
         * Production discoverer that routes through the shared gateway using
         * the same getter selection as playback.
         */
        fun productionDiscoverer(
            gateway: TsnetGateway,
        ): suspend (TransportKind, String) -> CameraDiscoveryState = { transport, url ->
            FrigateCameraDiscovery(bytesGetterFor(transport, gateway))
                .discover(url, CONFIG_TIMEOUT_MS)
        }
    }
}
