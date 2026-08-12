package com.homelab.poc.ui

import com.homelab.poc.core.frigate.Camera
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure [liveDestination] decision extracted from [AppRoot]'s
 * Screen.Live branch. The decision prevents a navigation race when a network
 * transition falls back to Tailscale and the embedded node requires enrollment:
 * the branch must not call navigateBack(), because the global enrollment
 * LaunchedEffect is responsible for pushing Screen.Setup.
 */
class AppRootLiveDestinationTest {

    private val camera = Camera(
        id = "backyard",
        displayName = "Quintal",
        enabled = true,
        playable = true,
    )

    @Test
    fun `auth required waits for enrollment routing`() {
        val destination = liveDestination(
            connection = FrigateConnection.Failed(
                error = "Tailscale requires authentication",
                authUrl = "https://login.tailscale.com/a/abc123",
                authRequired = true,
            ),
            camera = camera,
        )

        assertEquals(
            "Live must not navigate back when enrollment is pending",
            LiveDestination.WaitForEnrollment,
            destination,
        )
    }

    @Test
    fun `auth required with null camera still waits for enrollment routing`() {
        val destination = liveDestination(
            connection = FrigateConnection.Failed(
                error = "Tailscale requires authentication",
                authUrl = "https://login.tailscale.com/a/abc123",
                authRequired = true,
            ),
            camera = null,
        )

        assertEquals(
            "Enrollment routing must take precedence over missing camera",
            LiveDestination.WaitForEnrollment,
            destination,
        )
    }

    @Test
    fun `plain failure goes back to Home`() {
        val destination = liveDestination(
            connection = FrigateConnection.Failed("connection refused"),
            camera = camera,
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
        )

        assertEquals(LiveDestination.RenderLive, destination)
    }

    @Test
    fun `connected without camera goes back`() {
        val destination = liveDestination(
            connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            camera = null,
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
        )

        assertEquals(LiveDestination.GoBack, destination)
    }
}
