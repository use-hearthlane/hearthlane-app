package com.homelab.poc.core.frigate

/**
 * Result of a transparent Frigate connection attempt. This is the only shape
 * the UI consumes: it never sees transport details beyond the [TransportKind]
 * label.
 */
sealed interface FrigateConnection {

    /** Frigate answered through the given transport. */
    data class Connected(
        val transport: TransportKind,
        val version: String,
    ) : FrigateConnection

    /** Both the local and the Tailscale path failed. */
    data class Failed(
        val error: String,
        /** Present only when the failure is a pending Tailscale enrollment. */
        val authUrl: String? = null,
        /** True when the failure is a pending Tailscale enrollment (the URL
         *  may still be unavailable if the login flow has not published it). */
        val authRequired: Boolean = false,
    ) : FrigateConnection
}
