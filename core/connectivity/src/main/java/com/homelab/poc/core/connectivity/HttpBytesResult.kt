package com.homelab.poc.core.connectivity

/**
 * Result of a completed HTTP GET performed by a transport primitive.
 *
 * The status code is preserved so callers (for example a media DataSource) can
 * react to non-2xx responses: a request that reached the server is not a
 * failure just because the status is not 2xx. [finalUrl] is the URL after any
 * redirects were followed.
 */
data class HttpBytesResult(
    val statusCode: Int,
    val contentType: String?,
    val finalUrl: String,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is HttpBytesResult &&
            other.statusCode == statusCode &&
            other.contentType == contentType &&
            other.finalUrl == finalUrl &&
            other.body.contentEquals(body)

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + finalUrl.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}
