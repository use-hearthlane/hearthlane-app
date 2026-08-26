package com.homelab.poc.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.homelab.poc.R
import com.homelab.poc.controller.EventDetailController
import com.homelab.poc.controller.EventDetailState
import com.homelab.poc.core.frigate.Event
import com.homelab.poc.core.playback.EventPlayer
import com.homelab.poc.core.playback.PlaybackStatus
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory

/**
 * Family-facing detail view for a single camera event.
 *
 * Reached from the camera screen's recent-events list. An event with a clip
 * starts playing immediately in the media area (there is no Play button and no
 * thumbnail is loaded for it); a playback failure shows a friendly message with
 * Retry in place. Events without a clip fall back to the snapshot. The event
 * metadata stays visible below the media area.
 *
 * The screen renders [EventDetailController.state] and
 * [EventDetailController.playbackState]; it never fetches the event, never
 * creates players and never reasons about the active transport.
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailScreen(
    controller: EventDetailController,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    cameraDisplayName: String?,
    onBack: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val playbackStatus by controller.playbackState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.event_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val current = state) {
                EventDetailState.Loading ->
                    CenteredMessage(stringResource(R.string.event_detail_loading), progress = true)
                EventDetailState.NotFound ->
                    CenteredMessage(stringResource(R.string.event_detail_not_found))
                is EventDetailState.Error ->
                    CenteredMessage(
                        text = stringResource(R.string.event_detail_error),
                        actionLabel = stringResource(R.string.retry_button),
                        onAction = controller::load,
                    )
                is EventDetailState.Loaded ->
                    EventDetailContent(
                        event = current.event,
                        playbackStatus = playbackStatus,
                        player = controller.player,
                        thumbnailFactory = thumbnailFactory,
                        snapshotImageLoader = snapshotImageLoader,
                        baseUrl = baseUrl,
                        cameraDisplayName = cameraDisplayName,
                        onPlay = controller::play,
                    )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun EventDetailContent(
    event: Event,
    playbackStatus: PlaybackStatus,
    player: EventPlayer,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    cameraDisplayName: String?,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EventMediaArea(
            event = event,
            playbackStatus = playbackStatus,
            player = player,
            thumbnailFactory = thumbnailFactory,
            snapshotImageLoader = snapshotImageLoader,
            baseUrl = baseUrl,
            onPlay = onPlay,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = localizedFrigateObjectLabel(event.label),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        cameraDisplayName?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        InfoRow(
            label = stringResource(R.string.event_detail_time),
            value = formattedEventDateTime(event.startTime),
        )
        InfoRow(
            label = stringResource(R.string.event_detail_duration),
            value = event.endTime?.let { EventFormat.duration(it - event.startTime) }
                ?: stringResource(R.string.event_in_progress),
        )
        if (event.zones.isNotEmpty()) {
            InfoRow(
                label = stringResource(R.string.event_detail_zones),
                value = event.zones.joinToString(", "),
            )
        }
    }
}

/**
 * The media area of the event detail. A clip event plays automatically in the
 * [PlayerView] (no thumbnail); playback failures show a friendly message with
 * Retry in place. Events without a clip fall back to the snapshot.
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun EventMediaArea(
    event: Event,
    playbackStatus: PlaybackStatus,
    player: EventPlayer,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    onPlay: () -> Unit,
) {
    when {
        !event.hasClip ->
            EventSnapshot(event, thumbnailFactory, snapshotImageLoader, baseUrl)

        playbackStatus is PlaybackStatus.Error ->
            CenteredMessage(
                text = eventPlaybackErrorMessage(playbackStatus),
                actionLabel = stringResource(R.string.retry_button),
                onAction = onPlay,
            )

        // Playback not started yet (auto-play disabled): show the snapshot as a
        // tappable content affordance that starts playback on tap.
        playbackStatus == PlaybackStatus.Idle ->
            EventSnapshot(
                event = event,
                thumbnailFactory = thumbnailFactory,
                snapshotImageLoader = snapshotImageLoader,
                baseUrl = baseUrl,
                playable = true,
                onPlay = onPlay,
            )

        else ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setPlayer(player.player)
                    }
                },
                update = { view -> view.player = player.player },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .testTag("event_player_view"),
            )
    }
}

@Composable
private fun eventPlaybackErrorMessage(status: PlaybackStatus.Error): String = when (status.statusCode) {
    404 -> stringResource(R.string.event_playback_not_found)
    null -> stringResource(R.string.event_playback_network_error)
    else -> stringResource(R.string.event_playback_server_error)
}

@Composable
private fun EventSnapshot(
    event: Event,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    playable: Boolean = false,
    onPlay: () -> Unit = {},
) {
    val playLabel = stringResource(R.string.event_detail_play_clip)
    val boxModifier = if (playable) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable(onClick = onPlay)
            .semantics { contentDescription = playLabel }
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    }
    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (event.hasSnapshot) {
            val model = thumbnailFactory.eventSnapshot(event.id, baseUrl)
            val description = if (playable) null else localizedFrigateObjectLabel(event.label)
            SubcomposeAsyncImage(
                model = model,
                imageLoader = snapshotImageLoader,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { SnapshotPlaceholder() },
                error = { SnapshotPlaceholder() },
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
            SnapshotPlaceholder()
        }
        if (playable) {
            PlayOverlay()
        }
    }
}

@Composable
private fun PlayOverlay() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun SnapshotPlaceholder() {
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
