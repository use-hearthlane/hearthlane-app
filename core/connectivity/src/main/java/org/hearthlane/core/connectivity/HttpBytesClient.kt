package org.hearthlane.core.connectivity

/**
 * Generic HTTP request primitive returning the full response body plus status
 * and metadata.
 *
 * Implementations never fall back to another network path: the tsnet
 * implementation dials exclusively through the embedded tunnel, while the
 * local implementation uses the normal Android network. The relay location
 * publish path is the current consumer of this interface.
 */
fun interface HttpBytesClient {
    /**
     * Performs an HTTP request and returns the response. Throws on transport
     * failures; a response with a non-2xx status code is returned, not thrown.
     * A null [body] sends no request body (GET); a null [contentType] sends no
     * Content-Type header.
     *
     * @param timeoutMs total budget for establishing the connection and
     *   reading the response.
     */
    suspend fun request(
        method: String,
        url: String,
        contentType: String?,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): HttpBytesResult
}