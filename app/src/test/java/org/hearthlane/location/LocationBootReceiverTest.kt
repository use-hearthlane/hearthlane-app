package org.hearthlane.location

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [LocationBootReceiver]: a BOOT_COMPLETED start runs
 * from the background, so the receiver delegates to [LocationFgsGate] before
 * dispatching anything. The gate's own decision matrix is covered by
 * [LocationFgsGateTest]; here the receiver's behavior is locked with a fixed
 * evaluation injected through the test-only constructor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationBootReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `ignores broadcasts that are not boot completed`() {
        val receiver = LocationBootReceiver(LocationFgsGate.Evaluation(true, null))

        receiver.onReceive(app, Intent("org.hearthlane.NOT_A_BOOT"))

        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun `does not start the service when the gate blocks`() {
        val receiver = LocationBootReceiver(
            LocationFgsGate.Evaluation(
                false,
                LocationFgsGate.BlockReason.MISSING_LOCATION_PERMISSION,
            ),
        )

        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun `starts the service when the gate passes`() {
        val receiver = LocationBootReceiver(LocationFgsGate.Evaluation(true, null))

        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(LocationForegroundService.ACTION_START, shadowOf(app).nextStartedService?.action)
    }
}