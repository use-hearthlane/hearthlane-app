package org.hearthlane.location

import androidx.test.core.app.ApplicationProvider
import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric shadow-level tests for [LocationReader].
 *
 * What these cover: the permission gate, the "location disabled / no enabled
 * provider" outcomes, the last-known read path, the API 30+ getCurrentLocation
 * path (the shadow delivers a fresh last-known synchronously) and the
 * API 26-29 single-update timeout path.
 *
 * What they do NOT cover (physical-device only): real GPS/network fix quality,
 * real acquisition latency, battery cost, OEM behaviour.
 *
 * Note: Robolectric's shadow enables the three framework providers by default,
 * so every test explicitly declares the intended provider state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationReaderTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val context = app as Context
    private val locationManager = context.getSystemService(LocationManager::class.java)!!
    private val shadow = shadowOf(locationManager)

    private fun grantCoarsePermission() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun disableAllProviders() {
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.PASSIVE_PROVIDER, false)
    }

    private fun enableNetworkOnly() {
        disableAllProviders()
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    /** Turns the system location master switch off. */
    private fun disableSystemLocation() {
        shadow.setLocationEnabled(false)
    }

    private fun newNetworkLocation(
        latitude: Double = -23.5505,
        longitude: Double = -46.6333,
        accuracyMeters: Float = 40f,
        ageNanos: Long = 1_000_000_000L,
    ): Location = Location(LocationManager.NETWORK_PROVIDER).apply {
        this.latitude = latitude
        this.longitude = longitude
        this.accuracy = accuracyMeters
        time = System.currentTimeMillis() - 1_000
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageNanos
    }

    private fun reader() = LocationReader(context, locationManager)

    @Test
    fun `readLastKnown without permission returns NO_PERMISSION`() = runTest {
        val result = reader().readLastKnown()
        assertEquals(LocationReadStatus.NO_PERMISSION, result.status)
    }

    @Test
    fun `readCurrent without permission returns NO_PERMISSION`() = runTest {
        val result = reader().readCurrent()
        assertEquals(LocationReadStatus.NO_PERMISSION, result.status)
    }

    @Test
    fun `readLastKnown with permission but location disabled returns LOCATION_DISABLED`() = runTest {
        grantCoarsePermission()
        disableSystemLocation()
        val result = reader().readLastKnown()
        assertEquals(LocationReadStatus.LOCATION_DISABLED, result.status)
    }

    @Test
    fun `readCurrent with permission but location disabled returns LOCATION_DISABLED`() = runTest {
        grantCoarsePermission()
        disableSystemLocation()
        val result = reader().readCurrent()
        assertEquals(LocationReadStatus.LOCATION_DISABLED, result.status)
    }

    @Test
    fun `readLastKnown with location on but no stored fix returns NO_POSITION`() = runTest {
        grantCoarsePermission()
        enableNetworkOnly()
        val result = reader().readLastKnown()
        assertEquals(LocationReadStatus.NO_POSITION, result.status)
    }

    @Test
    fun `readLastKnown returns the enabled provider's fix with its age`() = runTest {
        grantCoarsePermission()
        enableNetworkOnly()
        val location = newNetworkLocation()
        shadow.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location)

        val result = reader().readLastKnown()

        assertEquals(LocationReadStatus.SUCCESS, result.status)
        val sample = result.sample
        assertNotNull(sample)
        assertEquals(LocationManager.NETWORK_PROVIDER, sample!!.provider)
        assertEquals(-23.5505, sample.latitude, 0.0)
        assertEquals(40f, sample.accuracyMeters)
        assertTrue(sample.hasAccuracy)
        assertTrue(sample.fromLastKnown)
        assertEquals(0L, sample.acquisitionMs)
        // The fix was recorded ~1s in the past; its measured age must be ~1s.
        assertTrue(sample.ageMs in 500..2_000)
    }

    @Test
    fun `readCurrent on API 30 plus delivers a fresh last-known fix synchronously`() = runTest {
        grantCoarsePermission()
        enableNetworkOnly()
        shadow.setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            newNetworkLocation(),
        )

        val result = reader().readCurrent()

        assertEquals(LocationReadStatus.SUCCESS, result.status)
        val sample = result.sample
        assertNotNull(sample)
        assertFalse(sample!!.fromLastKnown)
        assertEquals(LocationManager.NETWORK_PROVIDER, sample.provider)
    }

    @Test
    fun `readCurrent on API 30 plus without a fix times out instead of hanging`() = runTest {
        grantCoarsePermission()
        enableNetworkOnly()

        val result = reader().readCurrent(timeoutMs = 500)

        assertEquals(LocationReadStatus.TIMEOUT, result.status)
    }

    @Test
    @Config(sdk = [28])
    fun `readCurrent on API 26-29 without a fix times out via the single-update path`() = runTest {
        grantCoarsePermission()
        enableNetworkOnly()

        val result = reader().readCurrent(timeoutMs = 500)

        assertEquals(LocationReadStatus.TIMEOUT, result.status)
    }
}