package org.hearthlane.setup

import org.hearthlane.controller.LocationSharingController
import org.hearthlane.controller.LocationSharingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Setup-specific presentation phase for location provisioning. The permission
 * decisions live entirely in the shared [LocationSharingController]; this only
 * maps its status (plus the user's acknowledgement flag) to what the Setup
 * screen shows and whether it may finish.
 */
enum class SetupLocationPhase {
    /** Sharing is explicitly off; the Setup does not force a permission flow. */
    Hidden,

    /** Foreground location permission is missing. */
    NeedsForegroundPermission,

    /** Background location permission is missing (Android 11+). */
    NeedsBackgroundPermission,

    /** The device location switch is off. */
    LocationServicesDisabled,

    /** Fully eligible: permissions granted and location enabled. */
    Operational,

    /** The user chose to continue despite a missing permission/location. */
    Limited,
}

/**
 * Pure mapping between the shared controller status and the Setup phase. No
 * permission rule is duplicated here; the controller stays the single source
 * of truth.
 */
object SetupLocationPhases {

    fun phaseFor(status: LocationSharingStatus, acknowledged: Boolean): SetupLocationPhase = when {
        status == LocationSharingStatus.Disabled -> SetupLocationPhase.Hidden
        status == LocationSharingStatus.Error -> SetupLocationPhase.Limited
        acknowledged && isLimitable(status) -> SetupLocationPhase.Limited
        status == LocationSharingStatus.NeedsForegroundPermission ->
            SetupLocationPhase.NeedsForegroundPermission
        status == LocationSharingStatus.NeedsBackgroundPermission ->
            SetupLocationPhase.NeedsBackgroundPermission
        status == LocationSharingStatus.LocationDisabled ->
            SetupLocationPhase.LocationServicesDisabled
        else -> SetupLocationPhase.Operational
    }

    /** Whether the Setup may be finished for a given [phase]. */
    fun canFinish(phase: SetupLocationPhase): Boolean = when (phase) {
        SetupLocationPhase.Hidden,
        SetupLocationPhase.Operational,
        SetupLocationPhase.Limited,
        -> true
        SetupLocationPhase.NeedsForegroundPermission,
        SetupLocationPhase.NeedsBackgroundPermission,
        SetupLocationPhase.LocationServicesDisabled,
        -> false
    }

    private fun isLimitable(status: LocationSharingStatus): Boolean = when (status) {
        LocationSharingStatus.NeedsForegroundPermission,
        LocationSharingStatus.NeedsBackgroundPermission,
        LocationSharingStatus.LocationDisabled,
        -> true
        else -> false
    }
}

/**
 * Setup orchestrator over the shared [LocationSharingController]. Every
 * Android permission step (runtime request, background explanation, system
 * settings, revalidation) is delegated to the controller, so Settings and Setup
 * agree on the exact same states. The only Setup-owned state is whether the
 * user acknowledged finishing with a limitation.
 */
class SetupLocationFlow(
    val controller: LocationSharingController,
) {

    /** Live controller state; the Setup screen collects it like Settings does. */
    val controllerState: StateFlow<LocationSharingController.UiState> = controller.state

    private val _acknowledged = MutableStateFlow(false)
    val acknowledged: StateFlow<Boolean> = _acknowledged.asStateFlow()

    /** Initiates or continues the permission flow (user taps Continue). */
    fun start() {
        _acknowledged.value = false
        controller.onToggleDesired(true)
    }

    /** Foreground permission request result. */
    fun onForegroundPermissionResult(granted: Boolean) {
        controller.onForegroundPermissionResult(granted)
    }

    /** The in-app background explanation was accepted; the screen opens the
     *  system settings page. */
    fun onBackgroundExplanationConfirmed() {
        controller.onBackgroundExplanationConfirmed()
    }

    /** Revalidates permissions/location after returning from the system. */
    fun onSettingsReturned() {
        controller.onSettingsReturned()
    }

    /** The screen performed the emitted step. */
    fun consumeStep() {
        controller.consumeStep()
    }

    /** The user chose to finish despite a missing permission/location. */
    fun acknowledgeLimitation() {
        _acknowledged.value = true
    }
}