package com.homelab.poc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.homelab.poc.R
import com.homelab.poc.controller.CameraDiscoveryController
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.core.frigate.Camera
import com.homelab.poc.core.frigate.CameraDiscoveryState
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory

/**
 * V1.3 family-facing Home screen.
 *
 * The Home shows discovered cameras as a responsive grid of cards. Each card
 * displays the Frigate-friendly display name, a best-effort snapshot thumbnail,
 * and an unavailable state when the camera is not playable. Tapping a playable
 * card navigates to the live view for that camera id.
 *
 * Infrastructure details (transport, URL, Frigate version, raw errors) are not
 * shown in the normal flow; connection state is expressed in product language
 * only (Connecting / Live / Try again). The full discovery state is handled:
 * loading, loaded, empty, error, and individual unavailable cameras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    controller: FrigateConnectionController,
    cameraDiscovery: CameraDiscoveryController,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    baseUrl: String,
    onOpenSettings: () -> Unit,
    onCameraSelected: (Camera) -> Unit,
) {
    val connection by controller.connection.collectAsState()
    val connecting by controller.connecting.collectAsState()
    val discoveryState by cameraDiscovery.state.collectAsState()
    val refreshKey by cameraDiscovery.refreshKey.collectAsState()

    // Connect on first entry. Playback is not started here; the live view does
    // that after the user selects a camera.
    LaunchedEffect(Unit) { controller.connect(restartPlayback = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = cameraDiscovery::refresh) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.refresh_button),
                        )
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_button))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionHeader(
                connection = connection,
                connecting = connecting,
                modifier = Modifier.padding(top = 8.dp),
            )
            CameraGridSection(
                connection = connection,
                discoveryState = discoveryState,
                refreshKey = refreshKey,
                baseUrl = baseUrl,
                thumbnailFactory = thumbnailFactory,
                snapshotImageLoader = snapshotImageLoader,
                onCameraSelected = onCameraSelected,
                onRetry = { controller.connect(restartPlayback = false) },
                onRefresh = cameraDiscovery::refresh,
            )
        }
    }
}

@Composable
private fun ConnectionHeader(
    connection: FrigateConnection?,
    connecting: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = when {
        connecting || connection == null -> stringResource(R.string.home_connecting)
        connection is FrigateConnection.Connected -> stringResource(R.string.home_live)
        connection is FrigateConnection.Failed -> stringResource(R.string.home_try_again)
        else -> stringResource(R.string.home_connecting)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = when (connection) {
            is FrigateConnection.Connected -> MaterialTheme.colorScheme.primary
            is FrigateConnection.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier,
    )
}

@Composable
internal fun CameraGridSection(
    connection: FrigateConnection?,
    discoveryState: CameraDiscoveryState,
    refreshKey: Int,
    baseUrl: String,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    onCameraSelected: (Camera) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (val conn = connection) {
        is FrigateConnection.Connected -> when (val state = discoveryState) {
            CameraDiscoveryState.Loading -> LoadingState(stringResource(R.string.home_loading_cameras))
            CameraDiscoveryState.Empty -> EmptyState(stringResource(R.string.home_no_cameras))
            is CameraDiscoveryState.Error -> ErrorState(
                message = stringResource(R.string.home_cameras_error),
                onRetry = onRefresh,
            )
            is CameraDiscoveryState.Loaded -> CameraGrid(
                cameras = state.cameras,
                refreshKey = refreshKey,
                baseUrl = baseUrl,
                thumbnailFactory = thumbnailFactory,
                snapshotImageLoader = snapshotImageLoader,
                onCameraSelected = onCameraSelected,
            )
        }
        is FrigateConnection.Failed -> ErrorState(
            message = stringResource(R.string.home_connection_error),
            onRetry = onRetry,
        )
        else -> LoadingState(stringResource(R.string.home_connecting_body))
    }
}

@Composable
private fun CameraGrid(
    cameras: List<Camera>,
    refreshKey: Int,
    baseUrl: String,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    onCameraSelected: (Camera) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = cameras,
            key = { it.id },
        ) { camera ->
            CameraCard(
                camera = camera,
                refreshKey = refreshKey,
                baseUrl = baseUrl,
                thumbnailFactory = thumbnailFactory,
                snapshotImageLoader = snapshotImageLoader,
                onClick = { if (camera.playable) onCameraSelected(camera) },
            )
        }
    }
}

@Composable
internal fun CameraCard(
    camera: Camera,
    refreshKey: Int,
    baseUrl: String,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val model = thumbnailFactory.create(camera.id, baseUrl, refreshKey)
    val painter = rememberAsyncImagePainter(
        model = model,
        imageLoader = snapshotImageLoader,
    )
    val state by painter.state.collectAsState()

    Card(
        onClick = onClick,
        enabled = camera.playable,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail or best-effort placeholder.
            when (val s = state) {
                is AsyncImagePainter.State.Success ->
                    androidx.compose.foundation.Image(
                        painter = s.painter,
                        contentDescription = camera.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                else -> ThumbnailPlaceholder(camera.displayName)
            }

            if (!camera.playable) {
                UnavailableOverlay()
            }
        }

        Text(
            text = camera.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ThumbnailPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun UnavailableOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.camera_unavailable),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry_button))
            }
        }
    }
}
