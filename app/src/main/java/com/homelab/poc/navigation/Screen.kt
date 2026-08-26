package com.homelab.poc.navigation

import androidx.compose.runtime.mutableStateListOf

/**
 * V1 screen set. Sealed-class based on purpose: the whole navigation space is
 * small and must be checked exhaustively by the compiler, with no new
 * dependency.
 *
 * V1.0 ships the Home shell. V1.1 adds the Setup screen: it is both the
 * first-run gate and the Settings/Admin re-entry for editing the server URL.
 * V1.5 adds the administrator Settings hub and the Diagnostics screen, both
 * reached from Home via the discreet Settings entry.
 */
sealed interface Screen {
    data object Home : Screen

    /** First-run administrator setup; reopened from Settings to edit the server. */
    data object Setup : Screen

    /** V1.5 administrator hub: server settings, diagnostics, node reset, app info. */
    data object Settings : Screen

    /** V1.5 administrator observability screen with the sanitized report copy. */
    data object Diagnostics : Screen

    /**
     * Live view for a single camera. The route identity is the camera id; the
     * display name is passed as a screen argument so the title can stay
     * family-facing without re-querying discovery.
     */
    data class Live(val cameraId: String) : Screen

    /**
     * Details for a single event. Reached from the camera screen's recent-events
     * list. The route carries the camera id for navigation context and the event
     * id, which is the identity of the resource: the detail screen fetches the
     * event itself and never receives the whole [Event] as a navigation
     * argument.
     */
    data class EventDetail(val cameraId: String, val eventId: String) : Screen
}

/**
 * Minimal back-stack navigation state, backed by a Compose observable list so
 * a `when` on [current] recomposes when it changes.
 */
class AppNavigation(initial: Screen = Screen.Home) {

    private val backStack = mutableStateListOf(initial)

    /** The screen on top of the back stack. */
    val current: Screen get() = backStack.last()

    /** Pushes [screen] onto the back stack. */
    fun navigateTo(screen: Screen) {
        backStack.add(screen)
    }

    /** Pops the top screen; the initial screen can never be popped. */
    fun navigateBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Replaces the whole back stack with [screen]. */
    fun resetTo(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }
}
