package org.hearthlane.core.relay

import org.hearthlane.core.connectivity.TsnetGateway
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Transparent relay connection strategy, mirroring the proven Frigate
 * strategy:
 *
 * 1. Probe over the local network with a short timeout (Tailscale stays
 *    stopped); on success report [RelayTransportKind.LOCAL].
 * 2. On local timeout/failure, start the embedded Tailscale node and probe
 *    again over the tsnet network; on success report [RelayTransportKind.TAILSCALE].
 * 3. Otherwise report [RelayConnection.Failed].
 *
 * Lifecycle policy:
 * - The embedded node is only started by the Tailscale fallback (via
 *   [tailscaleGateway]), never by the local probe.
 * - After a confirmed LOCAL success the node is released with
 *   [TsnetGateway.stopIfRunning] if a previous remote attempt left it running.
 * - The node is never stopped before the local result is known, and never
 *   started and stopped within a single probe cycle (no flapping).
 *
 * The UI consumes only the returned [RelayConnection]; it has no knowledge of
 * transports or the Tailscale lifecycle.
 */
class RelayConnectionManager(
    private val config: RelayConfig,
    private val localTransport: RelayTransport,
    private val tailscaleTransport: RelayTransport,
    private val tailscaleGateway: TsnetGateway,
) {

    suspend fun connect(): RelayConnection {
        Log.i(TAG, "local relay probe started (baseUrl=${config.localBaseUrl})")
        val local = withTimeoutOrNull(config.localTimeoutMs) { localTransport.probe() }
            ?: RelayTransportResult.Failure(
                "local relay probe timed out after ${config.localTimeoutMs}ms",
            )

        when (local) {
            is RelayTransportResult.Success -> {
                Log.i(TAG, "local relay probe succeeded")
                tailscaleGateway.stopIfRunning()
                return RelayConnection.Connected(RelayTransportKind.LOCAL)
            }
            is RelayTransportResult.Failure -> {
                Log.i(TAG, "local relay probe failed: ${local.error}")
            }
        }

        Log.i(TAG, "Tailscale relay fallback started")
        return when (val remote = tailscaleTransport.probe()) {
            is RelayTransportResult.Success -> {
                Log.i(TAG, "relay connection succeeded via Tailscale")
                RelayConnection.Connected(RelayTransportKind.TAILSCALE)
            }
            is RelayTransportResult.Failure -> {
                Log.i(TAG, "relay connection failed: ${remote.error}")
                RelayConnection.Failed(
                    error = remote.error,
                    authUrl = remote.authUrl,
                    authRequired = remote.authRequired,
                )
            }
        }
    }

    private companion object {
        const val TAG = "RelayConnection"
    }
}