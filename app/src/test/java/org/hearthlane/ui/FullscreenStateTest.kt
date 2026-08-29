package org.hearthlane.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [FullscreenState]: the presentation-only state transitions used by
 * the event-detail player, including the Back rule (the first Back exits
 * fullscreen and must not navigate).
 */
class FullscreenStateTest {

    @Test
    fun `starts in normal mode`() = runTest {
        assertFalse(FullscreenState().isFullscreen.value)
    }

    @Test
    fun `enter moves from normal to fullscreen`() = runTest {
        val state = FullscreenState()

        state.enter()

        assertTrue(state.isFullscreen.value)
    }

    @Test
    fun `exit moves from fullscreen to normal`() = runTest {
        val state = FullscreenState().apply { enter() }
        assertTrue(state.isFullscreen.value)

        state.exit()

        assertFalse(state.isFullscreen.value)
    }

    @Test
    fun `toggle switches both directions`() = runTest {
        val state = FullscreenState()

        state.toggle()
        assertTrue(state.isFullscreen.value)

        state.toggle()
        assertFalse(state.isFullscreen.value)
    }

    @Test
    fun `back while fullscreen exits fullscreen and consumes the press`() = runTest {
        val state = FullscreenState().apply { enter() }

        val consumed = state.handleBack()

        assertTrue("the first Back must be consumed by fullscreen", consumed)
        assertFalse("and the screen returns to normal", state.isFullscreen.value)
    }

    @Test
    fun `back while normal is not consumed so navigation can happen`() = runTest {
        val state = FullscreenState()

        val consumed = state.handleBack()

        assertFalse("the Back must fall through to normal navigation", consumed)
        assertFalse(state.isFullscreen.value)
    }
}