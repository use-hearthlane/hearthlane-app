package org.hearthlane.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-only fullscreen state for the event-detail player.
 *
 * Fullscreen is a pure view concern: it never touches the Frigate domain, the
 * event model or the player itself (the same player instance is re-attached to
 * the fullscreen surface). The Back handler and the fullscreen toggle share the
 * same [exit] path so both always produce the same result.
 */
class FullscreenState {

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    /** Enters fullscreen. */
    fun enter() {
        _isFullscreen.value = true
    }

    /** Exits fullscreen, restoring the normal Event Detail. */
    fun exit() {
        _isFullscreen.value = false
    }

    /** Toggles between normal and fullscreen. */
    fun toggle() {
        _isFullscreen.value = !_isFullscreen.value
    }

    /**
     * Handles a Back press: exits fullscreen and reports that the press was
     * consumed. Returns true when fullscreen was active (so the caller must NOT
     * navigate); false when the press should fall through to normal navigation.
     */
    fun handleBack(): Boolean {
        if (_isFullscreen.value) {
            _isFullscreen.value = false
            return true
        }
        return false
    }
}