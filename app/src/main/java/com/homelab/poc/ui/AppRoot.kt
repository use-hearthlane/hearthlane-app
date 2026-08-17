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
import com.homelab.poc.BuildConfig
import com.homelab.poc.R
import com.homelab.poc.controller.CameraDiscoveryController
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.controller.PlaybackSnapshotStore
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
import com.homelab.poc.ui.theme.FamilyCameraTheme

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
        FamilyCameraTheme {
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

    // App-lifetime playback diagnostics accumulator for the V1.5 Diagnostics
    // screen: the live view records finished player sessions into it.
    val playbackSnapshotStore = remember { PlaybackSnapshotStore() }
    val playbackSnapshot by playbackSnapshotStore.snapshot.collectAsState()

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

    FamilyCameraTheme {
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
            return@FamilyCameraTheme
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
                    onOpenSettings = { navigation.navigateTo(Screen.Settings) },
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
            Screen.Settings -> {
                SettingsScreen(
                    nodeHostname = AppSettings.nodeHostname(nodeSuffix),
                    appVersion = BuildConfig.VERSION_NAME,
                    appBuild = BuildConfig.VERSION_CODE.toString(),
                    onOpenServerSettings = { navigation.navigateTo(Screen.Setup) },
                    onOpenDiagnostics = { navigation.navigateTo(Screen.Diagnostics) },
                    onResetTailscale = {
                        // Clears the node identity, then opens the server-settings
                        // screen so the administrator can test the connection and
                        // complete the interactive re-enrollment.
                        controller.resetTailscale()
                        navigation.navigateTo(Screen.Setup)
                    },
                    onBack = { navigation.navigateBack() },
                )
            }
            Screen.Diagnostics -> {
                // The diagnostics screen shows live connectivity state, so the
                // network-change listener stays active while it is shown (same
                // lifecycle as Home and the live view).
                DisposableEffect(controller) {
                    controller.start()
                    onDispose { controller.stop() }
                }
                DiagnosticsScreen(
                    controller = controller,
                    playbackSnapshot = playbackSnapshot,
                    appVersion = BuildConfig.VERSION_NAME,
                    onRetryConnection = { controller.connect(restartPlayback = false) },
                    onBack = { navigation.navigateBack() },
                )
            }
            is Screen.Live -> {
                // Keep the network-change listener alive while live video is shown.
                // When the Live screen became a separate destination (V1.3) the
                // listener was scoped to Home only, so turning on Wi-Fi while
                // watching would never re-probe: the connection stayed on
                // Tailscale even though the LAN had become reachable. The POC
                // embedded the live view in Home where the listener was always
                // active; this restores that behavior.
                DisposableEffect(controller) {
                    controller.start()
                    onDispose { controller.stop() }
                }
                val camera = resolveLiveCamera(screen.cameraId, selectedCamera, discoveryState)

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
                            playbackSnapshotStore = playbackSnapshotStore,
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
}

/**
 * Resolves the [Camera] model for the Live screen from the state already
 * available in the composition root. The camera selected on Home wins; when it
 * is not available (for example after a process restart that loses the
 * selected-camera memory), the already-discovered camera list is consulted by
 * exact camera id. No new global discovery is triggered here.
 *
 * The resolved camera carries the Frigate-friendly [Camera.displayName], which
 * the Live screen uses as its title.
 */
internal fun resolveLiveCamera(
    cameraId: String,
    selectedCamera: Camera?,
    discoveryState: CameraDiscoveryState?,
): Camera? = selectedCamera
    ?: (discoveryState as? CameraDiscoveryState.Loaded)
        ?.cameras
        ?.find { it.id == cameraId }

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
