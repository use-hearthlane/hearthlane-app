package com.homelab.poc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
import com.homelab.poc.core.frigate.TsnetGateway
import com.homelab.poc.core.frigate.TransportKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Robolectric/Compose tests for the V1.4 [LiveView] contract.
 *
 * - The selected camera resolves its own go2rtc stream (src=<cameraId>),
 *   never a "first stream".
 * - A camera without a go2rtc stream surfaces "Camera unavailable" instead of
 *   a dead player.
 * - Leaving the live view releases the player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val streamsJson = """{"backyard":{},"hall":{},"front_door":{}}"""
    private val masterPlaylist =
        """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        http://fake.local/variant.m3u8
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

    @Test
    fun `the selected camera resolves its own stream, not a first stream`() {
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
            "Playback must target the selected camera's stream (src=backyard), never the first stream",
            gateway.requestedUrls.any { it.contains("stream.m3u8") && it.contains("src=backyard") },
        )
        assertTrue(
            "No request may target any other stream",
            gateway.requestedUrls.none { it.contains("src=hall") || it.contains("src=front_door") },
        )
    }

    @Test
    fun `a camera without a go2rtc stream shows the unavailable state`() {
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

        composeTestRule
            .onNodeWithText("Camera unavailable")
            .assertExists()
    }

    @Test
    fun `leaving the live view releases the player`() {
        val gateway = RoutingTsnetGateway()
        var show by mutableStateOf(true)

        composeTestRule.setContent {
            if (show) {
                LiveView(
                    cameraId = "backyard",
                    baseUrl = "http://frigate:5000",
                    gateway = gateway,
                    transport = TransportKind.TAILSCALE,
                    connectAttempt = 1,
                    networkTick = 0,
                )
            }
        }
        composeTestRule.waitForIdle()

        show = false
        composeTestRule.waitForIdle()

        val released = ShadowLog.getLogs().any {
            it.tag == "PocCamera" && it.msg.contains("player released")
        }
        assertTrue(
            "Leaving the live view (back navigation) must release the player",
            released,
        )
    }

    @Test
    fun `family-facing live view does not contain transport label or metrics text`() {
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

        composeTestRule.onNodeWithText("Transport: LOCAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("Transport: TAILSCALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("LOCAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("TAILSCALE").assertDoesNotExist()
        // Metrics diagnostics line must not appear
        composeTestRule.onNodeWithText("Diagnostics:").assertDoesNotExist()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recovery exhausted state shows connection lost and try again`() {
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

        // v2 createComposeRule uses StandardTestDispatcher for Dispatchers.Main,
        // so mainClock shares the same scheduler as rememberCoroutineScope().
        // Advance past 3 failures (MAX_AUTO_RECOVERY=2 requires attempt 3 to exhaust).
        // Each retry delay is DISCOVERY_RETRY_DELAY_MS = 1500ms.
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Connection lost").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

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

    private inner class NoStreamGateway : TsnetGateway {
        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun reset() = Unit

        override suspend fun httpGet(url: String, timeoutMs: Long): String =
            """{"hall":{},"front_door":{}}"""

        override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
            HttpBytesResult(
                200,
                "application/json",
                url,
                """{"hall":{},"front_door":{}}""".toByteArray(),
            )
    }

    /**
     * HttpBytesGetter that always fails, so the LOCAL recovery budget is
     * exhausted quickly and the "Connection lost" state is surfaced.
     */
    private class FailingHttpBytesGetter : HttpBytesGetter {
        override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult =
            throw java.io.IOException("connection refused")
    }
}
