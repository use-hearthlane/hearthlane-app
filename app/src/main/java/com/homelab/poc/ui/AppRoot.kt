package com.homelab.poc.ui

import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.homelab.poc.R
import com.homelab.poc.controller.CameraDiscoveryController
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.core.frigate.Camera
import com.homelab.poc.core.frigate.CameraDiscoveryState
import com.homelab.poc.core.frigate.FrigateConfig
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.navigation.AppNavigation
import com.homelab.poc.navigation.Screen
import com.homelab.poc.settings.AppSettings
import com.homelab.poc.setup.shouldShowSetup
import com.homelab.poc.tailscale.TsnetGatewayImpl
import com.homelab.poc.thumbnail.CameraThumbnailModelFactory
import com.homelab.poc.thumbnail.FrigateSnapshotImageLoader

/**
 * V1 composition root: loads the persisted settings, owns the embedded
 * Tailscale gateway and the shared connection controller, and routes the
 * minimal navigation. The connection/session state lives here (not in a
 * screen) so later screens can reuse it without re-probing.
 *
 * V1.1 gate: until the administrator completes the initial setup the app shows
 * only the setup screen; after that, normal use goes straight to Home and never
 * asks about infrastructure again. Reopening the setup lives behind the
 * discreet Settings entry on Home.
 */
@Composable
fun AppRoot(
    stateDir: String,
    frigateConfig: FrigateConfig,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember {
        AppSettings.create(context, frigateConfig.localBaseUrl, scope)
    }

    val settingsReady by settings.ready.collectAsState()
    if (!settingsReady) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Loading settings…")
                }
            }
        }
        return
    }

    val setupComplete by settings.setupComplete.collectAsState()

    // The node hostname is an internal value derived from the persisted
    // per-installation suffix; it is never part of the normal configuration
    // flow and only changes when the settings store is cleared.
    val nodeSuffix by settings.nodeSuffix.collectAsState()
    val gateway = remember(nodeSuffix) {
        TsnetGatewayImpl(
            hostname = AppSettings.nodeHostname(nodeSuffix),
            stateDir = stateDir,
            connectTimeoutMs = frigateConfig.tailscaleConnectTimeoutMs,
        )
    }

    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val controller = remember(gateway) {
        FrigateConnectionController(
            gateway = gateway,
            settings = settings,
            connectivityManager = connectivityManager,
            scope = scope,
        )
    }

    val baseUrl by settings.baseUrl.collectAsState()
    val cameraDiscovery = remember(controller, gateway) {
        CameraDiscoveryController(
            connection = controller.connection,
            baseUrl = { baseUrl },
            scope = scope,
            discoverer = CameraDiscoveryController.productionDiscoverer(gateway),
        )
    }

    val snapshotImageLoader = remember {
        SingletonImageLoader.setSafe { FrigateSnapshotImageLoader.create(context) }
        FrigateSnapshotImageLoader.create(context)
    }
    val thumbnailFactory = remember(controller, gateway) {
        CameraThumbnailModelFactory(
            connection = controller.connection,
            gateway = gateway,
        )
    }

    val navigation = remember { AppNavigation() }
    var selectedCamera by remember { mutableStateOf<Camera?>(null) }

    // Observe shared state outside the individual branches so the Live branch
    // can react to connection/discovery changes without calling .value inside
    // composition.
    val discoveryState by cameraDiscovery.state.collectAsState()
    val connection by controller.connection.collectAsState()
    val connectAttempt by controller.connectAttempt.collectAsState()
    val networkTick by controller.networkTick.collectAsState()

    // V1.3 enrollment routing: when a network transition falls back to Tailscale
    // and the node is not yet enrolled, the shared connection exposes
    // FrigateConnection.Failed(authRequired=true). The app must route to the
    // existing V1.1 SetupScreen so the administrator can complete enrollment
    // instead of leaving the family-facing Home/Live view in a failed state.
    var autoEnrollmentNavigated by remember { mutableStateOf(false) }
    val failedConnection = connection as? FrigateConnection.Failed
    val authRequired = failedConnection?.authRequired == true
    val enrollmentAuthUrl = failedConnection?.authUrl
    LaunchedEffect(authRequired) {
        if (authRequired && !autoEnrollmentNavigated && navigation.current != Screen.Setup) {
            autoEnrollmentNavigated = true
            navigation.navigateTo(Screen.Setup)
        }
        if (!authRequired) {
            autoEnrollmentNavigated = false
        }
    }

    // The first-run gate has no back stack entry, so the system back action
    // only pops an explicitly pushed screen (Setup reopened from Home).
    BackHandler(enabled = navigation.current != Screen.Home) {
        navigation.navigateBack()
    }

    if (shouldShowSetup(setupComplete)) {
        // First run: the setup gates the app. The shared controller is
        // intentionally not started here: no network-driven re-probe runs
        // while the administrator configures the server. Setup only reuses the
        // probe path (FrigateConnectionManager) through controller.testConnection.
        SetupScreen(
            controller = controller,
            settings = settings,
            title = stringResource(R.string.setup_title),
            onFinished = { navigation.resetTo(Screen.Home) },
        )
        return
    }

    when (val screen = navigation.current) {
        Screen.Home -> {
            // The network-change listener and discovery observer only run during
            // normal use; leaving Home (opening setup) stops them until Home is
            // shown again.
            DisposableEffect(controller) {
                controller.start()
                cameraDiscovery.start()
                onDispose {
                    cameraDiscovery.stop()
                    controller.stop()
                }
            }
            HomeScreen(
                controller = controller,
                cameraDiscovery = cameraDiscovery,
                thumbnailFactory = thumbnailFactory,
                snapshotImageLoader = snapshotImageLoader,
                baseUrl = baseUrl,
                onOpenSettings = { navigation.navigateTo(Screen.Setup) },
                onCameraSelected = { camera ->
                    selectedCamera = camera
                    navigation.navigateTo(Screen.Live(camera.id))
                },
            )
        }
        Screen.Setup -> {
            SetupScreen(
                controller = controller,
                settings = settings,
                title = stringResource(R.string.settings_title),
                onFinished = { navigation.resetTo(Screen.Home) },
                onBack = { navigation.navigateBack() },
                enrollmentRequired = authRequired,
                authUrl = enrollmentAuthUrl,
            )
        }
        is Screen.Live -> {
            val camera = selectedCamera
                ?: (discoveryState as? CameraDiscoveryState.Loaded)
                    ?.cameras
                    ?.find { it.id == screen.cameraId }

            // The single decision point for the Live branch. It prevents the
            // enrollment-routing race: when auth is required we must NOT call
            // navigateBack() here; the global LaunchedEffect(authRequired) is
            // the only place that pushes Screen.Setup.
            when (liveDestination(connection, camera)) {
                LiveDestination.WaitForEnrollment -> {
                    ConnectingPlaceholder(text = stringResource(R.string.home_connecting_body))
                }
                LiveDestination.RenderLive -> {
                    // liveDestination guarantees camera != null and connection is Connected here.
                    LiveScreen(
                        cameraId = screen.cameraId,
                        displayName = camera!!.displayName,
                        baseUrl = baseUrl,
                        gateway = controller.gateway,
                        transport = (connection as FrigateConnection.Connected).transport,
                        connectAttempt = connectAttempt,
                        networkTick = networkTick,
                        onBack = { navigation.navigateBack() },
                    )
                }
                LiveDestination.GoBack -> {
                    // Camera not found or connection lost without pending enrollment.
                    navigation.navigateBack()
                }
            }
        }
    }
}

/**
 * Pure decision for the Live branch. Extracted so the navigation race between
 * the branch's navigateBack() and the global enrollment routing can be unit
 * tested without a Compose environment.
 */
internal sealed interface LiveDestination {
    data object RenderLive : LiveDestination
    data object WaitForEnrollment : LiveDestination
    data object GoBack : LiveDestination
}

internal fun liveDestination(
    connection: FrigateConnection?,
    camera: Camera?,
): LiveDestination = when {
    (connection as? FrigateConnection.Failed)?.authRequired == true -> LiveDestination.WaitForEnrollment
    camera != null && connection is FrigateConnection.Connected -> LiveDestination.RenderLive
    else -> LiveDestination.GoBack
}

@Composable
private fun ConnectingPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
