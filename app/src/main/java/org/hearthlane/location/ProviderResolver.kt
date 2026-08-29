package org.hearthlane.location

import android.location.LocationManager
import android.os.Build

/**
 * Resolves provider availability without assuming which provider will be
 * used, and without depending on the device having any particular provider.
 *
 * Three levels are kept apart by design:
 * - the provider exists on the device;
 * - the provider is enabled (the user has not disabled it);
 * - the provider actually delivered a fix (read back from Location.getProvider).
 *
 * [providers] is injectable so the resolver is testable without a real
 * LocationManager; the default is the low-power preference.
 */
class ProviderResolver(
    private val locationManager: LocationManager,
    private val providers: List<String> = LocationReadingStrategy.providerPreference,
) {

    fun isProviderEnabled(provider: String): Boolean =
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)

    /** Enabled providers, in the preference order supplied at construction. */
    fun enabledProviders(): List<String> = providers.filter(::isProviderEnabled)

    /**
     * "Location service switched on" signal. Uses the system master switch on
     * API 28+ (LocationManager.isLocationEnabled) and falls back to the known
     * providers on API 26-27, where the master switch is not exposed.
     */
    fun isLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val master = runCatching { locationManager.isLocationEnabled() }.getOrNull()
            if (master != null) return master
        }
        return enabledProviders().isNotEmpty()
    }

    /** First enabled provider in preference order, or null when none is on. */
    fun preferredEnabledProvider(): String? = providers.firstOrNull(::isProviderEnabled)
}