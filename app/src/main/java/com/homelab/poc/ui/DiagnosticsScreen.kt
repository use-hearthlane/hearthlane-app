package com.homelab.poc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.homelab.poc.R
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.controller.PlaybackSnapshot
import com.homelab.poc.diagnostics.DiagnosticsReport
import com.homelab.poc.diagnostics.buildDiagnosticsSnapshot
import kotlinx.coroutines.delay

/**
 * V1.5 administrator observability screen. Shows every Section 10 diagnostics
 * field as the exact plain-text report produced by
 * [DiagnosticsReport.build], so the displayed text and the copied report are
 * the same sanitized content by construction. A retry-connection action is
 * provided as an admin convenience (the normal flow keeps its own Try again).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    controller: FrigateConnectionController,
    playbackSnapshot: PlaybackSnapshot,
    appVersion: String,
    onRetryConnection: () -> Unit,
    onBack: () -> Unit,
) {
    val connection by controller.connection.collectAsState()
    val connecting by controller.connecting.collectAsState()
    val transport by controller.lastProbedTransport.collectAsState()
    val transportSwitchCount by controller.transportSwitchCount.collectAsState()

    val report = remember(
        connection,
        connecting,
        transport,
        transportSwitchCount,
        playbackSnapshot,
        appVersion,
    ) {
        DiagnosticsReport.build(
            buildDiagnosticsSnapshot(
                connection = connection,
                connecting = connecting,
                transport = transport,
                transportSwitchCount = transportSwitchCount,
                playback = playbackSnapshot,
                appVersion = appVersion,
            ),
        )
    }

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPY_FEEDBACK_MS)
            copied = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SelectionContainer {
                Text(
                    text = report,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                clipboard.setText(AnnotatedString(report))
                copied = true
            }) {
                Text(
                    stringResource(
                        if (copied) R.string.diagnostics_copied
                        else R.string.diagnostics_copy_button,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetryConnection) {
                Text(stringResource(R.string.diagnostics_retry_connection))
            }
        }
    }
}

private const val COPY_FEEDBACK_MS = 2_000L
