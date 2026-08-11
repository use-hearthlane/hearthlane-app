package com.homelab.poc.core.frigate

/**
 * Outcome of a single transport probe. Both [FrigateTransport] implementations
 * reduce every outcome to these two cases so the manager never deals with
 * transport exceptions directly.
 */
sealed interface FrigateTransportResult {

    data class Success(val version: String) : FrigateTransportResult

    data class Failure(
        val error: String,
        /** Present only when the failure is a pending Tailscale enrollment. */
        val authUrl: String? = null,
    ) : FrigateTransportResult
}

/**
 * A single probe path to Frigate. [LocalTransport] uses the normal Android
 * network and [TailscaleTransport] uses the tsnet network exclusively.
 */
interface FrigateTransport {
    val kind: TransportKind

    /** Probes Frigate `/api/version` over this transport and returns the outcome. */
    suspend fun probe(): FrigateTransportResult
}
