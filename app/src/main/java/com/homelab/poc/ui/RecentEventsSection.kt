package com.homelab.poc.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.homelab.poc.R
import com.homelab.poc.controller.RecentEventsController
import com.homelab.poc.controller.RecentEventsState
import com.homelab.poc.core.frigate.Event
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory

/**
 * Reusable recent-events content for a single camera: a "Recent Events" header
 * followed by the list (thumbnail, detected object, time, duration or "in
 * progress") with the existing pagination footer. It has no toolbar of its own
 * and is meant to be composed directly into the camera screen below the live
 * player. All thumbnails travel through the same transport-scoped getter as the
 * rest of the app.
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

    Column(modifier) {
        Text(
            text = stringResource(R.string.recent_events_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                RecentEventsState.Loading ->
                    CenteredMessage(stringResource(R.string.recent_events_loading), progress = true)
                RecentEventsState.Empty ->
                    CenteredMessage(stringResource(R.string.recent_events_empty))
                is RecentEventsState.Error ->
                    CenteredMessage(
                        text = stringResource(R.string.recent_events_error),
                        actionLabel = stringResource(R.string.retry_button),
                        onAction = controller::loadInitial,
                    )
                is RecentEventsState.Loaded ->
                    EventList(
                        state = current,
                        thumbnailFactory = thumbnailFactory,
                        snapshotImageLoader = snapshotImageLoader,
                        baseUrl = baseUrl,
                        controller = controller,
                        onEventSelected = onEventSelected,
                    )
            }
        }
    }
}

@Composable
private fun EventList(
    state: RecentEventsState.Loaded,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    controller: RecentEventsController,
    onEventSelected: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.events, key = { it.id }) { event ->
            EventRow(
                event = event,
                thumbnailFactory = thumbnailFactory,
                snapshotImageLoader = snapshotImageLoader,
                baseUrl = baseUrl,
                onEventSelected = onEventSelected,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.canLoadMore) {
            item(key = "load_more") {
                LoadMoreFooter(state, onLoadMore = controller::loadMore)
            }
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Text(
                text = formattedEventTime(event.startTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = event.endTime?.let { EventFormat.duration(it - event.startTime) }
                    ?: stringResource(R.string.event_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (event.hasClip) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.event_playback_available),
                tint = MaterialTheme.colorScheme.primary,
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