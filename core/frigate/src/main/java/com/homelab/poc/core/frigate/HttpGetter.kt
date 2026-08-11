package com.homelab.poc.core.frigate

/**
 * Minimal HTTP GET abstraction so the Frigate probe logic is testable without
 * real sockets. Implementations must not fall back to another network path.
 */
fun interface HttpGetter {

    /**
     * Performs a GET and returns the response body.
     *
     * @throws Exception when the request fails or returns a non-2xx status.
     */
    suspend fun get(url: String, timeoutMs: Long): String
}
