package com.homelab.poc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.homelab.poc.core.frigate.Camera
import com.homelab.poc.core.frigate.CameraDiscoveryState
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.test.FakeTsnetGateway
import com.homelab.poc.test.fakeSnapshotImageLoader
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraGridSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val imageLoader = fakeSnapshotImageLoader(context)
    private val factory = CameraThumbnailModelFactory(
        connection = MutableStateFlow(FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")),
        gateway = FakeTsnetGateway(),
    )

    @Test
    fun `loaded cameras render matching cards`() {
        val cameras = listOf(
            Camera("backyard", "Quintal", true, true),
            Camera("hall", "Hall", true, true),
        )

        composeTestRule.setContent {
            CameraGridSection(
                connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
                discoveryState = CameraDiscoveryState.Loaded(cameras),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onCameraSelected = {},
                onRetry = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Quintal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hall").assertIsDisplayed()
    }

    @Test
    fun `clicking a playable camera reports its id`() {
        val cameras = listOf(
            Camera("backyard", "Quintal", true, true),
            Camera("hall", "Hall", true, true),
        )
        var selectedId: String? = null

        composeTestRule.setContent {
            CameraGridSection(
                connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
                discoveryState = CameraDiscoveryState.Loaded(cameras),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onCameraSelected = { selectedId = it.id },
                onRetry = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Hall").performClick()

        assertEquals("hall", selectedId)
    }

    @Test
    fun `empty state shows appropriate message`() {
        composeTestRule.setContent {
            CameraGridSection(
                connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
                discoveryState = CameraDiscoveryState.Empty,
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onCameraSelected = {},
                onRetry = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("No cameras configured.").assertIsDisplayed()
    }

    @Test
    fun `error state shows retry action`() {
        var retried = false
        composeTestRule.setContent {
            CameraGridSection(
                connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
                discoveryState = CameraDiscoveryState.Error("boom"),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onCameraSelected = {},
                onRetry = {},
                onRefresh = { retried = true },
            )
        }

        composeTestRule.onNodeWithText("Could not load cameras.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `connection error shows product-language retry`() {
        var retried = false
        composeTestRule.setContent {
            CameraGridSection(
                connection = FrigateConnection.Failed("network unreachable"),
                discoveryState = CameraDiscoveryState.Loading,
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onCameraSelected = {},
                onRetry = { retried = true },
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Could not reach the camera server.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `loading state is shown while discovering`() {
        composeTestRule.setContent {
            CameraGridSection(
                connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
                discoveryState = CameraDiscoveryState.Loading,
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onCameraSelected = {},
                onRetry = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Loading cameras…").assertIsDisplayed()
    }
}
