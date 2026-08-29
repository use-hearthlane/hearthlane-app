package org.hearthlane.core.relay

import org.hearthlane.core.connectivity.HttpBytesResult
import java.io.IOException

/** Raised by the HTTP relay client on a non-2xx response. */
class RelayException(message: String) : IOException(message)

/**
 * The data operations the app performs against the relay contract.
 *
 * The client is bound to one [RelayHttpTransport] (selected by the connection
 * manager) and one base URL, so it never re-runs the connection strategy and
 * never leaks the network path to callers.
 */
interface RelayClient {
    /**
     * Replaces the relay's stored location for [deviceId] (never a history)
     * and returns the HTTP status code of the response (200/201/204 on
     * success). Throws [RelayException] on a non-2xx response.
     */
    suspend fun publishLocation(deviceId: String, location: DeviceLocation): Int

    /** Known devices (last-known semantics; ordering is not part of the contract). */
    suspend fun listDevices(): List<DeviceInfo>

    /** Last published location for [deviceId], or null when never published. */
    suspend fun getLocation(deviceId: String): DeviceLocation?

    /** Sets (or replaces) the single nickname for [deviceId]. */
    suspend fun setNickname(deviceId: String, nickname: String)
}

/**
 * HTTP implementation of the relay contract. Points at a configurable base
 * URL (no discovery). The base URL is the API root, with the contract paths
 * built without a version prefix:
 *
 * `GET  /devices`
 * `GET  /devices/{deviceId}/location`
 * `PUT  /devices/{deviceId}/location`
 * `PUT  /devices/{deviceId}/nickname`
 *
 * The relay MVP has no application authentication, so no request carries an
 * `Authorization` header.
 */
class HttpRelayClient(
    private val transport: RelayHttpTransport,
    private val baseUrl: String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : RelayClient {

    override suspend fun publishLocation(deviceId: String, location: DeviceLocation): Int {
        val response = request(
            method = "PUT",
            url = "${apiBase()}/devices/$deviceId/location",
            contentType = JSON_CONTENT_TYPE,
            body = RelayContractJson.locationBody(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.recordedAtEpochMs,
            ),
        )
        if (response.statusCode !in 200..299) {
            throw RelayException("publish location failed: HTTP ${response.statusCode}")
        }
        return response.statusCode
    }

    override suspend fun listDevices(): List<DeviceInfo> {
        val response = request(method = "GET", url = "${apiBase()}/devices", contentType = null, body = null)
        if (response.statusCode != 200) {
            throw RelayException("list devices failed: HTTP ${response.statusCode}")
        }
        return RelayContractJson.parseDeviceList(response.body.toString(Charsets.UTF_8))
    }

    override suspend fun getLocation(deviceId: String): DeviceLocation? {
        val response = request(
            method = "GET",
            url = "${apiBase()}/devices/$deviceId/location",
            contentType = null,
            body = null,
        )
        return when (response.statusCode) {
            200 -> RelayContractJson.parseLocation(response.body.toString(Charsets.UTF_8))
            404 -> null
            else -> throw RelayException("get location failed: HTTP ${response.statusCode}")
        }
    }

    override suspend fun setNickname(deviceId: String, nickname: String) {
        val response = request(
            method = "PUT",
            url = "${apiBase()}/devices/$deviceId/nickname",
            contentType = JSON_CONTENT_TYPE,
            body = RelayContractJson.nicknameBody(nickname),
        )
        if (response.statusCode !in 200..299) {
            throw RelayException("set nickname failed: HTTP ${response.statusCode}")
        }
    }

    /**
     * The relay API root: the configured base URL used as the root of the
     * contract paths. A trailing slash is stripped so the built URLs never
     * contain `//` (e.g. `http://host:8080/` + `/devices`).
     */
    private fun apiBase(): String = baseUrl.trimEnd('/')

    private suspend fun request(
        method: String,
        url: String,
        contentType: String?,
        body: String?,
    ): HttpBytesResult =
        transport.request(method, url, contentType, body, headers = emptyMap(), timeoutMs)

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val JSON_CONTENT_TYPE = "application/json"
    }
}