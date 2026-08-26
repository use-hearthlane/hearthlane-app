package com.homelab.poc.controller

import com.homelab.poc.core.frigate.Event
import com.homelab.poc.core.frigate.FrigateEventApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen state for the recent-events list of a single camera.
 *
 * [Loaded] is the only state that renders a list; it also carries the
 * pagination bookkeeping so appending a page never blanks the already loaded
 * events, and a pagination failure keeps them intact.
 */
sealed interface RecentEventsState {
    /** Initial page is being loaded. */
    data object Loading : RecentEventsState

    /**
     * At least one event is loaded.
     *
     * @param canLoadMore True when the last page returned a full page, so more
     *   events are likely available via [RecentEventsController.loadMore].
     * @param loadingMore True while an older page is being fetched.
     * @param loadMoreError Non-null when the last pagination attempt failed;
     *   the loaded events are preserved so the user can retry.
     */
    data class Loaded(
        val events: List<Event>,
        val canLoadMore: Boolean = false,
        val loadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : RecentEventsState

    /** The camera has no recent events. */
    data object Empty : RecentEventsState

    /** The initial load failed; the screen offers a retry. */
    data class Error(val message: String) : RecentEventsState
}

/**
 * Shared observable recent-events state for a single camera.
 *
 * Loads the most recent page on [loadInitial] (also used for refresh) and
 * appends older pages via [loadMore], which calls
 * [FrigateEventApi.olderEvents] with the `startTime` of the last loaded event
 * as the exclusive `before` cursor. The cursor is kept as a [Double] (the same
 * type as [Event.startTime]) so the sub-second precision the Frigate API needs
 * is preserved — it is never truncated, rounded or formatted.
 *
 * A pagination failure ([loadMore]) keeps the already loaded events and flags
 * [RecentEventsState.Loaded.loadMoreError] instead of discarding the list.
 */
class RecentEventsController(
    private val api: FrigateEventApi,
    private val cameraId: String,
    private val baseUrl: () -> String,
    private val limit: Int,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<RecentEventsState>(RecentEventsState.Loading)
    val state: StateFlow<RecentEventsState> = _state.asStateFlow()

    private var initialJob: Job? = null
    private var moreJob: Job? = null

    /** `startTime` of the last loaded event; the pagination cursor. */
    private var cursor: Double? = null

    /** Loads (or reloads) the most recent page. */
    fun loadInitial() {
        initialJob?.cancel()
        moreJob?.cancel()
        initialJob = scope.launch {
            _state.value = RecentEventsState.Loading
            val (events, error) = try {
                api.recentEvents(baseUrl().trim(), cameraId, limit) to null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null to (e.message ?: "could not load events")
            }
            when {
                error != null -> _state.value = RecentEventsState.Error(error)
                events.isNullOrEmpty() -> {
                    cursor = null
                    _state.value = RecentEventsState.Empty
                }
                else -> {
                    cursor = events.last().startTime
                    _state.value = RecentEventsState.Loaded(
                        events = events,
                        canLoadMore = events.size == limit,
                    )
                }
            }
        }
    }

    /** Re-fetches the most recent page (manual refresh). */
    fun refresh() = loadInitial()

    /**
     * Appends the next older page. No-op unless the list is currently
     * [RecentEventsState.Loaded], can load more and is not already loading.
     * On failure the loaded events are preserved and [RecentEventsState.Loaded.loadMoreError]
     * is set.
     */
    fun loadMore() {
        val current = _state.value as? RecentEventsState.Loaded ?: return
        if (!current.canLoadMore || current.loadingMore) return
        val before = cursor ?: return
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        moreJob?.cancel()
        moreJob = scope.launch {
            val (events, error) = try {
                api.olderEvents(baseUrl().trim(), cameraId, limit, before) to null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null to (e.message ?: "could not load more events")
            }
            val now = _state.value as? RecentEventsState.Loaded ?: return@launch
            if (error != null) {
                _state.value = now.copy(loadingMore = false, loadMoreError = error)
            } else if (events.isNullOrEmpty()) {
                _state.value = now.copy(canLoadMore = false, loadingMore = false)
            } else {
                this@RecentEventsController.cursor = events.last().startTime
                _state.value = now.copy(
                    events = now.events + events,
                    canLoadMore = events.size == limit,
                    loadingMore = false,
                )
            }
        }
    }
}
