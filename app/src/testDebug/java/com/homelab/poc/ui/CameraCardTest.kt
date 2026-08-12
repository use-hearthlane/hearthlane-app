package com.homelab.poc.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.homelab.poc.core.frigate.Camera
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
class CameraCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val imageLoader = fakeSnapshotImageLoader(context)
    private val factory = CameraThumbnailModelFactory(
        connection = MutableStateFlow(null),
        gateway = FakeTsnetGateway(),
    )

    @Test
    fun `card displays the friendly name as title`() {
        composeTestRule.setContent {
            CameraCard(
                camera = Camera(
                    id = "backyard",
                    displayName = "Quintal",
                    enabled = true,
                    playable = true,
                ),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("Quintal").assertIsDisplayed()
    }

    @Test
    fun `card does not display the camera id as title when friendly name exists`() {
        composeTestRule.setContent {
            CameraCard(
                camera = Camera(
                    id = "backyard",
                    displayName = "Quintal",
                    enabled = true,
                    playable = true,
                ),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("backyard").assertDoesNotExist()
    }

    @Test
    fun `playable card is enabled and reports the camera id on click`() {
        var clickedId: String? = null
        composeTestRule.setContent {
            CameraCard(
                camera = Camera(
                    id = "hall",
                    displayName = "Hall",
                    enabled = true,
                    playable = true,
                ),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onClick = { clickedId = "hall" },
            )
        }

        composeTestRule.onNodeWithText("Hall")
            .assertIsEnabled()
            .performClick()

        assertEquals("hall", clickedId)
    }

    @Test
    fun `unplayable card is disabled and does not navigate`() {
        var clicked = false
        composeTestRule.setContent {
            CameraCard(
                camera = Camera(
                    id = "garage",
                    displayName = "Garagem",
                    enabled = true,
                    playable = false,
                ),
                refreshKey = 0,
                baseUrl = "http://frigate",
                thumbnailFactory = factory,
                snapshotImageLoader = imageLoader,
                onClick = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("Garagem").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Garagem").performClick()
        assertEquals(false, clicked)
    }

    @Test
    fun `multiple cards keep distinct ids and names`() {
        val cameras = listOf(
            Camera("backyard", "Quintal", true, true),
            Camera("hall", "Hall", true, true),
            Camera("gate", "Portão", true, false),
        )

        composeTestRule.setContent {
            cameras.forEach { camera ->
                CameraCard(
                    camera = camera,
                    refreshKey = 0,
                    baseUrl = "http://frigate",
                    thumbnailFactory = factory,
                    snapshotImageLoader = imageLoader,
                    onClick = { },
                )
            }
        }

        composeTestRule.onNodeWithText("Quintal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hall").assertIsDisplayed()
        composeTestRule.onNodeWithText("Portão").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Camera unavailable").assertCountEquals(1)
    }
}
