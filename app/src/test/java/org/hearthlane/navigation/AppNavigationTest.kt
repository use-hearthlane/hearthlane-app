package org.hearthlane.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {

    @Test
    fun `starts at the Home shell`() {
        assertEquals(Screen.Home, AppNavigation().current)
    }

    @Test
    fun `navigateTo pushes and navigateBack pops`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Home)
        assertEquals(Screen.Home, navigation.current)
        navigation.navigateBack()
        assertEquals(Screen.Home, navigation.current)
    }

    @Test
    fun `navigateBack never pops the initial screen`() {
        val navigation = AppNavigation()
        navigation.navigateBack()
        navigation.navigateBack()
        assertEquals(Screen.Home, navigation.current)
    }

    @Test
    fun `resetTo replaces the whole back stack`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Home)
        navigation.resetTo(Screen.Home)
        navigation.navigateBack()
        assertEquals("after reset the stack has exactly one screen", Screen.Home, navigation.current)
    }

    @Test
    fun `Setup is reachable from Home and pops back`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Setup)
        assertEquals(Screen.Setup, navigation.current)
        navigation.navigateBack()
        assertEquals(Screen.Home, navigation.current)
    }

    @Test
    fun `resetTo Home clears the Setup route`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Setup)
        navigation.resetTo(Screen.Home)
        navigation.navigateBack()
        assertEquals("Setup must not remain on the stack after a reset", Screen.Home, navigation.current)
    }

    @Test
    fun `Live screen is pushed with camera id and pops back`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Live("backyard"))
        assertEquals(Screen.Live("backyard"), navigation.current)
        navigation.navigateBack()
        assertEquals(Screen.Home, navigation.current)
    }

    @Test
    fun `Live screen identity is the camera id`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Live("garage"))
        val current = navigation.current as Screen.Live
        assertEquals("garage", current.cameraId)
    }

    @Test
    fun `Settings is reachable from Home and pops back`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Settings)
        assertEquals(Screen.Settings, navigation.current)
        navigation.navigateBack()
        assertEquals(Screen.Home, navigation.current)
    }

    @Test
    fun `Diagnostics is reachable from Settings and pops back through it`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Settings)
        navigation.navigateTo(Screen.Diagnostics)
        assertEquals(Screen.Diagnostics, navigation.current)
        navigation.navigateBack()
        assertEquals("back from Diagnostics lands on Settings", Screen.Settings, navigation.current)
        navigation.navigateBack()
        assertEquals(Screen.Home, navigation.current)
    }

    @Test
    fun `EventDetail is pushed with camera and event id from the camera screen`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Live("backyard"))
        navigation.navigateTo(Screen.EventDetail("backyard", "evt-1"))
        val current = navigation.current as Screen.EventDetail
        assertEquals("camera id must be preserved on the route", "backyard", current.cameraId)
        assertEquals("event id must be preserved on the route", "evt-1", current.eventId)
    }

    @Test
    fun `back from EventDetail returns directly to the camera screen`() {
        val navigation = AppNavigation()
        navigation.navigateTo(Screen.Live("backyard"))
        navigation.navigateTo(Screen.EventDetail("backyard", "evt-1"))
        navigation.navigateBack()
        assertEquals(
            "back from EventDetail must land directly on the camera screen (no intermediate list)",
            Screen.Live("backyard"),
            navigation.current,
        )
    }
}
