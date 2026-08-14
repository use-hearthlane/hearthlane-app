package com.homelab.poc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homelab.poc.R
import com.homelab.poc.controller.PlaybackSnapshotStore
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.core.frigate.TsnetGateway

/**
 * V1.4 live-view destination. The route carries the selected [cameraId]; the
 * screen resolves and plays exactly that camera's go2rtc stream via
 * [LiveView]. The screen title is the Frigate [displayName] (friendly name,
 * camera key fallback) so the family-facing title never shows a raw camera id
 * when a friendly name exists.
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
    playbackSnapshotStore: PlaybackSnapshotStore? = null,
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
            LiveView(
                cameraId = cameraId,
                baseUrl = baseUrl,
                gateway = gateway,
                transport = transport,
                connectAttempt = connectAttempt,
                networkTick = networkTick,
                modifier = Modifier.fillMaxWidth(),
                playbackSnapshotStore = playbackSnapshotStore,
            )
        }
    }
}
