package org.hearthlane.test

import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.connectivity.TsnetGateway

/**
 * Test double for [TsnetGateway]. Returns the configured response for every
 * HTTP request and records the requested URLs.
 */
class FakeTsnetGateway(
    private val result: HttpBytesResult = HttpBytesResult(
        statusCode = 200,
        contentType = "application/json",
        finalUrl = "",
        body = byteArrayOf(),
    ),
) : TsnetGateway {

    val requestedUrls = mutableListOf<String>()

    /** How many times [reset] was called (administrator node reset). */
    var resetCount = 0

    override suspend fun ensureRunning() = Unit

    override suspend fun stopIfRunning() = Unit

    override suspend fun reset() {
        resetCount++
    }

    override suspend fun httpGet(url: String, timeoutMs: Long): String {
        requestedUrls.add(url)
        return result.body.toString(Charsets.UTF_8)
    }

    override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult {
        requestedUrls.add(url)
        return result.copy(finalUrl = url)
    }

    override suspend fun httpPut(
        url: String,
        contentType: String,
        body: String,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): HttpBytesResult {
        requestedUrls.add(url)
        return result.copy(finalUrl = url)
    }
}
