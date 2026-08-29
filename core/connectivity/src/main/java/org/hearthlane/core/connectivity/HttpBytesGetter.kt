package org.hearthlane.core.connectivity

/**
 * Generic HTTP GET primitive returning the full response body plus status and
 * metadata.
 *
 * Implementations never fall back to another network path: the tsnet
 * implementation dials exclusively through the embedded tunnel, while the
 * local implementation uses the normal Android network. Playback media
 * requests are funnelled through this interface so a single DataSource can
 * serve both the Tailscale and the home-LAN path.
 */
fun interface HttpBytesGetter {
    /**
     * Performs a GET and returns the response. Throws on transport failures;
     * a response with a non-2xx status code is returned, not thrown.
     *
     * @param timeoutMs total budget for establishing the connection and
     *   reading the response.
     */
    suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult
}
