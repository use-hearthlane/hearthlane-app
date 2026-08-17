package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesResult
import kotlinx.coroutines.CancellationException
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
    fun `clean LAN launch - local success keeps Tailscale stopped`() = runTest {
        val gateway = StubGateway()
        val manager = managerWith(
            local = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Success("0.15.1")),
            gateway = gateway,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.15.1"),
            manager.connect(),
        )
        assertEquals("Tailscale must never start on a local success", 0, gateway.startCalls)
        assertEquals("nothing was running, so nothing must be stopped", 0, gateway.stopCalls)
    }

    @Test
    fun `remote - local failure starts Tailscale and returns CONNECTED_TAILSCALE`() = runTest {
        val gateway = StubGateway()
        val manager = managerWith(
            local = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Failure("network unreachable")),
            gateway = gateway,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.15.2"),
            manager.connect(),
        )
        assertEquals("Tailscale must start exactly once for the fallback", 1, gateway.startCalls)
        assertEquals("a remote success keeps the node running", 0, gateway.stopCalls)
    }

    @Test
    fun `remote to LAN transition - running Tailscale is stopped after local success`() = runTest {
        val gateway = StubGateway(running = true)
        val manager = managerWith(
            local = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Success("0.15.1")),
            gateway = gateway,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.15.1"),
            manager.connect(),
        )
        assertEquals(
            "a running node left by a previous remote attempt must be stopped after LOCAL is confirmed",
            1,
            gateway.stopCalls,
        )
        assertEquals("no new start on the local-success path", 0, gateway.startCalls)
        assertTrue("the node must be stopped", !gateway.running)
    }

    @Test
    fun `local failure - Tailscale is never stopped before the fallback is decided`() = runTest {
        val gateway = StubGateway(running = true)
        val manager = managerWith(
            local = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Failure("unreachable")),
            gateway = gateway,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.15.2"),
            manager.connect(),
        )
        assertEquals("stop must not run while the fallback is being decided", 0, gateway.stopCalls)
        assertEquals("already running node must not be restarted", 0, gateway.startCalls)
        assertTrue("the node must remain running for the fallback", gateway.running)
    }

    @Test
    fun `local timeout starts Tailscale`() = runTest {
        val gateway = StubGateway()
        val manager = managerWith(
            local = HangingTransport(TransportKind.LOCAL),
            gateway = gateway,
        )

        assertEquals(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.15.2"),
            manager.connect(),
        )
        assertEquals("Tailscale must be probed after a local timeout", 1, gateway.startCalls)
    }

    @Test
    fun `both paths fail returns FAILED and forwards the enrollment url`() = runTest {
        val gateway = StubGateway()
        val manager = FrigateConnectionManager(
            config = config,
            localTransport = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Failure("unreachable")),
            tailscaleTransport = StubTransport(
                TransportKind.TAILSCALE,
                FrigateTransportResult.Failure(
                    error = "Tailscale requires authentication",
                    authUrl = "https://login.tailscale.com/a/example",
                    authRequired = true,
                ),
            ),
            tailscaleGateway = gateway,
        )

        val result = manager.connect()

        assertTrue(result is FrigateConnection.Failed)
        val failed = result as FrigateConnection.Failed
        assertEquals("https://login.tailscale.com/a/example", failed.authUrl)
        assertEquals("enrollment must be flagged on the result", true, failed.authRequired)
        assertEquals("nothing must be stopped when the fallback itself failed", 0, gateway.stopCalls)
    }

    @Test
    fun `CancellationException from local probe propagates without mapping to Failure`() = runTest {
        val manager = FrigateConnectionManager(
            config = config,
            localTransport = CancellationTransport(TransportKind.LOCAL),
            tailscaleTransport = StubTransport(TransportKind.TAILSCALE, FrigateTransportResult.Success("0.15.2")),
            tailscaleGateway = StubGateway(),
        )

        var thrown = false
        try {
            manager.connect()
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue("CancellationException must propagate, not be caught as Failure", thrown)
    }

    @Test
    fun `CancellationException from tailscale probe propagates without mapping to Failure`() = runTest {
        val manager = FrigateConnectionManager(
            config = config,
            localTransport = StubTransport(TransportKind.LOCAL, FrigateTransportResult.Failure("unreachable")),
            tailscaleTransport = CancellationTransport(TransportKind.TAILSCALE),
            tailscaleGateway = StubGateway(),
        )

        var thrown = false
        try {
            manager.connect()
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue("CancellationException from tailscale probe must propagate", thrown)
    }

    /**
     * Builds a manager whose Tailscale transport drives [gateway], so start/stop
     * lifecycle calls are recorded exactly as the strategy performs them.
     */
    private fun managerWith(
        local: FrigateTransport,
        gateway: StubGateway,
    ): FrigateConnectionManager = FrigateConnectionManager(
        config = config,
        localTransport = local,
        tailscaleTransport = TailscaleTransport(gateway, config),
        tailscaleGateway = gateway,
    )

    /** Simulates the embedded node: start/stop lifecycle plus a fixed probe. */
    private class StubGateway(
        var running: Boolean = false,
    ) : TsnetGateway {
        var startCalls: Int = 0
            private set
        var stopCalls: Int = 0
            private set

        override suspend fun ensureRunning() {
            if (!running) {
                startCalls++
                running = true
            }
        }

        override suspend fun stopIfRunning() {
            if (running) {
                stopCalls++
                running = false
            }
        }

        override suspend fun reset() {
            stopIfRunning()
        }

        override suspend fun httpGet(url: String, timeoutMs: Long): String = "0.15.2"

        override suspend fun httpGetBytes(url: String, timeoutMs: Long) =
            HttpBytesResult(200, "application/json", url, "{}".toByteArray())
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

    /** Transport that always throws CancellationException on probe. */
    private class CancellationTransport(override val kind: TransportKind) : FrigateTransport {
        override suspend fun probe(): FrigateTransportResult {
            throw CancellationException("test cancellation")
        }
    }
}
