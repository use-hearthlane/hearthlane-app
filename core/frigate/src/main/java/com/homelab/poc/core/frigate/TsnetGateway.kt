package com.homelab.poc.core.frigate

/**
 * Application-facing gateway to the embedded Tailscale node. Kept as an
 * interface so the fallback strategy can be unit tested without a real node;
 * the production implementation lives in the `native/tailscale` module.
 */
interface TsnetGateway {

    /**
     * Starts the embedded node if needed and blocks until it reaches the
     * connected (Running) state.
     *
     * @throws TailscaleAuthRequired when enrollment is pending.
     * @throws Exception on any other startup/timeout failure.
     */
    suspend fun ensureRunning()

    /**
     * Performs an HTTP GET exclusively over the tsnet network. Must never fall
     * back to the normal Android network.
     *
     * @throws Exception on failure.
     */
    suspend fun httpGet(url: String, timeoutMs: Long): String
}

/** Raised when the embedded node needs interactive enrollment to proceed. */
class TailscaleAuthRequired(val authUrl: String?) :
    Exception("Tailscale requires authentication")
