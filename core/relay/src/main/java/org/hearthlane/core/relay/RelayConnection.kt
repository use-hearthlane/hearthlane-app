package org.hearthlane.core.relay

/**
 * Result of a transparent relay connection attempt. This is the only shape the
 * UI consumes: it never sees transport details beyond the [RelayTransportKind]
 * label.
 */
sealed interface RelayConnection {

    /** The relay answered through the given transport. */
    data class Connected(
        val transport: RelayTransportKind,
    ) : RelayConnection

    /** Both the local and the Tailscale path failed. */
    data class Failed(
        val error: String,
        /** Present only when the failure is a pending Tailscale enrollment. */
        val authUrl: String? = null,
        /** True when the failure is a pending Tailscale enrollment (the URL
         *  may still be unavailable if the login flow has not published it). */
        val authRequired: Boolean = false,
    ) : RelayConnection
}

/** Which network path a [RelayConnection.Connected] transport resolved to. */
enum class RelayTransportKind {
    /** Request flows over the normal Android network (home LAN). */
    LOCAL,

    /** Request flows exclusively through the embedded tsnet network. */
    TAILSCALE,
}