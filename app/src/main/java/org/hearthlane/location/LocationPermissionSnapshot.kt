package org.hearthlane.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Real permission/location state consumed by the location-sharing controller.
 * Kept free of Android imports in the controller so its logic stays testable;
 * the snapshot itself is produced here, where the platform checks live.
 */
data class LocationPermissionSnapshot(
    val foregroundGranted: Boolean,
    val backgroundGranted: Boolean,
    val locationEnabled: Boolean,
) {
    companion object {
        /** Reads the current permission and master-switch state. */
        fun from(context: Context): LocationPermissionSnapshot = LocationPermissionSnapshot(
            foregroundGranted = hasLocationPermission(context),
            backgroundGranted = hasBackgroundLocationPermission(context),
            locationEnabled = LocationFgsGate.isLocationEnabled(context),
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
}