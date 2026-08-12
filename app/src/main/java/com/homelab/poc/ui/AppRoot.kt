package com.homelab.poc.ui

import android.net.ConnectivityManager
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
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.core.frigate.FrigateConfig
import com.homelab.poc.navigation.AppNavigation
import com.homelab.poc.navigation.Screen
import com.homelab.poc.settings.AppSettings
import com.homelab.poc.tailscale.TsnetGatewayImpl

/**
 * V1 composition root: loads the persisted settings, owns the embedded
 * Tailscale gateway and the shared connection controller, and routes the
 * minimal navigation. The connection/session state lives here (not in a
 * screen) so later screens can reuse it without re-probing.
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
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }

    val navigation = remember { AppNavigation() }
    when (val screen = navigation.current) {
        Screen.Home -> HomeScreen(controller = controller, settings = settings)
    }
}
