package com.homelab.poc.setup

import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFlowTest {

    private fun connected(version: String = "0.17.1"): FrigateConnection =
        FrigateConnection.Connected(TransportKind.LOCAL, version)

    @Test
    fun `first run - setup is needed`() {
        assertTrue(shouldShowSetup(setupComplete = false))
    }

    @Test
    fun `setup complete - the app goes straight to Home`() {
        assertFalse(shouldShowSetup(setupComplete = true))
    }

    @Test
    fun `starts at EnterConfig`() {
        val flow = SetupFlow { connected() }
        assertEquals(SetupFlow.State.EnterConfig, flow.state.value)
        assertFalse("nothing tested yet must not allow finishing", flow.canComplete())
    }

    @Test
    fun `successful test moves to Connected and allows finishing`() = runTest {
        val flow = SetupFlow { connected() }
        flow.test("http://site.omni.corp")

        assertEquals(SetupFlow.State.Connected("0.17.1"), flow.state.value)
        assertEquals("http://site.omni.corp", flow.lastTestedUrl)
        assertTrue("a successful probe must unlock finishing", flow.canComplete())
    }

    @Test
    fun `the typed URL is forwarded to the probe`() = runTest {
        var probed: String? = null
        val flow = SetupFlow { url ->
            probed = url
            connected()
        }

        flow.test("http://site.omni.corp")

        assertEquals("http://site.omni.corp", probed)
    }

    @Test
    fun `enrollment requirement moves to EnrollmentRequired with the auth URL`() = runTest {
        val flow = SetupFlow {
            FrigateConnection.Failed(
                error = "Tailscale requires authentication",
                authUrl = "https://login.tailscale.com/a/abc123",
                authRequired = true,
            )
        }

        flow.test("http://site.omni.corp")

        assertEquals(
            SetupFlow.State.EnrollmentRequired("https://login.tailscale.com/a/abc123"),
            flow.state.value,
        )
        assertFalse("an open enrollment must not allow finishing", flow.canComplete())
    }

    @Test
    fun `after enrollment a retry can finish the setup`() = runTest {
        var calls = 0
        val flow = SetupFlow {
            calls++
            if (calls == 1) {
                FrigateConnection.Failed(
                    error = "Tailscale requires authentication",
                    authUrl = "https://login.tailscale.com/a/abc123",
                    authRequired = true,
                )
            } else {
                FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1")
            }
        }

        flow.test("http://site.omni.corp")
        assertTrue(flow.state.value is SetupFlow.State.EnrollmentRequired)

        flow.test("http://site.omni.corp")
        assertEquals(SetupFlow.State.Connected("0.17.1"), flow.state.value)
        assertTrue("the setup may finish once enrollment succeeded", flow.canComplete())
    }

    @Test
    fun `failed test keeps the setup open`() = runTest {
        val flow = SetupFlow { FrigateConnection.Failed("connection refused") }

        flow.test("http://site.omni.corp")

        assertEquals(SetupFlow.State.Failed("connection refused"), flow.state.value)
        assertFalse("a failed probe must keep the setup open", flow.canComplete())
    }

    @Test
    fun `a probe exception is surfaced as a Failed state`() = runTest {
        val flow = SetupFlow { error("boom") }

        flow.test("http://site.omni.corp")

        assertEquals(SetupFlow.State.Failed("boom"), flow.state.value)
    }

    @Test
    fun `a test while already testing is ignored`() = runTest {
        val gate = CompletableDeferred<FrigateConnection>()
        val flow = SetupFlow { gate.await() }

        val first = launch { flow.test("http://first.omni.corp") }
        runCurrent()
        assertEquals(SetupFlow.State.Testing, flow.state.value)

        flow.test("http://second.omni.corp")

        assertEquals("the in-flight test must not be superseded", "http://first.omni.corp", flow.lastTestedUrl)
        gate.complete(connected())
        first.join()
        assertEquals(SetupFlow.State.Connected("0.17.1"), flow.state.value)
    }

    @Test
    fun `editing the URL after a result resets to EnterConfig`() = runTest {
        val flow = SetupFlow { connected() }
        flow.test("http://site.omni.corp")
        assertEquals(SetupFlow.State.Connected("0.17.1"), flow.state.value)

        flow.reset()

        assertEquals(SetupFlow.State.EnterConfig, flow.state.value)
        assertEquals(null, flow.lastTestedUrl)
        assertFalse("a reset must invalidate the previous result", flow.canComplete())
    }
}
