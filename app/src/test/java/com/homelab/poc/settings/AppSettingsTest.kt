package com.homelab.poc.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsTest {

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File.createTempFile("app_settings_test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @Test
    fun `seeds the build default URL on first run`() = runTest {
        val settings = AppSettings.createForTest(
            dataStore = dataStore(backgroundScope),
            defaultBaseUrl = "http://frigate:5000",
            scope = backgroundScope,
        )

        settings.ready.first { it }

        assertEquals("http://frigate:5000", settings.baseUrl.value)
    }

    @Test
    fun `persists an edited URL across instances`() = runTest {
        val store = dataStore(backgroundScope)
        val settings = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        settings.ready.first { it }

        settings.setBaseUrl("http://other:6000")

        val reloaded = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        reloaded.ready.first { it }
        assertEquals("http://other:6000", reloaded.baseUrl.value)
    }

    @Test
    fun `node suffix is generated once and stable across instances`() = runTest {
        val store = dataStore(backgroundScope)
        val settings = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        settings.ready.first { it }

        val suffix = settings.nodeSuffix.value
        assertTrue("suffix must be 8 hex chars, was \"$suffix\"", suffix.matches(Regex("[0-9a-f]{8}")))
        assertTrue("hostname must carry the suffix", AppSettings.nodeHostname(suffix).startsWith("hearthlane-"))

        val reloaded = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        reloaded.ready.first { it }
        assertEquals("the same installation must keep its node hostname", suffix, reloaded.nodeSuffix.value)
    }

    @Test
    fun `generated suffixes are unique`() {
        val suffixes = (1..1000).map { AppSettings.generateSuffix() }.toSet()
        assertEquals(1000, suffixes.size)
    }

    @Test
    fun `setup complete defaults to false on first run`() = runTest {
        val settings = AppSettings.createForTest(
            dataStore = dataStore(backgroundScope),
            defaultBaseUrl = "http://frigate:5000",
            scope = backgroundScope,
        )

        settings.ready.first { it }

        assertEquals(
            "a fresh installation must be gated on the initial setup",
            false,
            settings.setupComplete.value,
        )
    }

    @Test
    fun `setup complete is persisted across instances`() = runTest {
        val store = dataStore(backgroundScope)
        val settings = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        settings.ready.first { it }
        assertEquals(false, settings.setupComplete.value)

        settings.setSetupComplete(true)

        val reloaded = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        reloaded.ready.first { it }
        assertEquals("setup must stay complete across app restarts", true, reloaded.setupComplete.value)
    }

    @Test
    fun `url and setup flag round-trip together`() = runTest {
        val store = dataStore(backgroundScope)
        val settings = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        settings.ready.first { it }

        settings.setBaseUrl("http://site.omni.corp")
        settings.setSetupComplete(true)

        val reloaded = AppSettings.createForTest(store, "http://default:5000", backgroundScope)
        reloaded.ready.first { it }
        assertEquals("http://site.omni.corp", reloaded.baseUrl.value)
        assertEquals(true, reloaded.setupComplete.value)
    }
}
