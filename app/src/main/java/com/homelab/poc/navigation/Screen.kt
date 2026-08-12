package com.homelab.poc.navigation

import androidx.compose.runtime.mutableStateListOf

/**
 * V1 screen set. Sealed-class based on purpose: the whole navigation space is
 * small and must be checked exhaustively by the compiler, with no new
 * dependency.
 *
 * V1.0 ships only the Home shell; Live View, Diagnostics and Settings arrive in
 * later milestones and are added here as new objects.
 */
sealed interface Screen {
    data object Home : Screen
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
