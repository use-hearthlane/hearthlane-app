package org.hearthlane.core.frigate

import org.hearthlane.core.connectivity.TsnetGateway
import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.connectivity.HttpStream
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transport selection for streaming mirrors [bytesGetterFor]: the tsnet
 * getter is used for TAILSCALE and the local getter for LOCAL, decided outside
 * any UI or playback layer.
 */
class TransportStreamGetterTest {

    @Test
    fun `LOCAL resolves to the HttpURLConnection stream getter`() {
        val getter = streamGetterFor(TransportKind.LOCAL, FakeGateway.instance)

        assertTrue(getter is HttpUrlConnectionStreamGetter)
    }

    @Test
    fun `TAILSCALE resolves to the tsnet stream getter`() {
        val getter = streamGetterFor(TransportKind.TAILSCALE, FakeGateway.instance)

        assertTrue(getter is TsnetStreamGetter)
    }

    private class FakeGateway : TsnetGateway {
        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun reset() = Unit
        override suspend fun httpGet(url: String, timeoutMs: Long): String = ""
        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
            HttpBytesResult(200, "application/json", url, ByteArray(0))

        companion object {
            val instance = FakeGateway()
        }
    }
}