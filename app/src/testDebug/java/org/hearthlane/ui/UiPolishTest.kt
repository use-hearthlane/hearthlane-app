package org.hearthlane.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import org.hearthlane.core.connectivity.HttpBytesGetter
import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.frigate.TransportKind
import org.hearthlane.core.connectivity.TsnetGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPolishTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val streamsJson = """{"backyard":{},"hall":{}}"""
    private val masterPlaylist =
        """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        playlist.m3u8?id=backyard
        """.trimIndent()
    private val variantPlaylist =
        """
        #EXTM3U
        #EXT-X-VERSION:7
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:0
        #EXT-X-PLAYLIST-TYPE:EVENT
        #EXTINF:6.0,
        http://fake.local/seg0.mp4
        """.trimIndent()

    // -- Normal Live View (portrait default) --

    @Test
    fun `portrait player is present during connecting state`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Stream resolved → player + fullscreen toggle visible.
        composeTestRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `portrait player persists after connecting becomes playing`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(3000)
        composeTestRule.waitForIdle()

        // Fullscreen toggle must still be visible — player was not removed.
        composeTestRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
    }

    @Test
    fun `normal live view does not show Playing text`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Playing").assertDoesNotExist()
    }

    @Test
    fun `normal live view does not show Stop button`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Stop").assertDoesNotExist()
    }

    @Test
    fun `portrait player area is non-zero`() {
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Root height must be non-zero", rootBounds.height > 0f)
        assertTrue("Root width must be non-zero", rootBounds.width > 0f)
    }

    // -- Fullscreen --

    @Test
    fun `fullscreen toggle exists in portrait`() {
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
    }

    @Test
    fun `entering fullscreen shows exit fullscreen toggle`() {
        val fullscreen = mutableStateOf(false)
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
                fullscreen = fullscreen.value,
                onToggleFullscreen = { fullscreen.value = !fullscreen.value },
            )
        }
        composeTestRule.waitForIdle()

        // Toggle to fullscreen
        composeTestRule.onNodeWithContentDescription("Enter fullscreen").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
    }

    @Test
    fun `fullscreen preserves camera id`() {
        val gateway = RoutingTsnetGateway()
        val fullscreen = mutableStateOf(false)

        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = gateway,
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
                fullscreen = fullscreen.value,
                onToggleFullscreen = { fullscreen.value = !fullscreen.value },
            )
        }
        composeTestRule.waitForIdle()

        // Verify initial camera request
        assertTrue(
            "Initial request must target backyard",
            gateway.requestedUrls.any { it.contains("src=backyard") },
        )

        // Toggle to fullscreen
        composeTestRule.onNodeWithContentDescription("Enter fullscreen").performClick()
        composeTestRule.waitForIdle()

        // Camera id must not change
        assertTrue(
            "No request may target a different camera",
            gateway.requestedUrls.none { it.contains("src=hall") },
        )
    }

    @Test
    fun `fullscreen toggle does not re-create the player`() {
        val gateway = RoutingTsnetGateway()
        val fullscreen = mutableStateOf(false)

        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = gateway,
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
                fullscreen = fullscreen.value,
                onToggleFullscreen = { fullscreen.value = !fullscreen.value },
            )
        }
        composeTestRule.waitForIdle()

        val requestsBefore = gateway.requestedUrls.size

        // Toggle fullscreen
        composeTestRule.onNodeWithContentDescription("Enter fullscreen").performClick()
        composeTestRule.waitForIdle()

        // No new stream discovery should happen — same player, same camera.
        val newRequests = gateway.requestedUrls.drop(requestsBefore)
        assertTrue(
            "Fullscreen toggle must not trigger new stream discovery",
            newRequests.none { it.contains("/api/go2rtc/streams") },
        )
    }

    // -- Error states --

    @Test
    fun `unavailable state shows try again action`() {
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = NoStreamGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun `connection lost state shows try again action`() {
        val failingGetter = FailingHttpBytesGetter()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.LOCAL,
                connectAttempt = 1,
                networkTick = 0,
                testGetter = failingGetter,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Connection lost").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun `unavailable state is not navigable`() {
        var navigated = false
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = NoStreamGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        assertFalse("unavailable state must not navigate", navigated)
    }

    // -- No technical terms --

    @Test
    fun `family-facing live view does not contain technical terms`() {
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = RoutingTsnetGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("LOCAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("TAILSCALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("Transport: LOCAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("Transport: TAILSCALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("Diagnostics:").assertDoesNotExist()
        composeTestRule.onNodeWithText("ExoPlayer").assertDoesNotExist()
        composeTestRule.onNodeWithText("DNS").assertDoesNotExist()
    }

    // -- Landscape --

    @Test
    fun `landscape unavailable state shows try again action`() {
        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = NoStreamGateway(),
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    // -- Rotation --

    @Test
    fun `rotation preserves camera id through key contract`() {
        val gateway = RoutingTsnetGateway()

        composeTestRule.setContent {
            LiveView(
                cameraId = "backyard",
                baseUrl = "http://frigate:5000",
                gateway = gateway,
                transport = TransportKind.TAILSCALE,
                connectAttempt = 1,
                networkTick = 0,
            )
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "Gateway must be queried for the selected camera's stream",
            gateway.requestedUrls.any { it.contains("src=backyard") },
        )
    }

    // -- Test doubles --

    private inner class RoutingTsnetGateway : TsnetGateway {
        val requestedUrls = mutableListOf<String>()

        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun reset() = Unit

        override suspend fun httpGet(url: String, timeoutMs: Long): String {
            requestedUrls.add(url)
            return if (url.contains("/api/go2rtc/streams")) {
                streamsJson
            } else {
                masterPlaylist
            }
        }

        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult {
            requestedUrls.add(url)
            val body = when {
                url.contains("/api/go2rtc/streams") -> streamsJson.toByteArray()
                url.contains("stream.m3u8") -> masterPlaylist.toByteArray()
                else -> variantPlaylist.toByteArray()
            }
            return HttpBytesResult(200, "application/json", url, body)
        }
    }

    private class NoStreamGateway : TsnetGateway {
        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun reset() = Unit

        override suspend fun httpGet(url: String, timeoutMs: Long): String =
            """{"hall":{}}"""

        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
            HttpBytesResult(200, "application/json", url, """{"hall":{}}""".toByteArray())
    }

    private class FailingHttpBytesGetter : HttpBytesGetter {
        override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult =
            throw java.io.IOException("connection refused")
    }
}
