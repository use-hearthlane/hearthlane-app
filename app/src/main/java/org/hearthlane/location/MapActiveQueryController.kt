package org.hearthlane.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The map-active update loop: while [start] is active, one immediate query
 * runs and then repeats every [intervalMs]. [stop] cancels the loop and any
 * in-flight query (best-effort; no further query ever starts). The previous
 * result stays in memory so a transient failure does not blank the map.
 */
class MapActiveQueryController(
    private val query: suspend () -> MapQueryResult,
    private val intervalMs: () -> Long,
    private val scope: CoroutineScope,
) {

    data class State(
        val running: Boolean = false,
        val updateCount: Int = 0,
        val markers: List<DeviceMarker> = emptyList(),
        /** Null until the first cycle completes; then [MapQueryStatus]. */
        val status: MapQueryStatus? = null,
        val lastError: String? = null,
        val lastQueryAtMs: Long? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State(running = true)
            while (isActive) {
                val result = query()
                if (!isActive) break
                _state.update {
                    // Only a successful cycle replaces the markers: a transient
                    // failure must never blank the map the user is looking at.
                    it.copy(
                        updateCount = it.updateCount + 1,
                        markers = if (result.status == MapQueryStatus.OK) result.markers else it.markers,
                        status = result.status,
                        lastError = result.errorMessage,
                        lastQueryAtMs = System.currentTimeMillis(),
                    )
                }
                delay(intervalMs())
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }
}