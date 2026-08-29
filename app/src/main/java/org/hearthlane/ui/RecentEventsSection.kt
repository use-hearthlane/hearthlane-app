package org.hearthlane.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import org.hearthlane.R
import org.hearthlane.controller.RecentEventsController
import org.hearthlane.controller.RecentEventsState
import org.hearthlane.core.frigate.Event
import org.hearthlane.thumbnail.CameraThumbnailModelFactory
import java.time.LocalDate

/**
 * Reusable recent-events content for a single camera, exposed as items of an
 * existing scrollable list. The Camera Detail composes it into a single
 * [LazyColumn] (Live player + events), so the whole page scrolls in any
 * orientation without nested lazy scrolling.
 */
@Composable
internal fun RecentEventsSection(
    controller: RecentEventsController,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    onEventSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val dayGroupLabel = rememberDayGroupLabel()
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        recentEventsItems(
            state = state,
            thumbnailFactory = thumbnailFactory,
            snapshotImageLoader = snapshotImageLoader,
            baseUrl = baseUrl,
            controller = controller,
            onEventSelected = onEventSelected,
            dayGroupLabel = dayGroupLabel,
        )
    }
}

/**
 * Appends the recent-events content (header, day groups, event rows, pagination
 * footer or the loading/empty/error status) to an existing [LazyListScope]. Day
 * grouping keys are stable, so pagination appends into the last day without a
 * duplicated heading and recomposition never resets the scroll position.
 */
internal fun LazyListScope.recentEventsItems(
    state: RecentEventsState,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    controller: RecentEventsController,
    onEventSelected: (String) -> Unit,
    dayGroupLabel: (Double) -> String,
) {
    when (state) {
        is RecentEventsState.Loading ->
            item(key = "events_status") {
                EventListStatus(
                    text = stringResource(R.string.recent_events_loading),
                    progress = true,
                )
            }
        RecentEventsState.Empty ->
            item(key = "events_status") {
                EventListStatus(text = stringResource(R.string.recent_events_empty))
            }
        is RecentEventsState.Error ->
            item(key = "events_status") {
                EventListStatus(
                    text = stringResource(R.string.recent_events_error),
                    actionLabel = stringResource(R.string.retry_button),
                    onAction = controller::loadInitial,
                )
            }
        is RecentEventsState.Loaded -> {
            item(key = "events_header") {
                Text(
                    text = stringResource(R.string.recent_events_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            buildEventListRows(state.events) { _, startTime -> dayGroupLabel(startTime) }
                .forEach { row ->
                    when (row) {
                        is EventListRow.DayHeader -> item(key = "day-${row.date}") {
                            DayGroupHeader(row.label)
                        }
                        is EventListRow.EventRow -> item(key = "event-${row.event.id}") {
                            EventRow(
                                event = row.event,
                                thumbnailFactory = thumbnailFactory,
                                snapshotImageLoader = snapshotImageLoader,
                                baseUrl = baseUrl,
                                onEventSelected = onEventSelected,
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            if (state.canLoadMore) {
                item(key = "load_more") {
                    LoadMoreFooter(state, onLoadMore = controller::loadMore)
                }
            }
        }
    }
}

/** Remembered resolver of the "Today / Yesterday / date" day-group label. */
@Composable
internal fun rememberDayGroupLabel(): (Double) -> String {
    val todayLabel = stringResource(R.string.events_today)
    val yesterdayLabel = stringResource(R.string.events_yesterday)
    val dayPattern = stringResource(R.string.event_day_format)
    val locale = currentLocale()
    return remember(todayLabel, yesterdayLabel, dayPattern, locale) {
        { startTime ->
            val today = LocalDate.now()
            val date = EventFormat.dayKey(startTime)
            when (date) {
                today -> todayLabel
                today.minusDays(1) -> yesterdayLabel
                else -> EventFormat.dayLabel(startTime, pattern = dayPattern, locale = locale)
            }
        }
    }
}

/**
 * One row of the recent-events list: either a day-group heading or a single
 * event. Built from the already-ordered events so pagination appends rows to an
 * existing day without duplicating its heading.
 */
internal sealed interface EventListRow {
    data class DayHeader(val date: LocalDate, val label: String) : EventListRow
    data class EventRow(val event: Event) : EventListRow
}

/**
 * Groups an ordered event list into day headings + event rows. A heading is
 * emitted only when the day changes, so events from the same day (including
 * events appended by pagination) never produce a duplicate heading.
 */
internal fun buildEventListRows(
    events: List<Event>,
    dayLabel: (date: LocalDate, startTime: Double) -> String,
): List<EventListRow> {
    val rows = mutableListOf<EventListRow>()
    var lastDate: LocalDate? = null
    for (event in events) {
        val date = EventFormat.dayKey(event.startTime)
        if (date != lastDate) {
            rows.add(EventListRow.DayHeader(date, dayLabel(date, event.startTime)))
            lastDate = date
        }
        rows.add(EventListRow.EventRow(event))
    }
    return rows
}

@Composable
private fun DayGroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun EventRow(
    event: Event,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    onEventSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEventSelected(event.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventThumbnail(event, thumbnailFactory, snapshotImageLoader, baseUrl)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedFrigateObjectLabel(event.label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = event.endTime?.let { EventFormat.duration(it - event.startTime) }
                ?: stringResource(R.string.event_in_progress)
            Text(
                text = "${formattedEventTime(event.startTime)} · $duration",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (event.hasClip) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.event_view_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventThumbnail(
    event: Event,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
) {
    Box(modifier = Modifier.width(88.dp)) {
        if (event.hasSnapshot) {
            val model = thumbnailFactory.eventThumbnail(event.id, baseUrl)
            val description = localizedFrigateObjectLabel(event.label)
            SubcomposeAsyncImage(
                model = model,
                imageLoader = snapshotImageLoader,
                contentDescription = description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
                loading = { EventThumbnailPlaceholder() },
                error = { EventThumbnailPlaceholder() },
                success = {
                    Image(
                        painter = painter,
                        contentDescription = description,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        } else {
            EventThumbnailPlaceholder()
        }
    }
}

@Composable
private fun EventThumbnailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Compact list-item status (loading / empty / error) for the scrollable list. */
@Composable
private fun EventListStatus(
    text: String,
    progress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(
    state: RecentEventsState.Loaded,
    onLoadMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            state.loadingMore -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            state.loadMoreError != null -> {
                Text(
                    text = stringResource(R.string.load_more_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                TextButton(onClick = onLoadMore) {
                    Text(stringResource(R.string.retry_button))
                }
            }
            else -> TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.load_older_events))
            }
        }
    }
}