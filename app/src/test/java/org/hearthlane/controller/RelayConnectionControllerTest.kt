package org.hearthlane.controller

import org.hearthlane.core.relay.RelayConnection
import org.hearthlane.core.relay.RelayTransportKind
import org.hearthlane.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for [RelayConnectionController]: the probe path resolves LOCAL first,
 * then Tailscale, exposing auth-required once; failures land in a controlled
 * [RelayConnection.Failed] with the diagnostics message but never the auth URL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayConnectionControllerTest {

    private fun dataStoreFile(): File =
        File.createTempFile("relay_settings_test", ".preferences_pb").apply { deleteOnExit() }

    private suspend fun TestScope.createSettings(): AppSettings {
        val file = dataStoreFile()
        return AppSettings.createForTest(
            dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = backgroundScope,
            ) { file },
            defaultBaseDomain = "hearthlane.example",
            scope = backgroundScope,
        ).also { it.ready.first { ready -> ready } }
    }

    private suspend fun TestScope.createController(
        connector: suspend (baseUrl: String) -> RelayConnection,
    ): RelayConnectionController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RelayConnectionController(
            gateway = org.hearthlane.test.FakeTsnetGateway(),
            settings = createSettings(),
            scope = this,
            connector = connector,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `LOCAL then remote resolves to TAILSCALE`() = runTest {
        val results = mutableListOf<suspend () -> RelayConnection>(
            { RelayConnection.Connected(RelayTransportKind.LOCAL) },
            { RelayConnection.Connected(RelayTransportKind.TAILSCALE) },
        )
        var callIndex = 0
        val controller = createController { _ -> results[callIndex++]() }

        controller.probe()
        advanceUntilIdle()
        assertEquals(
            RelayConnection.Connected(RelayTransportKind.LOCAL),
            controller.connection.value,
        )
        assertEquals(RelayTransportKind.LOCAL, controller.lastProbedTransport.value)

        controller.probe()
        advanceUntilIdle()
        assertEquals(
            RelayConnection.Connected(RelayTransportKind.TAILSCALE),
            controller.connection.value,
        )
        assertEquals(RelayTransportKind.TAILSCALE, controller.lastProbedTransport.value)
    }

    @Test
    fun `failure exposes credentials-required once and does not self-retry`() = runTest {
        var calls = 0
        val controller = createController { _ ->
            calls++
            RelayConnection.Failed(
                error = "Tailscale requires authentication",
                authUrl = "https://login.tailscale.com/a/abc123",
                authRequired = true,
            )
        }

        controller.probe()
        advanceUntilIdle()
        assertEquals(1, calls)
        val connection = controller.connection.value
        assertTrue(connection is RelayConnection.Failed)
        assertTrue((connection as RelayConnection.Failed).authRequired)

        controller.probe()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertTrue((controller.connection.value as RelayConnection.Failed).authRequired)
    }

    @Test
    fun `lastError records the failure message but not the authUrl`() = runTest {
        val controller = createController { _ ->
            RelayConnection.Failed(
                error = "Tailscale requires authentication",
                authUrl = "https://login.tailscale.com/a/abc123",
                authRequired = true,
            )
        }

        controller.probe()
        advanceUntilIdle()

        val lastError = controller.lastError.value
        assertNotNull(lastError)
        assertFalseValue(lastError!!.contains("login.tailscale.com"))
    }

    @Test
    fun `client is null before a probe and bound after a win`() = runTest {
        val results = mutableListOf<suspend () -> RelayConnection>(
            { RelayConnection.Connected(RelayTransportKind.TAILSCALE) },
        )
        var callIndex = 0
        val controller = createController { _ -> results[callIndex++]() }

        // Relay was never reached: the map has no client.
        assertNull(controller.client())

        controller.probe()
        advanceUntilIdle()

        // Winning transport binds a client the map can query with.
        assertNotNull(controller.client())
        assertTrue(controller.client() is org.hearthlane.core.relay.RelayClient)
    }

    @Test
    fun `resetTailscale clears the node identity and the shared session state`() = runTest {
        val gateway = org.hearthlane.test.FakeTsnetGateway()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = RelayConnectionController(
            gateway = gateway,
            settings = createSettings(),
            scope = this,
            connector = { _ -> RelayConnection.Connected(RelayTransportKind.TAILSCALE) },
            ioDispatcher = dispatcher,
        )

        controller.probe()
        advanceUntilIdle()
        assertEquals(
            RelayConnection.Connected(RelayTransportKind.TAILSCALE),
            controller.connection.value,
        )

        controller.resetTailscale()
        advanceUntilIdle()

        assertEquals("the administrator reset must clear the node identity", 1, gateway.resetCount)
        assertNull("the shared connection state must be cleared after a reset", controller.connection.value)
        assertNull("the probed transport must be cleared after a reset", controller.lastProbedTransport.value)
        assertNull("the recorded error must be cleared after a reset", controller.lastError.value)
    }

    private fun assertFalseValue(value: Boolean) {
        org.junit.Assert.assertFalse(value)
    }
}