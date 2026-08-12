package com.homelab.poc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homelab.poc.R
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway

/**
 * V1.3 live-view destination. The route carries the selected [cameraId] so the
 * Home screen no longer relies on a "first global stream"; the actual per-camera
 * playback refactor is intentionally left to V1.4 to keep this milestone focused
 * on the Home UX. For now the proven [LiveView] spike continues to resolve the
 * first available go2rtc stream, while the shell already routes by camera id.
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
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName) },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.back_button))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.live_view_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            LiveView(
                baseUrl = baseUrl,
                gateway = gateway,
                transport = transport,
                connectAttempt = connectAttempt,
                networkTick = networkTick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
