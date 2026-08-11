package com.homelab.poc.core.frigate

/** Which network path a Frigate connection actually used. */
enum class TransportKind {
    /** Direct connection over the normal Android network (home LAN). */
    LOCAL,

    /** Connection through the embedded Tailscale network. */
    TAILSCALE,
}
