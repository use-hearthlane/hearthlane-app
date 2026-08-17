package com.homelab.poc.controller

import com.homelab.poc.core.frigate.Camera
import com.homelab.poc.core.frigate.CameraDiscoveryState
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraDiscoveryControllerTest {

    private val camera = Camera(
        id = "backyard",
        displayName = "Quintal",
        enabled = true,
        playable = true,
    )

    @Test
    fun `starts in Loading state`() = runTest {
        val controller = controller(connection = MutableStateFlow(null), scope = backgroundScope)

        assertEquals(CameraDiscoveryState.Loading, controller.state.value)
    }

    @Test
    fun `start discovers when a Connected connection arrives`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val calls = mutableListOf<Pair<TransportKind, String>>()
        val controller = controller(connection = connection, scope = backgroundScope) { transport, url ->
            calls.add(transport to url)
            CameraDiscoveryState.Loaded(listOf(camera))
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()

        assertEquals(listOf(TransportKind.LOCAL to "http://frigate:5000"), calls)
        assertTrue(controller.state.value is CameraDiscoveryState.Loaded)
        assertEquals(
            listOf(camera),
            (controller.state.value as CameraDiscoveryState.Loaded).cameras,
        )
    }

    @Test
    fun `failed connection does not trigger discovery`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        var calls = 0
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            calls++
            CameraDiscoveryState.Empty
        }

        controller.start()
        connection.value = FrigateConnection.Failed("network unreachable")
        runCurrent()

        assertEquals(0, calls)
        assertEquals(CameraDiscoveryState.Loading, controller.state.value)
    }

    @Test
    fun `transport switch re-discovers on the new transport`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val calls = mutableListOf<TransportKind>()
        val controller = controller(connection = connection, scope = backgroundScope) { transport, _ ->
            calls.add(transport)
            CameraDiscoveryState.Loaded(listOf(camera))
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()
        connection.value = FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1")
        runCurrent()

        assertEquals(listOf(TransportKind.LOCAL, TransportKind.TAILSCALE), calls)
        assertEquals(
            CameraDiscoveryState.Loaded(listOf(camera)),
            controller.state.value,
        )
    }

    @Test
    fun `refresh re-discovers with the current transport`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )
        val calls = mutableListOf<TransportKind>()
        val controller = controller(connection = connection, scope = backgroundScope) { transport, _ ->
            calls.add(transport)
            CameraDiscoveryState.Loaded(listOf(camera))
        }

        controller.start()
        runCurrent()
        controller.refresh()
        runCurrent()

        assertEquals(listOf(TransportKind.LOCAL, TransportKind.LOCAL), calls)
    }

    @Test
    fun `refresh bumps the refresh key for thumbnail cache busting`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )
        val controller = controller(connection = connection, scope = backgroundScope)

        assertEquals(0, controller.refreshKey.value)

        controller.start()
        runCurrent()
        controller.refresh()
        runCurrent()

        assertEquals(1, controller.refreshKey.value)
    }

    @Test
    fun `refresh does nothing when there is no current connection`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        var calls = 0
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            calls++
            CameraDiscoveryState.Empty
        }

        controller.refresh()
        runCurrent()

        assertEquals(0, calls)
    }

    @Test
    fun `discoverer failure maps to Error state`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            CameraDiscoveryState.Error("boom")
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()

        assertTrue(controller.state.value is CameraDiscoveryState.Error)
        assertEquals("boom", (controller.state.value as CameraDiscoveryState.Error).message)
    }

    @Test
    fun `Loading is emitted while a discoverer is slow`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            gate.await()
            CameraDiscoveryState.Loaded(listOf(camera))
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()

        assertEquals(CameraDiscoveryState.Loading, controller.state.value)
        gate.complete(Unit)
        runCurrent()

        assertEquals(CameraDiscoveryState.Loaded(listOf(camera)), controller.state.value)
    }

    @Test
    fun `Empty state is surfaced when no cameras exist`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            CameraDiscoveryState.Empty
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()

        assertEquals(CameraDiscoveryState.Empty, controller.state.value)
    }

    @Test
    fun `a streams warning inside Loaded is surfaced unchanged`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            CameraDiscoveryState.Loaded(listOf(camera), streamsWarning = "go2rtc unreachable")
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()

        val loaded = controller.state.value as CameraDiscoveryState.Loaded
        assertEquals(listOf(camera), loaded.cameras)
        assertEquals("go2rtc unreachable", loaded.streamsWarning)
    }

    @Test
    fun `stop cancels an in-flight discovery`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var completed = false
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            gate.await()
            completed = true
            CameraDiscoveryState.Empty
        }

        controller.start()
        runCurrent() // collector runs and discovery suspends on the gate
        assertEquals(CameraDiscoveryState.Loading, controller.state.value)

        controller.stop()
        gate.complete(Unit)
        runCurrent()

        assertFalse("stop must cancel the in-flight discovery", completed)
        assertEquals(CameraDiscoveryState.Loading, controller.state.value)
    }

    @Test
    fun `stop cancels the connection observer and stops reacting to emissions`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        var discoveryCount = 0
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            discoveryCount++
            CameraDiscoveryState.Loaded(listOf(camera))
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()
        assertEquals(1, discoveryCount)

        controller.stop()
        // Emit another connection after stop: the controller must not react.
        connection.value = FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1")
        runCurrent()
        assertEquals("stop must cancel the observer so no further discoveries run", 1, discoveryCount)
    }

    @Test
    fun `start stop start does not duplicate collectors`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(null)
        var discoveryCount = 0
        val controller = controller(connection = connection, scope = backgroundScope) { _, _ ->
            discoveryCount++
            CameraDiscoveryState.Loaded(listOf(camera))
        }

        controller.start()
        connection.value = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        runCurrent()
        assertEquals(1, discoveryCount)

        controller.stop()
        controller.start()
        connection.value = null
        connection.value = FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1")
        runCurrent()
        // Exactly one discovery for the new transport, not two (no duplicate collector).
        assertEquals("start/stop/start must not duplicate collectors", 2, discoveryCount)
    }

    private fun controller(
        connection: MutableStateFlow<FrigateConnection?>,
        scope: CoroutineScope,
        discoverer: suspend (TransportKind, String) -> CameraDiscoveryState = { _, _ ->
            CameraDiscoveryState.Empty
        },
    ): CameraDiscoveryController = CameraDiscoveryController(
        connection = connection,
        baseUrl = { "http://frigate:5000" },
        scope = scope,
        discoverer = discoverer,
    )
}
