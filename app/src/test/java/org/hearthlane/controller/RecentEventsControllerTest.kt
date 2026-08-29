package org.hearthlane.controller

import org.hearthlane.core.connectivity.HttpBytesGetter
import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.frigate.FrigateEventApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecentEventsControllerTest {

    private val limit = 2

    @Test
    fun `starts in Loading state`() = runTest {
        val controller = controller(scope = backgroundScope)

        assertEquals(RecentEventsState.Loading, controller.state.value)
    }

    @Test
    fun `loadInitial loads the most recent events`() = runTest {
        val controller = controller(scope = backgroundScope)

        controller.loadInitial()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(listOf("2-b", "1-a"), state.events.map { it.id })
        assertTrue("a full page means more may be available", state.canLoadMore)
    }

    @Test
    fun `a short page maps to Loaded with no more pages`() = runTest {
        val controller = controller(
            scope = backgroundScope,
            recent = eventsJson(event("1-a", 1.0, 2.0)),
        )

        controller.loadInitial()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(1, state.events.size)
        assertFalse("a page shorter than the limit means no more pages", state.canLoadMore)
    }

    @Test
    fun `empty recent events map to Empty`() = runTest {
        val controller = controller(scope = backgroundScope, recent = "[]")

        controller.loadInitial()
        runCurrent()

        assertEquals(RecentEventsState.Empty, controller.state.value)
    }

    @Test
    fun `initial load failure maps to Error`() = runTest {
        val controller = controller(scope = backgroundScope, failRecent = true)

        controller.loadInitial()
        runCurrent()

        assertTrue(controller.state.value is RecentEventsState.Error)
    }

    @Test
    fun `retry after an error loads events`() = runTest {
        val getter = RoutingGetter(recent = defaultRecent(), older = defaultOlder(), failRecent = true)
        val controller = RecentEventsController(
            api = FrigateEventApi(getter),
            cameraId = "backyard",
            baseUrl = { "http://frigate:5000" },
            limit = limit,
            scope = backgroundScope,
        )

        controller.loadInitial()
        runCurrent()
        assertTrue(controller.state.value is RecentEventsState.Error)

        getter.failRecent = false
        controller.loadInitial()
        runCurrent()

        assertTrue(controller.state.value is RecentEventsState.Loaded)
    }

    @Test
    fun `loadMore appends the older page`() = runTest {
        val controller = controller(scope = backgroundScope)

        controller.loadInitial()
        runCurrent()
        controller.loadMore()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(listOf("2-b", "1-a", "4-d", "3-c"), state.events.map { it.id })
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `loadMore keeps the sub-second cursor precision`() = runTest {
        val controller = controller(scope = backgroundScope)

        controller.loadInitial()
        runCurrent()
        controller.loadMore()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(4, state.events.size)
        assertEquals(3.25, state.events[3].startTime, 0.0)
    }

    @Test
    fun `pagination failure preserves the loaded events`() = runTest {
        val controller = controller(scope = backgroundScope, failOlder = true)

        controller.loadInitial()
        runCurrent()
        controller.loadMore()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals("the loaded events must survive a pagination failure", 2, state.events.size)
        assertNotNull("the failure must be surfaced for a retry", state.loadMoreError)
        assertFalse(state.loadingMore)
    }

    @Test
    fun `loadMore is a no-op when no more pages exist`() = runTest {
        val controller = controller(
            scope = backgroundScope,
            recent = eventsJson(event("1-a", 1.0, 2.0)),
        )

        controller.loadInitial()
        runCurrent()
        controller.loadMore()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(1, state.events.size)
    }

    @Test
    fun `refresh reloads the initial page discarding pagination`() = runTest {
        val controller = controller(scope = backgroundScope)

        controller.loadInitial()
        runCurrent()
        controller.loadMore()
        runCurrent()
        assertEquals(4, (controller.state.value as RecentEventsState.Loaded).events.size)

        controller.refresh()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(listOf("2-b", "1-a"), state.events.map { it.id })
    }

    @Test
    fun `an in-progress event keeps a null endTime`() = runTest {
        val controller = controller(
            scope = backgroundScope,
            recent = eventsJson(
                event("1-a", 1.0, 2.0),
                """{"id":"0-z","camera":"backyard","label":"person","start_time":0.5,"end_time":null,"has_clip":false,"has_snapshot":true,"zones":[]}""",
            ),
        )

        controller.loadInitial()
        runCurrent()

        val events = (controller.state.value as RecentEventsState.Loaded).events
        val inProgress = events.first { it.id == "0-z" }
        assertNull("an in-progress event has a null endTime", inProgress.endTime)
    }

    @Test
    fun `loadMore after a refresh uses the new page cursor`() = runTest {
        val controller = controller(scope = backgroundScope)

        controller.loadInitial()
        runCurrent()
        controller.refresh()
        runCurrent()
        controller.loadMore()
        runCurrent()

        val state = controller.state.value as RecentEventsState.Loaded
        assertEquals(listOf("2-b", "1-a", "4-d", "3-c"), state.events.map { it.id })
    }

    private fun controller(
        scope: CoroutineScope,
        recent: String = defaultRecent(),
        older: String = defaultOlder(),
        failRecent: Boolean = false,
        failOlder: Boolean = false,
    ): RecentEventsController = RecentEventsController(
        api = FrigateEventApi(
            RoutingGetter(
                recent = recent,
                older = older,
                failRecent = failRecent,
                failOlder = failOlder,
            ),
        ),
        cameraId = "backyard",
        baseUrl = { "http://frigate:5000" },
        limit = limit,
        scope = scope,
    )

    private class RoutingGetter(
        private val recent: String,
        private val older: String,
        var failRecent: Boolean = false,
        var failOlder: Boolean = false,
    ) : HttpBytesGetter {
        override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult = when {
            failRecent && !url.contains("before=") -> throw IOException("recent failed")
            failOlder && url.contains("before=") -> throw IOException("older failed")
            url.contains("before=") ->
                HttpBytesResult(200, "application/json", url, older.toByteArray())
            else -> HttpBytesResult(200, "application/json", url, recent.toByteArray())
        }
    }
}

private fun eventsJson(vararg events: String): String = events.joinToString(",", "[", "]")
private fun event(id: String, start: Double, end: Double): String =
    """{"id":"$id","camera":"backyard","label":"person","start_time":$start,"end_time":$end,"has_clip":true,"has_snapshot":true,"zones":[]}"""
private fun defaultRecent(): String = eventsJson(event("2-b", 2.0, 3.0), event("1-a", 1.0, 2.0))
private fun defaultOlder(): String = eventsJson(event("4-d", 4.0, 5.0), event("3-c", 3.25, 4.0))
