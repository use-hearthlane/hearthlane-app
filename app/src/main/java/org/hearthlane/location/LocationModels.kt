package org.hearthlane.location

/**
 * Product models for the location capability (Phase 9.3).
 *
 * The location source is ALWAYS the Android framework
 * [android.location.LocationManager] — never Google Play Services — and the
 * favorite provider is read back from the returned
 * [android.location.Location]. Values use the monotonic clock for age and
 * acquisition measurements and the wall clock only as payload metadata.
 */
data class LocationSample(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Wall-clock timestamp reported by [android.location.Location.getTime]. */
    val recordedAtWallClockMs: Long,
    /** Monotonic timestamp reported by Location.getElapsedRealtimeNanos. */
    val recordedAtElapsedNanos: Long,
    /** Age of the fix at capture time, from the monotonic clock, in ms. */
    val ageMs: Long,
    /** Time spent acquiring the fix, in ms (0 for a last-known read). */
    val acquisitionMs: Long,
    val fromLastKnown: Boolean,
) {
    val hasAccuracy: Boolean
        get() = !accuracyMeters.isNaN() && accuracyMeters >= 0f
}

/** Coarse outcome of a single location read. */
enum class LocationReadStatus {
    SUCCESS,
    NO_POSITION,
    NO_PERMISSION,
    LOCATION_DISABLED,
    TIMEOUT,
    ERROR,
}

data class LocationReadResult(
    val status: LocationReadStatus,
    val sample: LocationSample? = null,
    val message: String? = null,
)

/** Which framework API path a current-position read uses for a given SDK. */
enum class LocationApi {
    /** LocationManager.getCurrentLocation, Android 11+ (API 30+). */
    API30_CURRENT,

    /** LocationManager.requestSingleUpdate, Android 8-10 (API 26-29). */
    API26_SINGLE_UPDATE,
}