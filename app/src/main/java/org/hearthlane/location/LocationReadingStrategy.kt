package org.hearthlane.location

import android.location.LocationManager
import android.os.Build

/**
 * Pure selection of the current-position API path for a given SDK.
 *
 * The framework offers two non-blocking ways to ask for a fresh position:
 * - Android 11+ (API 30): LocationManager.getCurrentLocation, the modern
 *   one-shot API with CancellationSignal support.
 * - Android 8-10 (API 26-29): LocationManager.requestSingleUpdate.
 *
 * Kept as a pure function so the SDK split is unit-tested without a device.
 * minSdk stays 26; nothing in the product raises it.
 */
internal object LocationReadingStrategy {

    fun apiForSdk(sdkInt: Int): LocationApi =
        if (sdkInt >= Build.VERSION_CODES.R) LocationApi.API30_CURRENT
        else LocationApi.API26_SINGLE_UPDATE

    /**
     * Provider preference for a low-power, coarse-first foreground read:
     * network (fast, cheap, works indoors) first, then GPS, then passive.
     * The provider that actually delivered a fix is always read back from the
     * returned Location; this list only decides what to ask for first.
     */
    val providerPreference: List<String> = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
}