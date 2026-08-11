package com.homelab.poc.core.frigate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FrigateVersionProbeTest {

    @Test
    fun `parses version from a standard Frigate JSON response`() {
        assertEquals(
            "0.15.1",
            FrigateVersionProbe.parseVersion("""{"version":"0.15.1"}"""),
        )
    }

    @Test
    fun `uses the raw body when the response is plain text`() {
        assertEquals(
            "0.17.1-416a9b7",
            FrigateVersionProbe.parseVersion("0.17.1-416a9b7"),
        )
    }

    @Test
    fun `reports unknown when the body is empty`() {
        assertEquals("unknown", FrigateVersionProbe.parseVersion(""))
    }

    @Test
    fun `local transport maps a getter failure to a failure result`() = runTest {
        val failingGetter = HttpGetter { _, _ -> throw IOException("connection refused") }
        val transport = LocalTransport(
            config = FrigateConfig(localBaseUrl = "http://frigate:5000", tailscaleBaseUrl = "http://frigate:5000"),
            probe = FrigateVersionProbe(failingGetter),
        )

        val result = transport.probe()

        assertTrue(result is FrigateTransportResult.Failure)
    }
}
