package org.hearthlane.controller

import org.hearthlane.location.LocationPermissionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Product-facing status of the location-sharing capability. The toggle alone
 * only records the user's desire; the real system capacity is represented by
 * these states, so the UI can distinguish "I asked to share" from "sharing
 * actually works".
 */
enum class LocationSharingStatus {
    /** User desire is off. */
    Disabled,

    /** Desire on, but neither foreground location permission is granted. */
    NeedsForegroundPermission,

    /** Foreground granted, but the platform requires ACCESS_BACKGROUND_LOCATION
     *  and it is missing. Continuous (app-closed) sharing cannot run. */
    NeedsBackgroundPermission,

    /** Desire on and permissions granted, but the device location switch is off. */
    LocationDisabled,

    /** Desire on and everything eligible; the service has not started yet. */
    Ready,

    /** The publishing foreground service is active. */
    Running,

    /** A structural start failure (for example a SecurityException) happened. */
    Error,
}

/**
 * Pure derivation of [LocationSharingStatus] from the desired state, the real
 * permission/location state and the service activity. Extracted so the decision
 * is unit-testable without Android.
 */
object LocationSharingStatusResolver {

    data class Input(
        val desired: Boolean,
        val foregroundGranted: Boolean,
        val backgroundRequired: Boolean,
        val backgroundGranted: Boolean,
        val locationEnabled: Boolean,
        val serviceActive: Boolean,
        val startFailed: Boolean,
    )

    fun resolve(input: Input): LocationSharingStatus = when {
        !input.desired -> LocationSharingStatus.Disabled
        !input.foregroundGranted -> LocationSharingStatus.NeedsForegroundPermission
        input.backgroundRequired && !input.backgroundGranted -> LocationSharingStatus.NeedsBackgroundPermission
        !input.locationEnabled -> LocationSharingStatus.LocationDisabled
        input.startFailed -> LocationSharingStatus.Error
        input.serviceActive -> LocationSharingStatus.Running
        else -> LocationSharingStatus.Ready
    }
}

/**
 * Concrete action the Settings UI must perform to advance the permission flow.
 * The controller only decides; the screen executes the Android-specific action
 * (runtime permission request, in-app explanation dialog). Opening the system
 * settings page happens directly in the dialog's confirm action, so it never
 * races a state recompute.
 */
sealed interface LocationSharingStep {
    data object None : LocationSharingStep

    /** Ask for ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION. */
    data object RequestForegroundPermission : LocationSharingStep

    /** Show the in-app explanation before opening the system permission page. */
    data object ExplainBackgroundPermission : LocationSharingStep
}

/**
 * Orchestrates the location-sharing permission flow and the foreground-service
 * lifecycle from the desired state (the Settings toggle) and the real system
 * capacity.
 *
 * The flow follows the Android background-location rules: foreground location
 * first, then a separate background step only when the platform requires it
 * (API 30+). The service starts only when the system is fully eligible; a
 * denial never crashes and the toggle keeps showing the persisted desire while
 * the status caption explains what is missing.
 *
 * The controller holds no Activity: every Android interaction (permission
 * request, settings intent) is either injected as an executor ([startService] /
 * [stopService]) or emitted as a [LocationSharingStep] the screen performs and
 * reports back ([onForegroundPermissionResult], [onSettingsReturned]).
 */
class LocationSharingController(
    private val scope: CoroutineScope,
    private val sharingEnabled: StateFlow<Boolean>,
    private val setSharingEnabledAction: suspend (Boolean) -> Unit,
    private val permissionSnapshot: () -> LocationPermissionSnapshot,
    private val backgroundPermissionRequired: Boolean,
    private val startService: () -> Boolean,
    private val stopService: () -> Boolean,
) {

    data class UiState(
        val desired: Boolean = false,
        val status: LocationSharingStatus = LocationSharingStatus.Disabled,
        val step: LocationSharingStep = LocationSharingStep.None,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var serviceActive = false
    private var startFailed = false
    private var lastStatus: LocationSharingStatus = LocationSharingStatus.Disabled

    init {
        recompute(initiatedByUser = false)
    }

    /** Toggle change: persists the desire and drives the flow. */
    fun onToggleDesired(enabled: Boolean) {
        scope.launch {
            setSharingEnabledAction(enabled)
            recompute(initiatedByUser = true)
        }
    }

    /** Foreground permission request result. A denial keeps the Needs* status
     *  (no auto re-request); a grant continues into the background step. */
    fun onForegroundPermissionResult(granted: Boolean) {
        recompute(initiatedByUser = false)
    }

    /** The in-app explanation was accepted: the screen opens the system
     *  settings page and clears the step. */
    fun onBackgroundExplanationConfirmed() {
        consumeStep()
    }

    /** Revalidates the real state (called when the app resumes). */
    fun onSettingsReturned() {
        recompute(initiatedByUser = false)
    }

    /** The UI performed the emitted step. */
    fun consumeStep() {
        _state.value = _state.value.copy(step = LocationSharingStep.None)
    }

    /** A structural service-start failure (e.g. SecurityException). */
    fun onFgsStartFailed() {
        startFailed = true
        recompute(initiatedByUser = false)
    }

    private fun recompute(initiatedByUser: Boolean) {
        val desired = sharingEnabled.value
        if (!desired) startFailed = false
        val snap = permissionSnapshot()
        val previous = lastStatus

        // Drive the service lifecycle from a first status pass, then re-derive
        // so the emitted state reflects the post-action reality.
        val preliminary = resolve(desired, snap)
        when (preliminary) {
            LocationSharingStatus.Disabled -> if (serviceActive) {
                if (stopService()) serviceActive = false
            }
            LocationSharingStatus.Ready -> if (!serviceActive) {
                if (startService()) {
                    serviceActive = true
                    startFailed = false
                } else {
                    startFailed = true
                }
            }
            else -> Unit
        }

        val status = resolve(desired, snap)
        val step = when {
            status == LocationSharingStatus.NeedsForegroundPermission && initiatedByUser ->
                LocationSharingStep.RequestForegroundPermission
            status == LocationSharingStatus.NeedsBackgroundPermission &&
                (initiatedByUser || previous == LocationSharingStatus.NeedsForegroundPermission) ->
                LocationSharingStep.ExplainBackgroundPermission
            status == LocationSharingStatus.NeedsForegroundPermission ||
                status == LocationSharingStatus.NeedsBackgroundPermission ->
                // Preserve a pending step: a grant result and the following
                // ON_RESUME revalidation must not race away the dialog/request
                // before the screen executes it.
                _state.value.step
            else -> LocationSharingStep.None
        }
        lastStatus = status
        _state.value = UiState(desired = desired, status = status, step = step)
    }

    private fun resolve(desired: Boolean, snap: LocationPermissionSnapshot): LocationSharingStatus =
        LocationSharingStatusResolver.resolve(
            LocationSharingStatusResolver.Input(
                desired = desired,
                foregroundGranted = snap.foregroundGranted,
                backgroundRequired = backgroundPermissionRequired,
                backgroundGranted = snap.backgroundGranted,
                locationEnabled = snap.locationEnabled,
                serviceActive = serviceActive,
                startFailed = startFailed,
            ),
        )
}