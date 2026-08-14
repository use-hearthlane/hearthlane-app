package com.homelab.poc.ui

import com.homelab.poc.core.frigate.Camera
import com.homelab.poc.core.frigate.CameraDiscoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for the pure [resolveLiveCamera] resolution extracted from [AppRoot]'s
 * Screen.Live branch. V1.4 guarantees that the Live screen plays the camera
 * that was tapped on Home: the resolution must prefer the selected camera and
 * only fall back to the already-discovered camera list by exact id. No new
 * global discovery is performed by this function.
 */
class ResolveLiveCameraTest {

    private val backyard = Camera(
        id = "backyard",
        displayName = "Quintal",
        enabled = true,
        playable = true,
    )
    private val frontDoor = Camera(
        id = "front_door",
        displayName = "Porta da Frente",
        enabled = true,
        playable = true,
    )
    private val loaded = CameraDiscoveryState.Loaded(listOf(backyard, frontDoor))

    @Test
    fun `the selected camera wins over the discovery list`() {
        val resolved = resolveLiveCamera(
            cameraId = "backyard",
            selectedCamera = backyard,
            discoveryState = loaded,
        )

        assertSame(
            "The camera tapped on Home must be the one played in Live",
            backyard,
            resolved,
        )
    }

    @Test
    fun `a different selected camera is honored`() {
        val resolved = resolveLiveCamera(
            cameraId = "front_door",
            selectedCamera = frontDoor,
            discoveryState = loaded,
        )

        assertSame(frontDoor, resolved)
    }

    @Test
    fun `falls back to the discovered camera by exact id when nothing was selected`() {
        val resolved = resolveLiveCamera(
            cameraId = "front_door",
            selectedCamera = null,
            discoveryState = loaded,
        )

        assertSame(
            "After a restart the Live route resolves its camera from discovery",
            frontDoor,
            resolved,
        )
    }

    @Test
    fun `keeps the friendly display name from the discovered camera`() {
        val resolved = resolveLiveCamera(
            cameraId = "backyard",
            selectedCamera = null,
            discoveryState = loaded,
        )

        assertEquals(
            "The Live title must show the Frigate-friendly name, not the raw id",
            "Quintal",
            resolved?.displayName,
        )
    }

    @Test
    fun `returns null when the id is absent from discovery`() {
        val resolved = resolveLiveCamera(
            cameraId = "missing_camera",
            selectedCamera = null,
            discoveryState = loaded,
        )

        assertNull(
            "An unknown camera id must resolve to nothing, not to a different camera",
            resolved,
        )
    }

    @Test
    fun `returns null when discovery has not loaded`() {
        val resolved = resolveLiveCamera(
            cameraId = "backyard",
            selectedCamera = null,
            discoveryState = CameraDiscoveryState.Loading,
        )

        assertNull(resolved)
    }
}
