package com.homelab.poc.navigation

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
}
