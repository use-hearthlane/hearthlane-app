package com.homelab.poc.controller

import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SettingsController]: the initial state, the product-facing
 * connection summary derived from the shared flows, the server address
 * propagation, the remote-access reconfigure action and the release lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsControllerTest {

    private fun TestScope.controller(
        serverUrl: MutableStateFlow<String> = MutableStateFlow("http://frigate:5000"),
        connection: MutableStateFlow<FrigateConnection?> = MutableStateFlow(null),
        connecting: MutableStateFlow<Boolean> = MutableStateFlow(false),
        autoPlayEventClips: MutableStateFlow<Boolean> = MutableStateFlow(true),
        reset: () -> Unit = {},
        setAutoPlay: suspend (Boolean) -> Unit = {},
    ) = SettingsController(
        serverUrl = serverUrl,
        connection = connection,
        connecting = connecting,
        autoPlayEventClips = autoPlayEventClips,
        appVersion = "1.2.0",
        appBuild = "3",
        resetRemoteAccessAction = reset,
        setAutoPlayEventClipsAction = setAutoPlay,
        scope = backgroundScope,
    )

    @Test
    fun `initial state reflects the injected values`() = runTest {
        val controller = controller(
            serverUrl = MutableStateFlow("http://frigate:5000"),
            connection = MutableStateFlow(FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")),
            connecting = MutableStateFlow(false),
        )
        runCurrent()

        assertEquals("http://frigate:5000", controller.state.value.serverUrl)
        assertEquals(ConnectionStatus.Connected, controller.state.value.connectionStatus)
        assertEquals("1.2.0", controller.state.value.appVersion)
        assertEquals("3", controller.state.value.appBuild)
    }

    @Test
    fun `initial state reflects the auto-play preference`() = runTest {
        val controller = controller(
            autoPlayEventClips = MutableStateFlow(true),
        )
        runCurrent()

        assertEquals(true, controller.state.value.autoPlayEventClips)
    }

    @Test
    fun `auto-play off is reflected in the state`() = runTest {
        val controller = controller(
            autoPlayEventClips = MutableStateFlow(false),
        )
        runCurrent()

        assertEquals(false, controller.state.value.autoPlayEventClips)
    }

    @Test
    fun `setAutoPlayEventClips delegates and persists`() = runTest {
        val persisted = mutableListOf<Boolean>()
        val controller = controller(
            setAutoPlay = { persisted.add(it) },
        )

        controller.setAutoPlayEventClips(false)
        controller.setAutoPlayEventClips(true)
        runCurrent()

        assertEquals(listOf(false, true), persisted)
    }

    @Test
    fun `auto-play state propagates when the persisted flow changes`() = runTest {
        val autoPlay = MutableStateFlow(true)
        val controller = controller(autoPlayEventClips = autoPlay)
        runCurrent()

        autoPlay.value = false
        runCurrent()

        assertEquals(false, controller.state.value.autoPlayEventClips)
    }

    @Test
    fun `connected connection maps to Connected`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
        )
        val controller = controller(connection = connection)
        runCurrent()

        assertEquals(ConnectionStatus.Connected, controller.state.value.connectionStatus)
    }

    @Test
    fun `connecting maps to Connecting`() = runTest {
        val controller = controller(connecting = MutableStateFlow(true))
        runCurrent()

        assertEquals(ConnectionStatus.Connecting, controller.state.value.connectionStatus)
    }

    @Test
    fun `no connection maps to Unavailable`() = runTest {
        val controller = controller()
        runCurrent()

        assertEquals(ConnectionStatus.Unavailable, controller.state.value.connectionStatus)
    }

    @Test
    fun `failed connection maps to Unavailable`() = runTest {
        val controller = controller(
            connection = MutableStateFlow(FrigateConnection.Failed("timeout")),
        )
        runCurrent()

        assertEquals(ConnectionStatus.Unavailable, controller.state.value.connectionStatus)
    }

    @Test
    fun `server url updates when the persisted flow changes`() = runTest {
        val serverUrl = MutableStateFlow("http://frigate:5000")
        val controller = controller(serverUrl = serverUrl)
        runCurrent()

        serverUrl.value = "http://frigate:5001"
        runCurrent()

        assertEquals("http://frigate:5001", controller.state.value.serverUrl)
    }

    @Test
    fun `connection status updates when the shared flows change`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val connecting = MutableStateFlow(false)
        val controller = controller(connection = connection, connecting = connecting)
        runCurrent()

        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()
        assertEquals(ConnectionStatus.Connected, controller.state.value.connectionStatus)

        connecting.value = true
        runCurrent()
        assertEquals(ConnectionStatus.Connecting, controller.state.value.connectionStatus)
    }

    @Test
    fun `resetRemoteAccess delegates to the injected action`() = runTest {
        var resetCalls = 0
        val controller = controller(reset = { resetCalls++ })

        controller.resetRemoteAccess()
        controller.resetRemoteAccess()

        assertEquals(2, resetCalls)
    }

    @Test
    fun `release stops observing the shared state`() = runTest {
        val serverUrl = MutableStateFlow("http://frigate:5000")
        val controller = controller(serverUrl = serverUrl)
        runCurrent()

        controller.release()
        serverUrl.value = "http://frigate:5001"
        runCurrent()

        assertEquals("http://frigate:5000", controller.state.value.serverUrl)
    }
}
