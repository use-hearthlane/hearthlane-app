package org.hearthlane.test

import org.hearthlane.core.connectivity.HttpBytesGetter
import org.hearthlane.core.connectivity.HttpBytesResult

/**
 * Test double for [HttpBytesGetter]. Returns the configured response for every
 * request and records the requested URLs for assertions.
 */
class FakeHttpBytesGetter(
    private val result: HttpBytesResult = HttpBytesResult(
        statusCode = 200,
        contentType = "image/jpeg",
        finalUrl = "",
        body = byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
    ),
) : HttpBytesGetter {

    val requestedUrls = mutableListOf<String>()

    override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult {
        requestedUrls.add(url)
        return result.copy(finalUrl = url)
    }
}
