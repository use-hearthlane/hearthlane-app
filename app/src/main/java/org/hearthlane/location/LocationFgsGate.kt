package org.hearthlane.location

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Gate that decides whether a location foreground service may be started in
 * the current app/device state.
 *
 * Platform background: since Android 14 (API 34), the system evaluates the
 * eligibility of an FGS of type `location` at the moment the service calls
 * [android.app.Service.startForeground] — not when the service was started.
 * When the app is not allowed to access the while-in-use location permission
 * in that instant, [android.app.Service.startForeground] throws
 * `SecurityException` ("Starting FGS with type location ... foreground only
 * permission") and the process crashes.
 *
 * Critical consequence: once a `startForegroundService` call has been issued,
 * the service MUST call [android.app.Service.startForeground]; there is no
 * safe abort — stopping the service first also crashes the app with
 * `ForegroundServiceDidNotStartInTimeException`. The eligibility check must
 * therefore live AFTER the decision points (the UI permission flow and the
 * boot receiver) and BEFORE `startForegroundService` is invoked, never
 * inside the service.
 *
 * The encoded rule matches the platform's FGS `location` policy:
 *  1. the app must hold at least one of ACCESS_COARSE_LOCATION /
 *     ACCESS_FINE_LOCATION (the `anyOf` set of the type policy);
 *  2. a start from an eligible foreground state only needs rule 1;
 *  3. a start from the background additionally requires
 *     ACCESS_BACKGROUND_LOCATION and the device location switch to be on.
 *
 * Note: a missing location switch alone does NOT block a foreground start;
 * the foreground UI path intentionally ignores the background requirements.
 */
object LocationFgsGate {

    enum class BlockReason {
        MISSING_LOCATION_PERMISSION,
        BACKGROUND_START_NEEDS_BACKGROUND_PERMISSION,
        BACKGROUND_START_NEEDS_LOCATION_ENABLED,
    }

    data class Evaluation(val ready: Boolean, val reason: BlockReason?)

    /** Inputs of the decision, isolated so the logic is testable without Android. */
    data class EvaluateInput(
        val hasLocationPermission: Boolean,
        val hasBackgroundLocationPermission: Boolean,
        val isLocationEnabled: Boolean,
        val isEligibleForeground: Boolean,
    )

    /** Pure decision logic. */
    fun evaluate(input: EvaluateInput): Evaluation {
        if (!input.hasLocationPermission) {
            return Evaluation(false, BlockReason.MISSING_LOCATION_PERMISSION)
        }
        if (!input.isEligibleForeground) {
            if (!input.hasBackgroundLocationPermission) {
                return Evaluation(false, BlockReason.BACKGROUND_START_NEEDS_BACKGROUND_PERMISSION)
            }
            if (!input.isLocationEnabled) {
                return Evaluation(false, BlockReason.BACKGROUND_START_NEEDS_LOCATION_ENABLED)
            }
        }
        return Evaluation(true, null)
    }

    /** Full evaluation against the real app/device state (background callers). */
    fun evaluate(context: Context): Evaluation = evaluate(inputFor(context))

    /**
     * Evaluation for a start initiated while the app is on screen: the
     * activity is visible, so the app is in an eligible foreground state and
     * only the location permission is needed.
     */
    fun evaluateForUiStart(context: Context): Evaluation =
        evaluate(
            EvaluateInput(
                hasLocationPermission = hasLocationPermission(context),
                hasBackgroundLocationPermission = true,
                isLocationEnabled = true,
                isEligibleForeground = true,
            ),
        )

    /** "Location service switched on" signal (system master switch / providers). */
    fun isLocationEnabled(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)
            ?.let { ProviderResolver(it).isLocationEnabled() }
            ?: false

    /** ActivityManager-based check whether the app is in an eligible visible state. */
    internal fun activityManagerForeground(context: Context): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }.getOrDefault(false)

    private fun inputFor(context: Context): EvaluateInput = EvaluateInput(
        hasLocationPermission = hasLocationPermission(context),
        hasBackgroundLocationPermission = hasBackgroundLocationPermission(context),
        isLocationEnabled = isLocationEnabled(context),
        isEligibleForeground = activityManagerForeground(context),
    )

    private fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * ACCESS_BACKGROUND_LOCATION only exists from API 30. Before that the
     * background capability is carried by whichever of the normal location
     * permissions is granted, so the missing constant would otherwise block
     * background starts on API 26-29.
     */
    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return hasLocationPermission(context)
        }
        return hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}