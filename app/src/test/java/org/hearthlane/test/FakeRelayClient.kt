package org.hearthlane.test

import org.hearthlane.core.relay.DeviceInfo
import org.hearthlane.core.relay.DeviceLocation
import org.hearthlane.core.relay.RelayClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test double for [RelayClient]. Stores only the last location per device and
 * one nickname per device (last-known semantics, never a history). Publish
 * failures can be injected via [failPublish].
 */
class FakeRelayClient : RelayClient {

    private val mutex = Mutex()
    private val locations = mutableMapOf<String, DeviceLocation>()
    private val nicknames = mutableMapOf<String, String>()

    /** When true, [publishLocation] throws, mimicking a relay outage. */
    var failPublish = false

    /** Status returned by [publishLocation] on success. */
    var publishStatus: Int = 204

    /** When true, [listDevices]/[getLocation] throw, mimicking a query failure. */
    var failReads = false

    private fun failIfReadFailing() {
        if (failReads) throw java.io.IOException("relay query failed")
    }

    override suspend fun publishLocation(deviceId: String, location: DeviceLocation): Int =
        mutex.withLock {
            if (failPublish) {
                throw java.io.IOException("relay unreachable")
            }
            locations[deviceId] = location.copy(publishedAtEpochMs = System.currentTimeMillis())
            publishStatus
        }

    override suspend fun listDevices(): List<DeviceInfo> = mutex.withLock {
        failIfReadFailing()
        val ids = (locations.keys + nicknames.keys).distinct().sorted()
        ids.map { DeviceInfo(it, nicknames[it]) }
    }

    override suspend fun getLocation(deviceId: String): DeviceLocation? {
        failIfReadFailing()
        return mutex.withLock { locations[deviceId] }
    }

    override suspend fun setNickname(deviceId: String, nickname: String) =
        mutex.withLock { nicknames[deviceId] = nickname }

    /** Nickname currently stored for [deviceId], or null. Test accessor. */
    suspend fun nickname(deviceId: String): String? = mutex.withLock { nicknames[deviceId] }

    /** All stored nicknames. Test accessor. */
    suspend fun nicknames(): Map<String, String> = mutex.withLock { nicknames.toMap() }
}