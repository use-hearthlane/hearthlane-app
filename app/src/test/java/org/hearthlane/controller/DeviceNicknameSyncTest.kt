package org.hearthlane.controller

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import org.hearthlane.core.relay.DeviceInfo
import org.hearthlane.core.relay.DeviceLocation
import org.hearthlane.core.relay.RelayClient
import org.hearthlane.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for [DeviceNicknameSync]: the nickname is presentation only (identity
 * stays the node hostname), is always persisted locally, and is mirrored to the
 * relay best-effort — a relay outage never throws and never blocks setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceNicknameSyncTest {

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File.createTempFile("nickname_sync_test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private suspend fun settings(scope: CoroutineScope): AppSettings =
        AppSettings.createForTest(dataStore(scope), "hearthlane.example", scope)
            .also { it.ready.first { ready -> ready } }

    @Test
    fun `persists the nickname locally even when the relay is unreachable`() = runTest {
        val settings = settings(backgroundScope)
        val sync = DeviceNicknameSync(settings, relayClient = { null })

        sync.apply("Meu celular")

        assertEquals("Meu celular", settings.deviceNickname.value)
    }

    @Test
    fun `pushes the nickname to the relay when reachable`() = runTest {
        val settings = settings(backgroundScope)
        val fake = org.hearthlane.test.FakeRelayClient()
        val sync = DeviceNicknameSync(settings, relayClient = { fake })

        sync.apply("Celular do Braz")

        val deviceId = AppSettings.nodeHostname(settings.nodeSuffix.value)
        assertEquals("Celular do Braz", fake.nickname(deviceId))
        // Identity is the deviceId, not the nickname.
        assertTrue("the relay key must be the node hostname", deviceId.startsWith("hearthlane-"))
    }

    @Test
    fun `blank nickname is persisted but never pushed`() = runTest {
        val settings = settings(backgroundScope)
        val fake = org.hearthlane.test.FakeRelayClient()
        val sync = DeviceNicknameSync(settings, relayClient = { fake })

        sync.apply("   ")

        assertEquals("", settings.deviceNickname.value)
        assertTrue("a blank nickname must not touch the relay", fake.nicknames().isEmpty())
    }

    @Test
    fun `relay failure does not throw and keeps the local nickname`() = runTest {
        val settings = settings(backgroundScope)
        val failing = object : RelayClient {
            override suspend fun publishLocation(deviceId: String, location: DeviceLocation): Int = 204
            override suspend fun listDevices(): List<DeviceInfo> = emptyList()
            override suspend fun getLocation(deviceId: String): DeviceLocation? = null
            override suspend fun setNickname(deviceId: String, nickname: String) {
                throw java.io.IOException("relay unreachable")
            }
        }
        val sync = DeviceNicknameSync(settings, relayClient = { failing })

        sync.apply("Tablet da sala")

        assertEquals("Tablet da sala", settings.deviceNickname.value)
    }

    @Test
    fun `nickname is not the identity - deviceId comes from the node suffix`() = runTest {
        val settings = settings(backgroundScope)
        val fake = org.hearthlane.test.FakeRelayClient()
        val sync = DeviceNicknameSync(settings, relayClient = { fake })

        sync.apply("Any nickname")

        val nodeHost = AppSettings.nodeHostname(settings.nodeSuffix.value)
        val storedNickname = fake.nickname(nodeHost)
        assertTrue("the stored nickname must be exactly the submitted value", storedNickname == "Any nickname")
        assertTrue("the relay must key by node hostname", nodeHost != "Any nickname")
    }
}