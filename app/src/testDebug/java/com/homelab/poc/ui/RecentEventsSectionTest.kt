package com.homelab.poc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.homelab.poc.controller.RecentEventsController
import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.FrigateEventApi
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.test.FakeTsnetGateway
import com.homelab.poc.test.fakeSnapshotImageLoader
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException

/**
 * Robolectric/Compose tests for the [RecentEventsSection] contract (the list
 * content composed below the camera's live player): loading/empty/error states,
 * retry, thumbnails, in-progress events, duration, and loading an additional
 * (older) page.
 *
 * The controller runs on the runTest scheduler (same pattern as HomeScreenTest)
 * so its state updates are synchronized with the Compose test clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentEventsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val imageLoader = fakeSnapshotImageLoader(context)
    private val factory = CameraThumbnailModelFactory(
        connection = MutableStateFlow(FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1")),
        gateway = FakeTsnetGateway(),
    )

    private fun TestScope.createController(getter: HttpBytesGetter): RecentEventsController =
        RecentEventsController(
            api = FrigateEventApi(getter),
            cameraId = "backyard",
            baseUrl = { "http://frigate:5000" },
            limit = 2,
            scope = this,
        )

    private fun TestScope.loadAndIdle(controller: RecentEventsController) {
        controller.loadInitial()
        advanceUntilIdle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `loaded events render labels and duration`() = runTest {
        val controller = createController(RoutingGetter())

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("person").assertIsDisplayed()
        composeTestRule.onNodeWithText("car").assertIsDisplayed()
        composeTestRule.onNodeWithText("10s").assertIsDisplayed()
    }

    @Test
    fun `in-progress event shows the in-progress label`() = runTest {
        val controller = createController(RoutingGetter())

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("In progress").assertIsDisplayed()
    }

    @Test
    fun `empty state shows the empty message`() = runTest {
        val controller = createController(RoutingGetter(recent = "[]"))

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("No recent events.").assertIsDisplayed()
    }

    @Test
    fun `error state shows retry and retrying loads events`() = runTest {
        val getter = RoutingGetter(failRecent = true)
        val controller = createController(getter)

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("Could not load events.").assertIsDisplayed()

        getter.failRecent = false
        composeTestRule.onNodeWithText("Retry").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("person").assertIsDisplayed()
    }

    @Test
    fun `loading more appends the older page`() = runTest {
        val controller = createController(RoutingGetter())

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("Load older events").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("package").assertIsDisplayed()
        composeTestRule.onNodeWithText("person").assertIsDisplayed()
    }

    @Test
    fun `pagination failure keeps the list and shows a retry`() = runTest {
        val getter = RoutingGetter(failOlder = true)
        val controller = createController(getter)

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("Load older events").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Could not load more events.").assertIsDisplayed()
        composeTestRule.onNodeWithText("person").assertIsDisplayed()

        getter.failOlder = false
        composeTestRule.onNodeWithText("Retry").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        assertTrue(
            "after a successful retry the older page is appended",
            getter.requestedBefores.isNotEmpty(),
        )
        composeTestRule.onNodeWithText("package").assertIsDisplayed()
    }

    @Test
    fun `a row without a snapshot still renders its label`() = runTest {
        val controller = createController(RoutingGetter(recent = eventsJson(noSnapshotEvent)))

        composeTestRule.setContent { Screen(controller) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("person").assertIsDisplayed()
    }

    @Test
    fun `tapping an event invokes onEventSelected with its id`() = runTest {
        var selected: String? = null
        val controller = createController(RoutingGetter())

        composeTestRule.setContent { Screen(controller, onEventSelected = { selected = it }) }
        composeTestRule.waitForIdle()
        loadAndIdle(controller)

        composeTestRule.onNodeWithText("person").performClick()

        assertEquals("the tapped row must report the event id", "a", selected)
    }

    @Composable
    private fun Screen(controller: RecentEventsController, onEventSelected: (String) -> Unit = {}) {
        RecentEventsSection(
            controller = controller,
            thumbnailFactory = factory,
            snapshotImageLoader = imageLoader,
            baseUrl = "http://frigate:5000",
            onEventSelected = onEventSelected,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** Routes recent vs older pages and records the `before` cursors used. */
    private class RoutingGetter(
        var recent: String = eventsJson(personEvent, carEvent),
        private val older: String = eventsJson(olderEvent),
        var failRecent: Boolean = false,
        var failOlder: Boolean = false,
    ) : HttpBytesGetter {
        val requestedBefores = mutableListOf<String>()

        override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult = when {
            failRecent && !url.contains("before=") -> throw IOException("recent failed")
            failOlder && url.contains("before=") -> throw IOException("older failed")
            url.contains("before=") -> {
                requestedBefores.add(url.substringAfter("before="))
                HttpBytesResult(200, "application/json", url, older.toByteArray())
            }
            else -> HttpBytesResult(200, "application/json", url, recent.toByteArray())
        }
    }

    private companion object {
        const val T = 1787072293.5
        val personEvent =
            """{"id":"a","camera":"backyard","label":"person","start_time":$T,"end_time":${T + 10},"has_clip":true,"has_snapshot":true,"zones":[]}"""
        val carEvent =
            """{"id":"b","camera":"backyard","label":"car","start_time":${T - 100},"end_time":null,"has_clip":true,"has_snapshot":true,"zones":[]}"""
        val noSnapshotEvent =
            """{"id":"c","camera":"backyard","label":"person","start_time":$T,"end_time":${T + 5},"has_clip":false,"has_snapshot":false,"zones":[]}"""
        val olderEvent =
            """{"id":"d","camera":"backyard","label":"package","start_time":${T - 500},"end_time":${T - 490},"has_clip":true,"has_snapshot":true,"zones":[]}"""

        fun eventsJson(vararg events: String): String = events.joinToString(",", "[", "]")
    }
}
