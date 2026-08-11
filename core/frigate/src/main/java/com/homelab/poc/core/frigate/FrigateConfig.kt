package com.homelab.poc.core.frigate

/**
 * Static configuration for the transparent Frigate connection strategy.
 *
 * Both base URLs point at the same Frigate instance; each transport reaches it
 * through a different network path:
 *
 * - [localBaseUrl] is probed over the normal Android network (home LAN).
 * - [tailscaleBaseUrl] is probed exclusively through the embedded tsnet
 *   network and must resolve on the tailnet (MagicDNS name or 100.x address,
 *   or a LAN IP reachable through an advertised subnet route).
 *
 * No auto-discovery is implemented; these values are development configuration
 * supplied at build time.
 */
data class FrigateConfig(
    val localBaseUrl: String,
    val tailscaleBaseUrl: String,
    /**
     * Maximum time for the local probe before falling back to Tailscale.
     * Must stay short (1-2 s) so users outside the LAN are not kept waiting.
     */
    val localTimeoutMs: Long = 2_000,
    /** Maximum time to wait for the embedded node to reach Running. */
    val tailscaleConnectTimeoutMs: Long = 45_000,
    /** Maximum time for a Frigate probe made over the tsnet path. */
    val tailscaleProbeTimeoutMs: Long = 10_000,
)
