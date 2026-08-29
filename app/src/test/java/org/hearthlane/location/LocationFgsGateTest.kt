package org.hearthlane.location

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pure JVM tests for [LocationFgsGate]'s decision logic: the block/ready
 * matrix that mirrors the platform's FGS `location` eligibility rules, plus
 * the Android adapters it depends on (permission state, location switch).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationFgsGateTest {

    private fun input(
        locationPermission: Boolean = true,
        backgroundPermission: Boolean = true,
        locationEnabled: Boolean = true,
        eligibleForeground: Boolean = true,
    ) = LocationFgsGate.EvaluateInput(
        hasLocationPermission = locationPermission,
        hasBackgroundLocationPermission = backgroundPermission,
        isLocationEnabled = locationEnabled,
        isEligibleForeground = eligibleForeground,
    )

    @Test
    fun `ready when foreground with location permission granted`() {
        val decision = LocationFgsGate.evaluate(input())
        assertTrue(decision.ready)
        assertEquals(null, decision.reason)
    }

    @Test
    fun `missing location permission blocks regardless of foreground state`() {
        listOf(true, false).forEach { foreground ->
            val decision = LocationFgsGate.evaluate(
                input(locationPermission = false, eligibleForeground = foreground),
            )
            assertEquals(false, decision.ready)
            assertEquals(
                LocationFgsGate.BlockReason.MISSING_LOCATION_PERMISSION,
                decision.reason,
            )
        }
    }

    @Test
    fun `foreground start ignores background permission and the location switch`() {
        val decision = LocationFgsGate.evaluate(
            input(backgroundPermission = false, locationEnabled = false, eligibleForeground = true),
        )
        assertTrue(decision.ready)
        assertEquals(null, decision.reason)
    }

    @Test
    fun `background start needs the background location permission`() {
        val decision = LocationFgsGate.evaluate(
            input(backgroundPermission = false, eligibleForeground = false),
        )
        assertEquals(false, decision.ready)
        assertEquals(
            LocationFgsGate.BlockReason.BACKGROUND_START_NEEDS_BACKGROUND_PERMISSION,
            decision.reason,
        )
    }

    @Test
    fun `background start with background permission but location off is blocked`() {
        val decision = LocationFgsGate.evaluate(
            input(locationEnabled = false, eligibleForeground = false),
        )
        assertEquals(false, decision.ready)
        assertEquals(
            LocationFgsGate.BlockReason.BACKGROUND_START_NEEDS_LOCATION_ENABLED,
            decision.reason,
        )
    }

    @Test
    fun `background start is ready only with permission and location on`() {
        val decision = LocationFgsGate.evaluate(input(eligibleForeground = false))
        assertTrue(decision.ready)
        assertEquals(null, decision.reason)
    }

    @Test
    fun `evaluateForUiStart is blocked without the location permission`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val decision = LocationFgsGate.evaluateForUiStart(app)
        assertEquals(false, decision.ready)
        assertEquals(
            LocationFgsGate.BlockReason.MISSING_LOCATION_PERMISSION,
            decision.reason,
        )
    }

    @Test
    fun `evaluateForUiStart is ready once the location permission is granted`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val decision = LocationFgsGate.evaluateForUiStart(app)
        assertEquals(true, decision.ready)
        assertEquals(null, decision.reason)
    }

    @Test
    fun `isLocationEnabled follows the system location switch`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val locationManager = app.getSystemService(LocationManager::class.java)!!
        shadowOf(locationManager).setLocationEnabled(false)
        assertEquals(false, LocationFgsGate.isLocationEnabled(app))
        shadowOf(locationManager).setLocationEnabled(true)
        assertEquals(true, LocationFgsGate.isLocationEnabled(app))
    }
}