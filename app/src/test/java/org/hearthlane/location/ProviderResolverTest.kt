package org.hearthlane.location

import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric shadow-level tests for [ProviderResolver].
 *
 * Coverage: how the resolver maps provider state to the enabled/preferred
 * lists, and how it reads the system location master switch. This validates
 * the resolver's logic, NOT real provider behaviour — the real availability
 * matrix must be observed on a physical device.
 *
 * Note: Robolectric's shadow enables the three framework providers and the
 * master switch by default, so every test explicitly declares the intended
 * state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderResolverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val locationManager = context.getSystemService(LocationManager::class.java)!!
    private val shadow = shadowOf(locationManager)

    private fun setProvider(provider: String, enabled: Boolean) {
        shadow.setProviderEnabled(provider, enabled)
    }

    @Test
    fun `preferred provider returns the first enabled in preference order`() {
        setProvider(LocationManager.GPS_PROVIDER, false)
        setProvider(LocationManager.PASSIVE_PROVIDER, false)
        setProvider(LocationManager.NETWORK_PROVIDER, true)

        val resolver = ProviderResolver(locationManager)
        assertEquals(LocationManager.NETWORK_PROVIDER, resolver.preferredEnabledProvider())
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), resolver.enabledProviders())
    }

    @Test
    fun `enabled providers keep preference order when gps is on but network is off`() {
        setProvider(LocationManager.NETWORK_PROVIDER, false)
        setProvider(LocationManager.PASSIVE_PROVIDER, false)
        setProvider(LocationManager.GPS_PROVIDER, true)

        val resolver = ProviderResolver(locationManager)
        assertEquals(LocationManager.GPS_PROVIDER, resolver.preferredEnabledProvider())
        assertEquals(listOf(LocationManager.GPS_PROVIDER), resolver.enabledProviders())
    }

    @Test
    fun `preferred provider returns null when no known provider is enabled`() {
        setProvider(LocationManager.NETWORK_PROVIDER, false)
        setProvider(LocationManager.GPS_PROVIDER, false)
        setProvider(LocationManager.PASSIVE_PROVIDER, false)

        val resolver = ProviderResolver(locationManager)
        assertNull(resolver.preferredEnabledProvider())
        assertTrue(resolver.enabledProviders().isEmpty())
    }

    @Test
    fun `isLocationEnabled reflects the system master switch`() {
        val resolver = ProviderResolver(locationManager)

        shadow.setLocationEnabled(false)
        assertFalse(resolver.isLocationEnabled())

        shadow.setLocationEnabled(true)
        assertTrue(resolver.isLocationEnabled())
    }
}