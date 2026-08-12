package com.homelab.poc.core.frigate

import android.util.Log

/**
 * Probes Frigate exclusively over the embedded tsnet network.
 *
 * The transport first ensures the embedded node is running and connected (this
 * is the only place the node is started) and then issues the probe through a
 * getter that dials via tsnet. It never falls back to the normal Android
 * network: [TailscaleHttpGetter] delegates to [TsnetGateway.httpGet], which is
 * backed by tsnet's own dialer.
 */
class TailscaleTransport(
    private val gateway: TsnetGateway,
    private val config: FrigateConfig,
    probe: FrigateVersionProbe = FrigateVersionProbe(TailscaleHttpGetter(gateway)),
) : FrigateTransport {

    override val kind: TransportKind = TransportKind.TAILSCALE

    private val probe: FrigateVersionProbe = probe

    override suspend fun probe(): FrigateTransportResult {
        return try {
            gateway.ensureRunning()
            Log.i(TAG, "Frigate probe via Tailscale started")
            val version = probe.probe(config.tailscaleBaseUrl, config.tailscaleProbeTimeoutMs)
            Log.i(TAG, "Frigate probe via Tailscale succeeded (version=$version)")
            FrigateTransportResult.Success(version)
        } catch (e: TailscaleAuthRequired) {
            Log.w(TAG, "Tailscale requires authentication (enrollment pending)")
            FrigateTransportResult.Failure(
                error = "Tailscale requires authentication",
                authUrl = e.authUrl,
                authRequired = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Frigate probe via Tailscale failed: ${e.message}")
            FrigateTransportResult.Failure(e.message ?: "Tailscale probe failed")
        }
    }

    private companion object {
        const val TAG = "FrigateConnection"
    }
}

/** [HttpGetter] whose every request dials through the tsnet network. */
private class TailscaleHttpGetter(private val gateway: TsnetGateway) : HttpGetter {
    override suspend fun get(url: String, timeoutMs: Long): String =
        gateway.httpGet(url, timeoutMs)
}
