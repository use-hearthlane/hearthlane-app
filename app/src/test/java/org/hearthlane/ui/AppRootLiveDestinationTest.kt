package org.hearthlane.ui

import org.hearthlane.core.frigate.Camera
import org.hearthlane.core.frigate.FrigateConnection
import org.hearthlane.core.frigate.TransportKind
import org.hearthlane.navigation.AppNavigation
import org.hearthlane.navigation.Screen
import org.hearthlane.navigation.SetupRouteReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure decisions extracted from [AppRoot]: [liveDestination]
 * (the Screen.Live branch) and [shouldRouteToSetupForEnrollment] (the global
 * enrollment routing). They enforce the invariant that Screen.Setup is
 * exclusively the onboarding gate: after setupComplete is true, Tailscale
 * reauthentication (authRequired) must never auto-navigate to Setup.
 */
class AppRootLiveDestinationTest {

    private val camera = Camera(
        id = "backyard",
        displayName = "Quintal",
        enabled = true,
        playable = true,
    )

    private fun authRequired() = FrigateConnection.Failed(
        error = "Tailscale requires authentication",
        authUrl = "https://login.tailscale.com/a/abc123",
        authRequired = true,
    )

    @Test
    fun `auth required before onboarding waits for the enrollment route`() {
        val destination = liveDestination(
            connection = authRequired(),
            camera = camera,
            setupComplete = false,
        )

        assertEquals(
            "before onboarding completes, enrollment is part of Setup",
            LiveDestination.WaitForEnrollment,
            destination,
        )
    }

    @Test
    fun `auth required after onboarding goes back to Home`() {
        val destination = liveDestination(
            connection = authRequired(),
            camera = camera,
            setupComplete = true,
        )

        assertEquals(
            "a completed onboarding never waits for an enrollment route",
            LiveDestination.GoBack,
            destination,
        )
    }

    @Test
    fun `auth required after onboarding with null camera still goes back`() {
        val destination = liveDestination(
            connection = authRequired(),
            camera = null,
            setupComplete = true,
        )

        assertEquals(LiveDestination.GoBack, destination)
    }

    @Test
    fun `plain failure goes back to Home`() {
        val destination = liveDestination(
            connection = FrigateConnection.Failed("connection refused"),
            camera = camera,
            setupComplete = true,
        )

        assertEquals(
            "A non-auth failure must fall back to Home",
            LiveDestination.GoBack,
            destination,
        )
    }

    @Test
    fun `connected with camera renders Live`() {
        val destination = liveDestination(
            connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            camera = camera,
            setupComplete = true,
        )

        assertEquals(LiveDestination.RenderLive, destination)
    }

    @Test
    fun `connected without camera goes back`() {
        val destination = liveDestination(
            connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            camera = null,
            setupComplete = true,
        )

        assertEquals(
            "A missing camera must fall back to Home even when connected",
            LiveDestination.GoBack,
            destination,
        )
    }

    @Test
    fun `null connection goes back`() {
        val destination = liveDestination(
            connection = null,
            camera = camera,
            setupComplete = true,
        )

        assertEquals(LiveDestination.GoBack, destination)
    }

    @Test
    fun `routing to setup happens only while onboarding is pending`() {
        assertTrue(
            "pending onboarding may route to Setup for enrollment",
            shouldRouteToSetupForEnrollment(setupComplete = false, authRequired = true),
        )
    }

    @Test
    fun `completed onboarding never routes to setup for auth`() {
        assertFalse(
            "authRequired must never reopen Setup after onboarding",
            shouldRouteToSetupForEnrollment(setupComplete = true, authRequired = true),
        )
    }

    @Test
    fun `no auth never routes to setup`() {
        assertFalse(shouldRouteToSetupForEnrollment(setupComplete = false, authRequired = false))
        assertFalse(shouldRouteToSetupForEnrollment(setupComplete = true, authRequired = false))
    }

    @Test
    fun `completed onboarding refuses to render Setup for automatic reasons`() {
        assertFalse(
            "authRequired must never render Setup after onboarding",
            shouldRenderSetupScreen(setupComplete = true, reason = SetupRouteReasons.AUTH_REQUIRED),
        )
        assertFalse(shouldRenderSetupScreen(setupComplete = true, reason = SetupRouteReasons.UNKNOWN))
        assertFalse(shouldRenderSetupScreen(setupComplete = true, reason = null))
    }

    @Test
    fun `completed onboarding still renders Setup for explicit user actions`() {
        assertTrue(
            "Settings -> Remote access reconfigure is the legitimate post-onboarding entry",
            shouldRenderSetupScreen(setupComplete = true, reason = SetupRouteReasons.REMOTE_RECONFIGURE_USER_ACTION),
        )
        assertTrue(
            "Settings -> server settings is a legitimate explicit entry",
            shouldRenderSetupScreen(setupComplete = true, reason = SetupRouteReasons.USER_SERVER_SETTINGS),
        )
    }

    @Test
    fun `pending onboarding renders Setup regardless of reason`() {
        assertTrue(shouldRenderSetupScreen(setupComplete = false, reason = SetupRouteReasons.AUTH_REQUIRED))
        assertTrue(shouldRenderSetupScreen(setupComplete = false, reason = null))
    }

    @Test
    fun `navigation records the reason for Screen Setup`() {
        val navigation = AppNavigation()

        navigation.navigateTo(Screen.Setup, reason = SetupRouteReasons.AUTH_REQUIRED)

        assertEquals(Screen.Setup, navigation.current)
        assertEquals(SetupRouteReasons.AUTH_REQUIRED, navigation.setupReason)
    }

    @Test
    fun `reset clears the recorded Setup reason`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Setup, reason = SetupRouteReasons.AUTH_REQUIRED)

        navigation.resetTo(Screen.Home)

        assertEquals(null, navigation.setupReason)
        assertEquals(Screen.Home, navigation.current)
    }
}