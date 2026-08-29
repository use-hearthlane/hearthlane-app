package org.hearthlane.core.relay

/**
 * Static configuration for the transparent relay connection strategy.
 *
 * Both base URLs point at the same relay instance; each transport reaches it
 * through a different network path:
 *
 * - [localBaseUrl] is probed over the normal Android network (home LAN).
 * - [tailscaleBaseUrl] is probed exclusively through the embedded tsnet
 *   network and must resolve on the tailnet (MagicDNS name or 100.x address,
 *   or a LAN IP reachable through an advertised subnet route).
 *
 * No auto-discovery is implemented; the values are development configuration
 * supplied at build time and editable in Settings.
 *
 * The relay MVP has no application authentication: it trusts the private
 * LAN/Tailscale connectivity and never receives an `Authorization` header.
 */
data class RelayConfig(
    val localBaseUrl: String,
    val tailscaleBaseUrl: String,
    /**
     * Maximum time for the local probe before falling back to Tailscale.
     * Must stay short (1-2 s) so users outside the LAN are not kept waiting.
     */
    val localTimeoutMs: Long = 2_000,
    /** Maximum time to wait for the embedded node to reach Running. */
    val tailscaleConnectTimeoutMs: Long = 45_000,
    /** Maximum time for a relay probe made over the tsnet path. */
    val tailscaleProbeTimeoutMs: Long = 10_000,
    /** Maximum time for a relay data request (publish/list). */
    val requestTimeoutMs: Long = 10_000,
)