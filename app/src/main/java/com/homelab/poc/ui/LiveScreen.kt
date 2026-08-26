package com.homelab.poc.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.ImageLoader
import com.homelab.poc.R
import com.homelab.poc.controller.PlaybackSnapshotStore
import com.homelab.poc.controller.RecentEventsController
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory

/**
 * The camera screen: the live player on top and, in portrait, the camera's
 * recent-events list directly below it (the list is content of this screen, not
 * an intermediate destination). The toolbar only carries the screen's
 * navigation (back); no playback or event actions are added.
 *
 * In landscape and fullscreen the video dominates the screen (the list is a
 * portrait concern). The [RecentEventsController] is owned by the caller so it
 * survives recomposition and follows the camera context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveScreen(
    cameraId: String,
    displayName: String,
    baseUrl: String,
    gateway: TsnetGateway,
    transport: TransportKind,
    connectAttempt: Int,
    networkTick: Int,
    eventsController: RecentEventsController,
    thumbnailFactory: CameraThumbnailModelFactory,
    snapshotImageLoader: ImageLoader,
    playbackSnapshotStore: PlaybackSnapshotStore? = null,
    onBack: () -> Unit,
    onEventSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    var fullscreen by remember { mutableStateOf(false) }

    // Back handler: exit fullscreen first, then navigate back.
    BackHandler(enabled = fullscreen) {
        fullscreen = false
    }

    // System bars and orientation management.
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let {
            WindowInsetsControllerCompat(it, it.decorView)
        }
        if (fullscreen) {
            // Hide system bars for immersive fullscreen.
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Request landscape for fullscreen (activity has configChanges declared).
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // Let content draw behind system bars.
            WindowCompat.setDecorFitsSystemWindows(window!!, false)
        } else {
            // Restore system bars.
            controller?.show(WindowInsetsCompat.Type.systemBars())
            // Restore unspecified orientation (follows sensor).
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window!!, true)
        }
        onDispose {
            // Restore on disposal (e.g., back navigation).
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(displayName) },
                    colors = if (isLandscape) {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        )
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_button),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().padding(innerPadding)
        }
        val toggleFullscreen: () -> Unit = { fullscreen = !fullscreen }
        if (fullscreen || isLandscape) {
            // Video dominates: the list is a portrait concern.
            Box(modifier = contentModifier) {
                LiveView(
                    cameraId = cameraId,
                    baseUrl = baseUrl,
                    gateway = gateway,
                    transport = transport,
                    connectAttempt = connectAttempt,
                    networkTick = networkTick,
                    modifier = Modifier.fillMaxSize(),
                    playbackSnapshotStore = playbackSnapshotStore,
                    fullscreen = fullscreen,
                    onToggleFullscreen = toggleFullscreen,
                )
            }
        } else {
            Column(modifier = contentModifier) {
                LiveView(
                    cameraId = cameraId,
                    baseUrl = baseUrl,
                    gateway = gateway,
                    transport = transport,
                    connectAttempt = connectAttempt,
                    networkTick = networkTick,
                    modifier = Modifier.fillMaxWidth(),
                    playbackSnapshotStore = playbackSnapshotStore,
                    fullscreen = false,
                    onToggleFullscreen = toggleFullscreen,
                )
                RecentEventsSection(
                    controller = eventsController,
                    thumbnailFactory = thumbnailFactory,
                    snapshotImageLoader = snapshotImageLoader,
                    baseUrl = baseUrl,
                    onEventSelected = onEventSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .weight(1f),
                )
            }
        }
    }
}