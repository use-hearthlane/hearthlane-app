package org.hearthlane.core.relay

import android.util.Log
import org.hearthlane.core.connectivity.TailscaleAuthRequired
import org.hearthlane.core.connectivity.TsnetGateway

/**
 * Result of a single relay probe.
 */
sealed interface RelayTransportResult {

    object Success : RelayTransportResult

    data class Failure(
        val error: String,
        val authUrl: String? = null,
        val authRequired: Boolean = false,
    ) : RelayTransportResult
}

/**
 * A transport that can reach the relay. Implementations are path-specific: the
 * local transport talks over the normal Android network while the tailscale
 * transport goes exclusively through the embedded tsnet network.
 */
interface RelayTransport {

    val kind: RelayTransportKind

    suspend fun probe(): RelayTransportResult
}

/**
 * Probes the relay over the normal Android network. This transport never
 * touches the embedded Tailscale node, so a home-LAN success keeps Tailscale
 * stopped.
 */
class LocalRelayTransport(
    private val config: RelayConfig,
    private val http: RelayHttpTransport = localRelayHttpTransport(),
) : RelayTransport {

    override val kind: RelayTransportKind = RelayTransportKind.LOCAL

    override suspend fun probe(): RelayTransportResult =
        runCatching {
            http.request(
                method = "GET",
                url = "${config.localBaseUrl.trimEnd('/')}/devices",
                contentType = null,
                body = null,
                headers = emptyMap(),
                timeoutMs = config.localTimeoutMs,
            )
        }.fold(
            onSuccess = { result ->
                if (result.statusCode in 200..299) {
                    RelayTransportResult.Success
                } else {
                    RelayTransportResult.Failure("relay probe failed: HTTP ${result.statusCode}")
                }
            },
            onFailure = { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "local relay probe failed: ${e.message}")
                RelayTransportResult.Failure(e.message ?: "local relay probe failed")
            },
        )

    private companion object {
        const val TAG = "RelayConnection"
    }
}

/**
 * Probes the relay exclusively over the embedded tsnet network.
 *
 * The transport first ensures the embedded node is running and connected (this
 * is the only place the node is started) and then issues the probe through the
 * tsnet gateway. It never falls back to the normal Android network.
 */
class TailscaleRelayTransport(
    private val gateway: TsnetGateway,
    private val config: RelayConfig,
    http: RelayHttpTransport = relayHttpTransportFor(RelayTransportKind.TAILSCALE, gateway),
) : RelayTransport {

    override val kind: RelayTransportKind = RelayTransportKind.TAILSCALE

    private val http: RelayHttpTransport = http

    override suspend fun probe(): RelayTransportResult =
        try {
            gateway.ensureRunning()
            Log.i(TAG, "relay probe via Tailscale started")
            val result = http.request(
                method = "GET",
                url = "${config.tailscaleBaseUrl.trimEnd('/')}/devices",
                contentType = null,
                body = null,
                headers = emptyMap(),
                timeoutMs = config.tailscaleProbeTimeoutMs,
            )
            if (result.statusCode in 200..299) {
                Log.i(TAG, "relay probe via Tailscale succeeded")
                RelayTransportResult.Success
            } else {
                RelayTransportResult.Failure("relay probe failed: HTTP ${result.statusCode}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: TailscaleAuthRequired) {
            Log.w(TAG, "Tailscale requires authentication (enrollment pending)")
            RelayTransportResult.Failure(
                error = "Tailscale requires authentication",
                authUrl = e.authUrl,
                authRequired = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "relay probe via Tailscale failed: ${e.message}")
            RelayTransportResult.Failure(e.message ?: "Tailscale relay probe failed")
        }

    private companion object {
        const val TAG = "RelayConnection"
    }
}