package com.homelab.poc.setup

import com.homelab.poc.core.frigate.FrigateConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * V1.1 gate: the app shows the initial setup until the administrator completes
 * it; after that normal use never asks about infrastructure again.
 */
fun shouldShowSetup(setupComplete: Boolean): Boolean = !setupComplete

/**
 * Setup state machine for the V1.1 first-run flow. Pure orchestration: the
 * probe is injected so the machine is unit-testable without networking. The
 * production probe is [com.homelab.poc.controller.FrigateConnectionController.testConnection],
 * which runs the proven [com.homelab.poc.core.frigate.FrigateConnectionManager]
 * strategy unchanged, so no connectivity or enrollment logic is duplicated
 * here.
 *
 * The enrollment URL only ever lives in [State.EnrollmentRequired] for the
 * explicit administrative enrollment flow; nothing in this machine writes it
 * to logs.
 */
class SetupFlow(
    private val probe: suspend (url: String) -> FrigateConnection,
) {

    sealed interface State {
        /** Waiting for the administrator to enter the server address. */
        data object EnterConfig : State

        /** The connection probe is running. */
        data object Testing : State

        /** The probe succeeded; the setup can be finished. */
        data class Connected(val version: String) : State

        /** The embedded node needs enrollment; an admin action is required. */
        data class EnrollmentRequired(val authUrl: String?) : State

        /** The probe failed; the setup stays open for a retry. */
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.EnterConfig)
    val state: StateFlow<State> = _state.asStateFlow()

    /** URL that produced the current result, or null before the first test. */
    private var testedUrl: String? = null
    val lastTestedUrl: String? get() = testedUrl

    /**
     * Runs the probe for [url] and moves to the matching result state. A test
     * while one is already in flight is ignored, so a double tap cannot race
     * the probe.
     */
    suspend fun test(url: String) {
        if (_state.value is State.Testing) return
        testedUrl = url
        _state.value = State.Testing
        val result = try {
            probe(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FrigateConnection.Failed(e.message ?: e.toString())
        }
        _state.value = when (result) {
            is FrigateConnection.Connected -> State.Connected(result.version)
            is FrigateConnection.Failed ->
                if (result.authRequired) State.EnrollmentRequired(result.authUrl)
                else State.Failed(result.error)
        }
    }

    /** True only after a successful probe; the only gate to finishing setup. */
    fun canComplete(): Boolean = _state.value is State.Connected

    /** Returns to [State.EnterConfig], e.g. when the URL is edited after a test. */
    fun reset() {
        testedUrl = null
        _state.value = State.EnterConfig
    }
}
