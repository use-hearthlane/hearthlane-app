package org.hearthlane.core.relay

/**
 * Domain models and JSON parsing for the HYPOTHETICAL relay contract.
 *
 * The relay is a future, separate project; this module only consumes the
 * contract (frozen in docs/SPIKE-LOCATION-9-2.md). Nothing here is a server.
 *
 * The model is last-known only: publishing replaces the previous location.
 * There is no history anywhere.
 */

/** A device's last known location, as stored and served by the relay. */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy in meters. */
    val accuracy: Float,
    /** Device wall clock from [android.location.Location.getTime]. */
    val recordedAtEpochMs: Long,
    /** Assigned by the relay at publish time; null before the first publish. */
    val publishedAtEpochMs: Long? = null,
)

/** A device known to the relay, with its single nickname (optional). */
data class DeviceInfo(
    val deviceId: String,
    val nickname: String? = null,
)

/** Presentation state derived from the age of the last publication. */
enum class LocationStatus { AVAILABLE, STALE, UNAVAILABLE }

/**
 * Available/Stale/Unavailable derivation. The relay never deletes a location;
 * the client decides the state from [DeviceLocation.publishedAtEpochMs] age.
 * Thresholds are experimental (10 min / 60 min) and may change with product
 * data.
 */
object LocationStatusRules {

    const val AVAILABLE_AGE_MS = 10 * 60_000L
    const val STALE_AGE_MS = 60 * 60_000L

    fun statusFor(publishedAtEpochMs: Long?, nowEpochMs: Long): LocationStatus {
        if (publishedAtEpochMs == null) return LocationStatus.UNAVAILABLE
        val age = nowEpochMs - publishedAtEpochMs
        return when {
            age < AVAILABLE_AGE_MS -> LocationStatus.AVAILABLE
            age < STALE_AGE_MS -> LocationStatus.STALE
            else -> LocationStatus.UNAVAILABLE
        }
    }
}

/** Contract JSON: parses the relay response shapes and builds request bodies. */
object RelayContractJson {

    /** GET /devices/{id}/location response body. */
    fun parseLocation(json: String): DeviceLocation? {
        val latitude = numberField(json, "latitude") ?: return null
        val longitude = numberField(json, "longitude") ?: return null
        return DeviceLocation(
            latitude = latitude,
            longitude = longitude,
            accuracy = numberField(json, "accuracy")?.toFloat() ?: 0f,
            recordedAtEpochMs = numberField(json, "recordedAtEpochMs")?.toLong() ?: 0L,
            publishedAtEpochMs = numberField(json, "publishedAtEpochMs")?.toLong(),
        )
    }

    /** GET /devices response body. */
    fun parseDeviceList(json: String): List<DeviceInfo> =
        arrayElements(json, "devices").mapNotNull(::parseDevice)

    /** PUT /devices/{id}/location request body. The relay rejects out-of-range
     *  coordinates so the client never sends them. */
    fun locationBody(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        recordedAtEpochMs: Long,
    ): String = buildString {
        append('{')
        append("\"latitude\":").append(latitude)
        append(",\"longitude\":").append(longitude)
        append(",\"accuracy\":").append(accuracy)
        append(",\"recordedAtEpochMs\":").append(recordedAtEpochMs)
        append('}')
    }

    /** PUT /devices/{id}/nickname request body. */
    fun nicknameBody(nickname: String): String =
        "{\"nickname\":\"${jsonEscape(nickname)}\"}"

    private fun parseDevice(raw: String): DeviceInfo? {
        val deviceId = stringField(raw, "deviceId") ?: return null
        return DeviceInfo(
            deviceId = deviceId,
            nickname = stringField(raw, "nickname"),
        )
    }

    // Minimal JSON field readers. The relay response is a flat object (or a
    // flat array under a top-level key); hand-rolling keeps the module free of
    // a JSON dependency and is fully unit-testable.

    private fun numberField(json: String, name: String): Double? =
        fieldValue(json, name)?.toDoubleOrNull()

    private fun stringField(json: String, name: String): String? =
        fieldValue(json, name)?.let { unescape(it) }

    /** Returns the raw (non-escaped) value for the first `"name":` found. */
    private fun fieldValue(json: String, name: String): String? {
        val marker = "\"$name\":"
        val start = json.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        var i = valueStart
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        return when (json[i]) {
            '"' -> {
                // Scan to the closing quote, honoring \" escapes and never
                // treating an escaped quote as the terminator.
                var j = i + 1
                while (j < json.length) {
                    val c = json[j]
                    if (c == '"' && json[j - 1] != '\\') break
                    j++
                }
                if (j >= json.length) null else json.substring(i + 1, j)
            }
            else -> {
                var end = i
                while (end < json.length && json[end] != ',' && json[end] != '}') end++
                json.substring(i, end).trim()
            }
        }
    }

    private fun arrayElements(json: String, name: String): List<String> {
        val marker = "\"$name\":["
        val start = json.indexOf(marker)
        if (start < 0) return emptyList()
        val contentStart = start + marker.length
        var end = contentStart
        var depth = 0
        var inString = false
        while (end < json.length) {
            val c = json[end]
            when {
                c == '"' && json.getOrNull(end - 1) != '\\' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> if (depth > 0) depth--
                !inString && c == ']' && depth == 0 -> break
            }
            end++
        }
        if (end >= json.length) return emptyList()
        return splitTopLevel(json, contentStart, end)
    }

    /** Splits [json] on top-level commas (comma at depth 0, outside a string). */
    private fun splitTopLevel(json: String, start: Int, end: Int): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var i = start
        while (i < end) {
            val c = json[i]
            when {
                c == '"' && json.getOrNull(i - 1) != '\\' -> inString = !inString
                !inString && (c == '{' || c == '[') -> depth++
                !inString && (c == '}' || c == ']') -> if (depth > 0) depth--
                !inString && c == ',' && depth == 0 -> {
                    current.toString().trim().takeIf { it.isNotEmpty() }?.let(result::add)
                    current.clear()
                    i++
                    continue
                }
            }
            current.append(c)
            i++
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(result::add)
        return result
    }

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\\\", "\\")

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}