package org.hearthlane.controller

import org.hearthlane.core.relay.RelayClient
import org.hearthlane.settings.AppSettings
import kotlinx.coroutines.CancellationException

/**
 * Persists this device's nickname locally and mirrors it to the relay when
 * reachable, using the existing `PUT /devices/{deviceId}/nickname` contract.
 *
 * The nickname is presentation only; identity is always the deviceId derived
 * from the persisted node suffix. A relay outage never throws: the nickname is
 * kept locally and can be re-synced later (the Locations screen re-applies it
 * on the next successful relay connection).
 */
class DeviceNicknameSync(
    private val settings: AppSettings,
    private val relayClient: suspend () -> RelayClient?,
) {

    /** Persists [nickname] and, when non-blank and the relay is reachable,
     *  best-effort syncs it. Never throws for network failures. */
    suspend fun apply(nickname: String) {
        settings.setDeviceNickname(nickname)
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) return
        val deviceId = AppSettings.nodeHostname(settings.nodeSuffix.value)
        val client = relayClient() ?: return
        try {
            client.setNickname(deviceId, trimmed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Local-only; a later connection re-syncs it.
        }
    }
}