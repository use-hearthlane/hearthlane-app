package org.hearthlane.location

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for the SDK-dependent API selection. No Robolectric: the
 * decision must be deterministic from the SDK integer alone.
 */
class LocationReadingStrategyTest {

    @Test
    fun `sdk 30 and above use getCurrentLocation`() {
        assertEquals(LocationApi.API30_CURRENT, LocationReadingStrategy.apiForSdk(30))
        assertEquals(LocationApi.API30_CURRENT, LocationReadingStrategy.apiForSdk(31))
        assertEquals(LocationApi.API30_CURRENT, LocationReadingStrategy.apiForSdk(36))
    }

    @Test
    fun `sdk 26 to 29 use the single-update fallback`() {
        assertEquals(LocationApi.API26_SINGLE_UPDATE, LocationReadingStrategy.apiForSdk(26))
        assertEquals(LocationApi.API26_SINGLE_UPDATE, LocationReadingStrategy.apiForSdk(28))
        assertEquals(LocationApi.API26_SINGLE_UPDATE, LocationReadingStrategy.apiForSdk(29))
    }

    @Test
    fun `sdk below 26 also maps to the single-update fallback`() {
        assertEquals(LocationApi.API26_SINGLE_UPDATE, LocationReadingStrategy.apiForSdk(25))
    }

    @Test
    fun `provider preference is low-power coarse first`() {
        assertEquals(
            listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            ),
            LocationReadingStrategy.providerPreference,
        )
    }
}