package com.homelab.poc.ui

import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.homelab.poc.R
import com.homelab.poc.controller.CameraDiscoveryController
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.core.frigate.FrigateConfig
import com.homelab.poc.navigation.AppNavigation
import com.homelab.poc.navigation.Screen
import com.homelab.poc.settings.AppSettings
import com.homelab.poc.setup.shouldShowSetup
import com.homelab.poc.tailscale.TsnetGatewayImpl

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

    val cameraDiscovery = remember(controller, gateway) {
        CameraDiscoveryController(
            connection = controller.connection,
            baseUrl = { settings.baseUrl.value },
            scope = scope,
            discoverer = CameraDiscoveryController.productionDiscoverer(gateway),
        )
    }

    val navigation = remember { AppNavigation() }
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
                settings = settings,
                onOpenSettings = { navigation.navigateTo(Screen.Setup) },
            )
        }
        Screen.Setup -> {
            SetupScreen(
                controller = controller,
                settings = settings,
                title = stringResource(R.string.settings_title),
                onFinished = { navigation.resetTo(Screen.Home) },
                onBack = { navigation.navigateBack() },
            )
        }
    }
}
