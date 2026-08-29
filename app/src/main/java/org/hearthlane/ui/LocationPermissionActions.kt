package org.hearthlane.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** Opens the per-app details page where "Allow all the time" is chosen. */
internal fun openBackgroundPermissionSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ),
    )
}

/** Opens the device location settings (master switch). */
internal fun openDeviceLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}