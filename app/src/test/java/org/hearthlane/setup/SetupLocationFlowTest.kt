package org.hearthlane.setup

import org.hearthlane.controller.LocationSharingController
import org.hearthlane.controller.LocationSharingStep
import org.hearthlane.location.LocationPermissionSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SetupLocationFlow] / [SetupLocationPhases]: the Setup provisioning
 * derives from the shared [LocationSharingController], requests foreground
 * only, moves to background, reaches Operational only when fully eligible, and
 * lets the user finish with an explicitly acknowledged limitation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupLocationFlowTest {

    private class Harness(
        val scope: TestScope,
        initialDesired: Boolean = true,
        var foregroundGranted: Boolean = false,
        var backgroundGranted: Boolean = false,
        val backgroundRequired: Boolean = true,
        var locationEnabled: Boolean = true,
    ) {
        val desired = MutableStateFlow(initialDesired)
        val controller = LocationSharingController(
            scope = scope,
            sharingEnabled = desired,
            setSharingEnabledAction = { desired.value = it },
            permissionSnapshot = {
                LocationPermissionSnapshot(foregroundGranted, backgroundGranted, locationEnabled)
            },
            backgroundPermissionRequired = backgroundRequired,
            startService = { true },
            stopService = { true },
        )
        val flow = SetupLocationFlow(controller)

        fun phase(): SetupLocationPhase =
            SetupLocationPhases.phaseFor(controller.state.value.status, flow.acknowledged.value)
    }

    @Test
    fun `new install with default on and no permission needs foreground`() = runTest {
        val harness = Harness(this)

        assertEquals(SetupLocationPhase.NeedsForegroundPermission, harness.phase())
        assertFalse(SetupLocationPhases.canFinish(harness.phase()))
    }

    @Test
    fun `start requests the foreground permission`() = runTest {
        val harness = Harness(this)

        harness.flow.start()
        advanceUntilIdle()

        assertEquals(LocationSharingStep.RequestForegroundPermission, harness.controller.state.value.step)
    }

    @Test
    fun `foreground granted moves to needs background`() = runTest {
        val harness = Harness(this)
        harness.foregroundGranted = true

        harness.flow.onForegroundPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.NeedsBackgroundPermission, harness.phase())
    }

    @Test
    fun `background granted with location on becomes operational`() = runTest {
        val harness = Harness(this)
        harness.foregroundGranted = true
        harness.backgroundGranted = true

        harness.flow.onSettingsReturned()
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.Operational, harness.phase())
        assertTrue(SetupLocationPhases.canFinish(harness.phase()))
    }

    @Test
    fun `foreground denied never requests background`() = runTest {
        val harness = Harness(this)
        harness.foregroundGranted = false

        harness.flow.onForegroundPermissionResult(granted = false)
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.NeedsForegroundPermission, harness.phase())
        assertEquals(
            "background must not follow a foreground denial",
            LocationSharingStep.None,
            harness.controller.state.value.step,
        )
        assertFalse(SetupLocationPhases.canFinish(harness.phase()))
    }

    @Test
    fun `background denied is not treated as operational`() = runTest {
        val harness = Harness(this)
        harness.foregroundGranted = true
        harness.backgroundGranted = false

        harness.flow.onSettingsReturned()
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.NeedsBackgroundPermission, harness.phase())
        assertFalse(SetupLocationPhases.canFinish(harness.phase()))
    }

    @Test
    fun `acknowledging a limitation allows finishing`() = runTest {
        val harness = Harness(this)
        assertEquals(SetupLocationPhase.NeedsForegroundPermission, harness.phase())

        harness.flow.acknowledgeLimitation()
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.Limited, harness.phase())
        assertTrue(SetupLocationPhases.canFinish(harness.phase()))
    }

    @Test
    fun `start after acknowledging restarts the flow`() = runTest {
        val harness = Harness(this)
        harness.flow.acknowledgeLimitation()
        advanceUntilIdle()
        assertEquals(SetupLocationPhase.Limited, harness.phase())

        harness.flow.start()
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.NeedsForegroundPermission, harness.phase())
        assertEquals(LocationSharingStep.RequestForegroundPermission, harness.controller.state.value.step)
    }

    @Test
    fun `already granted permissions never re-request`() = runTest {
        val harness = Harness(
            this,
            initialDesired = true,
            foregroundGranted = true,
            backgroundGranted = true,
            locationEnabled = true,
        )

        assertEquals(SetupLocationPhase.Operational, harness.phase())
        assertEquals("no re-request when already granted", LocationSharingStep.None, harness.controller.state.value.step)

        harness.flow.start()
        advanceUntilIdle()

        assertEquals(LocationSharingStep.None, harness.controller.state.value.step)
    }

    @Test
    fun `location services off is reported and revalidated on return`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true, locationEnabled = false)

        assertEquals(SetupLocationPhase.LocationServicesDisabled, harness.phase())
        assertFalse(SetupLocationPhases.canFinish(harness.phase()))

        harness.locationEnabled = true
        harness.flow.onSettingsReturned()
        advanceUntilIdle()

        assertEquals(SetupLocationPhase.Operational, harness.phase())
    }

    @Test
    fun `sharing explicitly off hides the flow`() = runTest {
        val harness = Harness(this, initialDesired = false, foregroundGranted = false)

        assertEquals(SetupLocationPhase.Hidden, harness.phase())
        assertTrue("finishing must not be blocked when sharing is off", SetupLocationPhases.canFinish(harness.phase()))
    }

    @Test
    fun `background not required skips straight to operational`() = runTest {
        val harness = Harness(
            this,
            initialDesired = true,
            foregroundGranted = true,
            backgroundRequired = false,
            locationEnabled = true,
        )

        assertEquals(SetupLocationPhase.Operational, harness.phase())
    }
}