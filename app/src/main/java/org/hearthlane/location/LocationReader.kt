package org.hearthlane.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Thin foreground reader over android.location.LocationManager (no Google
 * Play Services).
 *
 * Three paths, selected by SDK (see [LocationReadingStrategy]):
 * - last known:    LocationManager.getLastKnownLocation(provider)
 * - API 30+:       LocationManager.getCurrentLocation(provider, signal, executor, consumer)
 * - API 26-29:     LocationManager.requestSingleUpdate(provider, listener, looper)
 *
 * Everything runs on [ioDispatcher] and returns a [LocationReadResult]; real
 * fix quality (accuracy, freshness, battery) is validated on a physical
 * device, not in unit tests.
 */
@SuppressLint("MissingPermission")
class LocationReader(
    private val context: Context,
    private val locationManager: LocationManager,
    private val resolver: ProviderResolver = ProviderResolver(locationManager),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Reads the best last-known position from the enabled providers, in
     * preference order. The fix may be arbitrarily old; its [LocationSample.ageMs]
     * is always recorded and must never be treated as "current".
     */
    suspend fun readLastKnown(): LocationReadResult = withContext(ioDispatcher) {
        if (!hasCoarsePermission()) return@withContext permissionDenied()

        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
        for (provider in resolver.enabledProviders()) {
            val location = runCatching { locationManager.getLastKnownLocation(provider) }
                .getOrNull()
            if (location != null) {
                return@withContext LocationReadResult(
                    status = LocationReadStatus.SUCCESS,
                    sample = location.toSample(
                        provider = location.provider ?: provider,
                        nowElapsedNanos = nowElapsedNanos,
                        acquisitionMs = 0L,
                        fromLastKnown = true,
                    ),
                )
            }
        }

        LocationReadResult(
            status = if (resolver.isLocationEnabled()) {
                LocationReadStatus.NO_POSITION
            } else {
                LocationReadStatus.LOCATION_DISABLED
            },
            message = "no last-known position",
        )
    }

    /**
     * Requests a fresh position on demand. Prefers network for a fast,
     * coarse, low-power fix, falling back to GPS/passive. Uses
     * getCurrentLocation on API 30+ and requestSingleUpdate on API 26-29,
     * both bounded by [timeoutMs]. Never blocks the main thread.
     */
    @SuppressLint("NewApi")
    suspend fun readCurrent(timeoutMs: Long = DEFAULT_CURRENT_TIMEOUT_MS): LocationReadResult =
        withContext(ioDispatcher) {
            if (!hasCoarsePermission()) return@withContext permissionDenied()

            val provider = resolver.preferredEnabledProvider()
            if (provider == null) {
                return@withContext LocationReadResult(
                    status = if (resolver.isLocationEnabled()) {
                        LocationReadStatus.NO_POSITION
                    } else {
                        LocationReadStatus.LOCATION_DISABLED
                    },
                    message = "no enabled provider",
                )
            }

            val startedAt = SystemClock.elapsedRealtimeNanos()
            val acquired = try {
                withTimeout(timeoutMs) {
                    when (LocationReadingStrategy.apiForSdk(Build.VERSION.SDK_INT)) {
                        LocationApi.API30_CURRENT -> currentApi30(provider)
                        LocationApi.API26_SINGLE_UPDATE -> singleUpdateApi26(provider)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                return@withContext LocationReadResult(
                    status = LocationReadStatus.TIMEOUT,
                    message = "no fix within ${timeoutMs}ms ($provider)",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext LocationReadResult(
                    status = LocationReadStatus.ERROR,
                    message = "current read failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }

            val location = acquired ?: return@withContext LocationReadResult(
                status = LocationReadStatus.NO_POSITION,
                message = "no position delivered by $provider",
            )

            LocationReadResult(
                status = LocationReadStatus.SUCCESS,
                sample = location.toSample(
                    provider = location.provider ?: provider,
                    nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                    acquisitionMs = LocationTime.durationMs(
                        startedAt,
                        SystemClock.elapsedRealtimeNanos(),
                    ),
                    fromLastKnown = false,
                ),
            )
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun currentApi30(provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            // Direct executor: the consumer only resumes the continuation.
            val executor = Executor { command -> command.run() }
            try {
                locationManager.getCurrentLocation(provider, signal, executor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (e: IllegalArgumentException) {
                // Provider disabled/unavailable between the check and the call.
                if (cont.isActive) cont.resume(null)
            }
        }

    private suspend fun singleUpdateApi26(provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = LocationListener { location ->
                if (cont.isActive) cont.resume(location)
            }
            cont.invokeOnCancellation { runCatching { locationManager.removeUpdates(listener) } }
            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (e: IllegalArgumentException) {
                // Provider disabled/unavailable between the check and the call.
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun permissionDenied(): LocationReadResult = LocationReadResult(
        status = LocationReadStatus.NO_PERMISSION,
        message = "missing ${Manifest.permission.ACCESS_COARSE_LOCATION}",
    )

    private fun Location.toSample(
        provider: String,
        nowElapsedNanos: Long,
        acquisitionMs: Long,
        fromLastKnown: Boolean,
    ): LocationSample {
        val recordedElapsed = runCatching { elapsedRealtimeNanos }.getOrDefault(nowElapsedNanos)
        return LocationSample(
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = runCatching { accuracy }.getOrDefault(Float.NaN),
            recordedAtWallClockMs = time,
            recordedAtElapsedNanos = recordedElapsed,
            ageMs = LocationTime.ageMs(recordedElapsed, nowElapsedNanos),
            acquisitionMs = acquisitionMs,
            fromLastKnown = fromLastKnown,
        )
    }

    private companion object {
        const val DEFAULT_CURRENT_TIMEOUT_MS = 15_000L
    }
}