package com.homelab.poc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.homelab.poc.controller.SettingsController
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric/Compose tests for the product-oriented [SettingsScreen]: section
 * structure, server address, connection summary, navigation entry points, the
 * reconfigure confirmation dialog, About information, the absence of
 * infrastructure labels, and both supported locales.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun TestScope.controller(
        serverUrl: String = "http://frigate:5000",
        connection: FrigateConnection? = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        connecting: Boolean = false,
        autoPlayEventClips: Boolean = true,
        reset: () -> Unit = {},
        setAutoPlay: suspend (Boolean) -> Unit = {},
    ) = SettingsController(
        serverUrl = MutableStateFlow(serverUrl),
        connection = MutableStateFlow(connection),
        connecting = MutableStateFlow(connecting),
        autoPlayEventClips = MutableStateFlow(autoPlayEventClips),
        appVersion = "1.2.0",
        appBuild = "3",
        resetRemoteAccessAction = reset,
        setAutoPlayEventClipsAction = setAutoPlay,
        scope = backgroundScope,
    )

    private fun render(
        controller: SettingsController,
        onOpenServerSettings: () -> Unit = {},
        onOpenDiagnostics: () -> Unit = {},
        onReconfigureRemoteAccess: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                controller = controller,
                onOpenServerSettings = onOpenServerSettings,
                onOpenDiagnostics = onOpenDiagnostics,
                onReconfigureRemoteAccess = onReconfigureRemoteAccess,
                onBack = {},
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `renders the product sections`() = runTest {
        val controller = controller()
        render(controller)

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remote access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Playback").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Diagnostics").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `playback section shows the autoplay switch`() = runTest {
        val controller = controller()
        render(controller)

        composeTestRule.onNodeWithText("Play event clips automatically")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun `autoplay switch reflects the persisted value when off`() = runTest {
        val controller = controller(autoPlayEventClips = false)
        render(controller)

        composeTestRule.onNodeWithText("Play event clips automatically")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun `toggling the autoplay switch persists the change`() = runTest {
        val persisted = mutableListOf<Boolean>()
        val controller = controller(setAutoPlay = { persisted.add(it) })
        render(controller)

        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        runCurrent()

        assertEquals(listOf(false), persisted)
    }

    @Test
    fun `shows the server address and the connection summary`() = runTest {
        val controller = controller(serverUrl = "http://frigate:5000")
        render(controller)

        composeTestRule.onNodeWithText("Server address").assertIsDisplayed()
        composeTestRule.onNodeWithText("http://frigate:5000").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `shows connecting summary`() = runTest {
        val controller = controller(connecting = true)
        render(controller)

        composeTestRule.onNodeWithText("Connecting…").assertIsDisplayed()
    }

    @Test
    fun `shows unavailable summary without a connection`() = runTest {
        val controller = controller(connection = null, connecting = false)
        render(controller)

        composeTestRule.onNodeWithText("Unavailable").assertIsDisplayed()
    }

    @Test
    fun `shows unavailable summary on a failed connection`() = runTest {
        val controller = controller(
            connection = FrigateConnection.Failed("timeout"),
            connecting = false,
        )
        render(controller)

        composeTestRule.onNodeWithText("Unavailable").assertIsDisplayed()
    }

    @Test
    fun `connection status transitions are reflected without reopening`() = runTest {
        val connection = MutableStateFlow<FrigateConnection?>(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )
        val connecting = MutableStateFlow(false)
        val controller = SettingsController(
            serverUrl = MutableStateFlow("http://frigate:5000"),
            connection = connection,
            connecting = connecting,
            autoPlayEventClips = MutableStateFlow(true),
            appVersion = "1.2.0",
            appBuild = "3",
            resetRemoteAccessAction = {},
            setAutoPlayEventClipsAction = {},
            scope = backgroundScope,
        )
        render(controller)

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()

        connecting.value = true
        runCurrent()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Connecting…").assertIsDisplayed()

        connecting.value = false
        connection.value = null
        runCurrent()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unavailable").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `pt-br connecting summary renders in portuguese`() = runTest {
        val controller = controller(connecting = true)
        render(controller)

        composeTestRule.onNodeWithText("Conectando…").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `pt-br unavailable summary renders in portuguese`() = runTest {
        val controller = controller(connection = null)
        render(controller)

        composeTestRule.onNodeWithText("Indisponível").assertIsDisplayed()
    }

    @Test
    fun `tapping the server address invokes onOpenServerSettings`() = runTest {
        var opened = 0
        val controller = controller()
        render(controller, onOpenServerSettings = { opened++ })

        composeTestRule.onNodeWithText("Server address").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun `tapping diagnostics invokes onOpenDiagnostics`() = runTest {
        var opened = 0
        val controller = controller()
        render(controller, onOpenDiagnostics = { opened++ })

        composeTestRule.onNodeWithText("Diagnostics").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun `reconfigure remote access asks for confirmation before running`() = runTest {
        var reconfigured = 0
        val controller = controller(reset = { reconfigured++ })
        render(controller, onReconfigureRemoteAccess = { controller.resetRemoteAccess() })

        composeTestRule.onNodeWithText("Reconfigure remote access").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reconfigure remote access?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconfigure").performClick()
        composeTestRule.waitForIdle()

        assertEquals("the reconfigure action must run after confirmation", 1, reconfigured)
    }

    @Test
    fun `cancel dismisses the reconfigure dialog without running`() = runTest {
        var reconfigured = 0
        val controller = controller()
        render(controller, onReconfigureRemoteAccess = { reconfigured++ })

        composeTestRule.onNodeWithText("Reconfigure remote access").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, reconfigured)
        composeTestRule.onNodeWithText("Reconfigure remote access?").assertDoesNotExist()
    }

    @Test
    fun `about shows the app name version and build`() = runTest {
        val controller = controller()
        render(controller)

        composeTestRule.onNodeWithText("Hearthlane").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Version 1.2.0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Build 3").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `infrastructure labels are not exposed`() = runTest {
        val controller = controller()
        render(controller)

        composeTestRule.onNodeWithText("Tailscale").assertDoesNotExist()
        composeTestRule.onNodeWithText("Frigate").assertDoesNotExist()
        composeTestRule.onNodeWithText("LOCAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("TAILSCALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("Node hostname").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `pt-br locale renders the product sections`() = runTest {
        val controller = controller()
        render(controller)

        composeTestRule.onNodeWithText("Configurações").assertIsDisplayed()
        composeTestRule.onNodeWithText("Servidor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Endereço do servidor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Acesso remoto").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconfigurar acesso remoto").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Reprodução").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Reproduzir clipes automaticamente").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Sobre").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Conectado").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `pt-br reconfigure dialog uses product language`() = runTest {
        val controller = controller()
        render(controller)

        composeTestRule.onNodeWithText("Reconfigurar acesso remoto").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reconfigurar acesso remoto?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconfigurar").assertIsDisplayed()
    }
}
