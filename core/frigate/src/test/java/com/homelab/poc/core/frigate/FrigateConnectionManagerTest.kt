package com.homelab.poc.core.frigate

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateConnectionManagerTest {

    private val config = FrigateConfig(
        localBaseUrl = "http://frigate:5000",
        tailscaleBaseUrl = "http://frigate:5000",
    )

    @Test
    fun `local success returns CONNECTED_LOCAL and never starts Tailscale`() = runTest {
        val tailscale = StubTransport(TransportKind.TAILSCALE, FrigateTransportResult.Success("0.15.1"))

        val manager = FrigateConnectionManager(
            config = config,
            localTransport = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Success("0.15.1")),
            tailscaleTransport = tailscale,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.15.1"),
            manager.connect(),
        )
        assertEquals("Tailscale transport must not be probed on local success", 0, tailscale.probeCalls)
    }

    @Test
    fun `local failure starts Tailscale and Tailscale success returns CONNECTED_TAILSCALE`() = runTest {
        val tailscale = StubTransport(TransportKind.TAILSCALE, FrigateTransportResult.Success("0.15.2"))

        val manager = FrigateConnectionManager(
            config = config,
            localTransport = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Failure("network unreachable")),
            tailscaleTransport = tailscale,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.15.2"),
            manager.connect(),
        )
        assertEquals("Tailscale transport must be probed exactly once after local failure", 1, tailscale.probeCalls)
    }

    @Test
    fun `local timeout starts Tailscale`() = runTest {
        val tailscale = StubTransport(TransportKind.TAILSCALE, FrigateTransportResult.Success("0.15.3"))

        val manager = FrigateConnectionManager(
            config = config,
            localTransport = HangingTransport(TransportKind.LOCAL),
            tailscaleTransport = tailscale,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.15.3"),
            manager.connect(),
        )
        assertEquals("Tailscale transport must be probed after a local timeout", 1, tailscale.probeCalls)
    }

    @Test
    fun `both paths fail returns FAILED and forwards the enrollment url`() = runTest {
        val manager = FrigateConnectionManager(
            config = config,
            localTransport = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Failure("unreachable")),
            tailscaleTransport = StubTransport(
                TransportKind.TAILSCALE,
                FrigateTransportResult.Failure(
                    error = "Tailscale requires authentication",
                    authUrl = "https://login.tailscale.com/a/example",
                ),
            ),
        )

        val result = manager.connect()

        assertTrue(result is FrigateConnection.Failed)
        val failed = result as FrigateConnection.Failed
        assertEquals("https://login.tailscale.com/a/example", failed.authUrl)
    }

    /** Returns a fixed result and counts how many times it was probed. */
    private class StubTransport(
        override val kind: TransportKind,
        private val result: FrigateTransportResult,
    ) : FrigateTransport {
        var probeCalls: Int = 0
            private set

        override suspend fun probe(): FrigateTransportResult {
            probeCalls++
            return result
        }
    }

    /** Simulates a local probe that never answers within the timeout. */
    private class HangingTransport(override val kind: TransportKind) : FrigateTransport {
        override suspend fun probe(): FrigateTransportResult {
            delay(Long.MAX_VALUE)
            error("HangingTransport should have been cancelled by the local timeout")
        }
    }
}
