package com.homelab.poc.core.frigate

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Transparent Frigate connection strategy:
 *
 * 1. Probe over the local network with a short timeout (Tailscale stays
 *    stopped); on success report [TransportKind.LOCAL].
 * 2. On local timeout/failure, start the embedded Tailscale node and probe
 *    again over the tsnet network; on success report [TransportKind.TAILSCALE].
 * 3. Otherwise report [FrigateConnection.Failed].
 *
 * Lifecycle policy:
 * - The embedded node is only started by the Tailscale fallback (via
 *   [tailscaleGateway]), never by the local probe.
 * - After a confirmed LOCAL success the node is released with
 *   [TsnetGateway.stopIfRunning] if a previous remote attempt left it running.
 * - The node is never stopped before the local result is known, and never
 *   started and stopped within a single probe cycle (no flapping).
 *
 * The UI consumes only the returned [FrigateConnection]; it has no knowledge
 * of transports or the Tailscale lifecycle.
 */
class FrigateConnectionManager(
    private val config: FrigateConfig,
    private val localTransport: FrigateTransport,
    private val tailscaleTransport: FrigateTransport,
    private val tailscaleGateway: TsnetGateway,
) {

    suspend fun connect(): FrigateConnection {
        Log.i(TAG, "local probe started (baseUrl=${config.localBaseUrl})")
        val local = withTimeoutOrNull(config.localTimeoutMs) { localTransport.probe() }
            ?: FrigateTransportResult.Failure(
                "local probe timed out after ${config.localTimeoutMs}ms",
            )

        when (local) {
            is FrigateTransportResult.Success -> {
                Log.i(TAG, "local probe succeeded (version=${local.version})")
                // Local access is confirmed: release the embedded node if a
                // previous remote attempt left it running. This is the only
                // place the node is stopped and it happens only on success.
                tailscaleGateway.stopIfRunning()
                return FrigateConnection.Connected(TransportKind.LOCAL, local.version)
            }
            is FrigateTransportResult.Failure -> {
                Log.i(TAG, "local probe failed: ${local.error}")
            }
        }

        Log.i(TAG, "Tailscale fallback started")
        return when (val remote = tailscaleTransport.probe()) {
            is FrigateTransportResult.Success -> {
                Log.i(TAG, "connection succeeded via Tailscale (version=${remote.version})")
                FrigateConnection.Connected(TransportKind.TAILSCALE, remote.version)
            }
            is FrigateTransportResult.Failure -> {
                Log.i(TAG, "connection failed: ${remote.error}")
                FrigateConnection.Failed(
                    error = remote.error,
                    authUrl = remote.authUrl,
                    authRequired = remote.authRequired,
                )
            }
        }
    }

    private companion object {
        const val TAG = "FrigateConnection"
    }
}
