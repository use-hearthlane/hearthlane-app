package org.hearthlane.controller

import org.hearthlane.location.LocationPermissionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LocationSharingController] and [LocationSharingStatusResolver]:
 * the permission flow (foreground -> explanation -> system settings -> return)
 * and the rule that the foreground service only ever starts when the system is
 * fully eligible. No Android/Activity is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationSharingControllerTest {

    private class Harness(
        val scope: CoroutineScope,
        initialDesired: Boolean = false,
        var foregroundGranted: Boolean = false,
        var backgroundGranted: Boolean = false,
        val backgroundRequired: Boolean = true,
        var locationEnabled: Boolean = true,
        var startSucceeds: Boolean = true,
        var stopSucceeds: Boolean = true,
    ) {
        val desired = MutableStateFlow(initialDesired)
        val persisted = mutableListOf<Boolean>()
        var startCalls = 0
        var stopCalls = 0

        val controller = LocationSharingController(
            scope = scope,
            sharingEnabled = desired,
            setSharingEnabledAction = { enabled ->
                desired.value = enabled
                persisted.add(enabled)
            },
            permissionSnapshot = {
                LocationPermissionSnapshot(
                    foregroundGranted = foregroundGranted,
                    backgroundGranted = backgroundGranted,
                    locationEnabled = locationEnabled,
                )
            },
            backgroundPermissionRequired = backgroundRequired,
            startService = {
                startCalls++
                startSucceeds
            },
            stopService = {
                stopCalls++
                stopSucceeds
            },
        )
    }

    @Test
    fun `toggle off stays disabled and never starts the service`() = runTest {
        val harness = Harness(this, initialDesired = false)

        harness.controller.onToggleDesired(false)
        advanceUntilIdle()

        assertEquals(LocationSharingStatus.Disabled, harness.controller.state.value.status)
        assertEquals(0, harness.startCalls)
        assertEquals(0, harness.stopCalls)
    }

    @Test
    fun `toggle on without foreground permission requests it and never starts`() = runTest {
        val harness = Harness(this, foregroundGranted = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.NeedsForegroundPermission, state.status)
        assertEquals(LocationSharingStep.RequestForegroundPermission, state.step)
        assertEquals("the desire is persisted even while permission is missing", true, state.desired)
        assertEquals(0, harness.startCalls)
    }

    @Test
    fun `foreground denied keeps the permission state and never starts`() = runTest {
        val harness = Harness(this, foregroundGranted = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        harness.controller.consumeStep()

        harness.controller.onForegroundPermissionResult(granted = false)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.NeedsForegroundPermission, state.status)
        assertEquals("a denial must not auto re-request", LocationSharingStep.None, state.step)
        assertEquals(0, harness.startCalls)
    }

    @Test
    fun `foreground granted moves to the background permission step`() = runTest {
        val harness = Harness(this, foregroundGranted = false, backgroundGranted = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        harness.controller.consumeStep()
        harness.foregroundGranted = true

        harness.controller.onForegroundPermissionResult(granted = true)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.NeedsBackgroundPermission, state.status)
        assertFalse(
            "continuous sharing must not be claimed before background is granted",
            state.status == LocationSharingStatus.Running,
        )
        assertEquals(LocationSharingStep.ExplainBackgroundPermission, state.step)
        assertEquals(0, harness.startCalls)
    }

    @Test
    fun `background not required skips the step and starts the service`() = runTest {
        val harness = Harness(
            this,
            foregroundGranted = true,
            backgroundGranted = true,
            backgroundRequired = false,
        )
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.Running, state.status)
        assertEquals(LocationSharingStep.None, state.step)
        assertEquals("the FGS must start when eligible", 1, harness.startCalls)
    }

    @Test
    fun `confirming the explanation is only a step clear`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        assertEquals(LocationSharingStep.ExplainBackgroundPermission, harness.controller.state.value.step)

        harness.controller.onBackgroundExplanationConfirmed()
        advanceUntilIdle()

        assertEquals(LocationSharingStep.None, harness.controller.state.value.step)
        assertEquals("the settings screen itself opens the system page", 0, harness.startCalls)
    }

    @Test
    fun `returning from settings without background stays needs-background without re-opening`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        harness.controller.consumeStep()

        harness.controller.onSettingsReturned()
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.NeedsBackgroundPermission, state.status)
        assertEquals("returning without the permission must not re-open the flow", LocationSharingStep.None, state.step)
        assertEquals(0, harness.startCalls)
    }

    @Test
    fun `returning from settings with background granted starts the service`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        harness.backgroundGranted = true

        harness.controller.onSettingsReturned()
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.Running, state.status)
        assertEquals(1, harness.startCalls)
    }

    @Test
    fun `toggle off stops the service and clears the desire`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        assertEquals(LocationSharingStatus.Running, harness.controller.state.value.status)

        harness.controller.onToggleDesired(false)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.Disabled, state.status)
        assertEquals(false, state.desired)
        assertEquals(1, harness.stopCalls)
    }

    @Test
    fun `permission revoked while running returns to needs-foreground`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        assertEquals(LocationSharingStatus.Running, harness.controller.state.value.status)

        harness.foregroundGranted = false
        harness.controller.onSettingsReturned()
        advanceUntilIdle()

        assertEquals(LocationSharingStatus.NeedsForegroundPermission, harness.controller.state.value.status)
    }

    @Test
    fun `background permission revoked while running returns to needs-background`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        assertEquals(LocationSharingStatus.Running, harness.controller.state.value.status)

        harness.backgroundGranted = false
        harness.controller.onSettingsReturned()
        advanceUntilIdle()

        assertEquals(LocationSharingStatus.NeedsBackgroundPermission, harness.controller.state.value.status)
    }

    @Test
    fun `location switch off surfaces LocationDisabled and blocks a start`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true, locationEnabled = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.LocationDisabled, state.status)
        assertEquals("an ineligible start must be blocked", 0, harness.startCalls)
    }

    @Test
    fun `location returning on with desire on starts the service`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true, locationEnabled = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        assertEquals(LocationSharingStatus.LocationDisabled, harness.controller.state.value.status)

        harness.locationEnabled = true
        harness.controller.onSettingsReturned()
        advanceUntilIdle()

        assertEquals(LocationSharingStatus.Running, harness.controller.state.value.status)
        assertEquals(1, harness.startCalls)
    }

    @Test
    fun `start failure surfaces Error`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true, startSucceeds = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()

        assertEquals(LocationSharingStatus.Error, harness.controller.state.value.status)
    }

    @Test
    fun `toggling off clears a start error`() = runTest {
        val harness = Harness(this, foregroundGranted = true, backgroundGranted = true, startSucceeds = false)
        harness.controller.onToggleDesired(true)
        advanceUntilIdle()
        assertEquals(LocationSharingStatus.Error, harness.controller.state.value.status)

        harness.controller.onToggleDesired(false)
        advanceUntilIdle()

        assertEquals(LocationSharingStatus.Disabled, harness.controller.state.value.status)
    }

    @Test
    fun `app restart with desire on restores the running service`() = runTest {
        val harness = Harness(this, initialDesired = true, foregroundGranted = true, backgroundGranted = true)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.Running, state.status)
        assertEquals(true, state.desired)
        assertEquals(1, harness.startCalls)
    }

    @Test
    fun `app restart with desire on but missing background does not claim running`() = runTest {
        val harness = Harness(this, initialDesired = true, foregroundGranted = true, backgroundGranted = false)
        advanceUntilIdle()

        val state = harness.controller.state.value
        assertEquals(LocationSharingStatus.NeedsBackgroundPermission, state.status)
        assertFalse(
            "restart must not silently drop into running",
            state.status == LocationSharingStatus.Running,
        )
        assertEquals(0, harness.startCalls)
    }

    @Test
    fun `resolver denies running when desire is off even if everything else holds`() {
        val status = LocationSharingStatusResolver.resolve(
            LocationSharingStatusResolver.Input(
                desired = false,
                foregroundGranted = true,
                backgroundRequired = true,
                backgroundGranted = true,
                locationEnabled = true,
                serviceActive = true,
                startFailed = false,
            ),
        )
        assertEquals(LocationSharingStatus.Disabled, status)
    }
}