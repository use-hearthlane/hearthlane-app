package com.homelab.poc.core.frigate

import android.util.Log

/**
 * Probes Frigate over the normal Android network. This transport never touches
 * the embedded Tailscale node, so a home-LAN success keeps Tailscale stopped.
 */
class LocalTransport(
    private val config: FrigateConfig,
    probe: FrigateVersionProbe = FrigateVersionProbe(HttpUrlConnectionGetter()),
) : FrigateTransport {

    override val kind: TransportKind = TransportKind.LOCAL

    private val probe: FrigateVersionProbe = probe

    override suspend fun probe(): FrigateTransportResult =
        try {
            FrigateTransportResult.Success(
                probe.probe(config.localBaseUrl, config.localTimeoutMs),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "local probe failed: ${e.message}")
            FrigateTransportResult.Failure(e.message ?: "local probe failed")
        }

    private companion object {
        const val TAG = "FrigateConnection"
    }
}
