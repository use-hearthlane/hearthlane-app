package org.hearthlane.ui

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import org.hearthlane.controller.CameraDiscoveryController
import org.hearthlane.controller.FrigateConnectionController
import org.hearthlane.core.frigate.Camera
import org.hearthlane.core.frigate.CameraDiscoveryState
import org.hearthlane.core.frigate.FrigateConnection
import org.hearthlane.core.frigate.TransportKind
import org.hearthlane.settings.AppSettings
import org.hearthlane.test.fakeSnapshotImageLoader
import org.hearthlane.test.FakeTsnetGateway
import org.hearthlane.thumbnail.CameraThumbnailModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for [HomeScreen] focused on the compact TopAppBar connection indicator.
 *
 * These tests intentionally do not exercise networking, discovery, Tailscale,
 * or playback; they verify that the family-facing Home renders only product
 * language (connected / connecting / problem) and keeps settings/refresh
 * accessible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun dataStoreFile(): File = File.createTempFile(
        "app_settings_test",
        ".preferences_pb",
    ).apply { deleteOnExit() }

    private suspend fun TestScope.createSettings(): AppSettings {
        val file = dataStoreFile()
        return AppSettings.createForTest(
            dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file },
            defaultBaseDomain = "hearthlane.example",
            scope = backgroundScope,
        ).also { it.ready.first { ready -> ready } }
    }

    private suspend fun TestScope.createController(
        connector: suspend (String) -> FrigateConnection,
    ): FrigateConnectionController {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val dispatcher = StandardTestDispatcher(testScheduler)
        return FrigateConnectionController(
            gateway = FakeTsnetGateway(),
            settings = createSettings(),
            connectivityManager = connectivityManager,
            scope = this,
            connector = connector,
            ioDispatcher = dispatcher,
        )
    }

    private fun createDiscoveryController(
        connection: FrigateConnection? = null,
    ): CameraDiscoveryController {
        val connectionFlow = kotlinx.coroutines.flow.MutableStateFlow(connection)
        return CameraDiscoveryController(
            connection = connectionFlow,
            baseUrl = { "http://frigate:5000" },
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            discoverer = { _, _ -> CameraDiscoveryState.Loaded(emptyList()) },
        )
    }

    private fun factory(controller: FrigateConnectionController): CameraThumbnailModelFactory {
        return CameraThumbnailModelFactory(
            connection = controller.connection,
            gateway = FakeTsnetGateway(),
        )
    }

    @Test
    fun `connected state shows compact connected indicator in top bar`() = runTest {
        val controller = createController {
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1")
        }
        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
        )

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live").assertDoesNotExist()
    }

    @Test
    fun `connecting state shows compact progress indicator in top bar`() = runTest {
        val deferred = kotlinx.coroutines.CompletableDeferred<FrigateConnection>()
        val controller = createController { deferred.await() }
        val cameraDiscovery = createDiscoveryController()

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        runCurrent()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Connecting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connecting").assertDoesNotExist()

        deferred.complete(FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"))
    }

    @Test
    fun `failed state shows compact problem indicator in top bar`() = runTest {
        val controller = createController {
            FrigateConnection.Failed("network unreachable")
        }
        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Failed("network unreachable"),
        )

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Connection problem").assertIsDisplayed()
    }

    @Test
    fun `home screen keeps refresh and settings accessible`() = runTest {
        val controller = createController {
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        }
        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Refresh cameras").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `home screen does not show technical terms`() = runTest {
        val controller = createController {
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1")
        }
        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
        )

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("LOCAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("TAILSCALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("Frigate").assertDoesNotExist()
    }

    @Test
    fun `refresh button triggers camera refresh`() = runTest {
        val controller = createController {
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        }

        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )
        val initialKey = cameraDiscovery.refreshKey.value

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Refresh cameras").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "Refresh must bump refreshKey",
            cameraDiscovery.refreshKey.value > initialKey,
        )
    }

    @Test
    fun `settings button invokes open settings callback`() = runTest {
        val controller = createController {
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")
        }
        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
        )

        var settingsOpened = false
        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = { settingsOpened = true },
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Settings callback must be invoked", settingsOpened)
    }

    @Test
    fun `failed state still shows product-language retry area`() = runTest {
        val controller = createController {
            FrigateConnection.Failed("network unreachable")
        }
        val cameraDiscovery = createDiscoveryController(
            FrigateConnection.Failed("network unreachable"),
        )

        composeTestRule.setContent {
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = factory(controller),
                snapshotImageLoader = fakeSnapshotImageLoader(context),
                baseUrl = "http://frigate:5000",
                onOpenSettings = {},
                onOpenLocations = {},
                onCameraSelected = {},
            )
        }

        composeTestRule.waitForIdle()
        controller.connect(restartPlayback = false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Could not reach the camera server.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
