package com.homelab.poc.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.homelab.poc.R
import com.homelab.poc.controller.FrigateConnectionController
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.settings.AppSettings
import kotlinx.coroutines.launch

/**
 * V1.0 shell: the POC Home screen, with its connection/session state moved into
 * [FrigateConnectionController] and the URL persisted through [AppSettings].
 * The visible behavior is unchanged from the POC: URL field, connect state,
 * failed/enrollment details, the LiveView spike and the Connect/Retry control.
 */
@Composable
fun HomeScreen(
    controller: FrigateConnectionController,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection by controller.connection.collectAsState()
    val connecting by controller.connecting.collectAsState()
    val connectAttempt by controller.connectAttempt.collectAsState()
    val networkTick by controller.networkTick.collectAsState()

    // The URL is persisted configuration (Preferences DataStore); the field is
    // an editable view over it, committed when the user taps Connect.
    val persistedBaseUrl by settings.baseUrl.collectAsState()
    var baseUrl by remember { mutableStateOf(persistedBaseUrl) }
    LaunchedEffect(persistedBaseUrl) { baseUrl = persistedBaseUrl }

    // Initial connect, matching the POC behavior of connecting on launch.
    LaunchedEffect(Unit) { controller.connect(restartPlayback = true) }

    fun saveAndConnect() {
        if (connecting) return
        scope.launch {
            settings.setBaseUrl(baseUrl)
            controller.connect(restartPlayback = true)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.phase_two_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.url_settings_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.frigate_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.connection_state_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = connectionStateLabel(connection, connecting),
                    style = MaterialTheme.typography.titleLarge,
                )
                (connection as? FrigateConnection.Connected)?.let { connected ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.frigate_version_value,
                            connected.version,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    LiveView(
                        baseUrl = persistedBaseUrl.trim(),
                        gateway = controller.gateway,
                        transport = connected.transport,
                        connectAttempt = connectAttempt,
                        networkTick = networkTick,
                    )
                }
                (connection as? FrigateConnection.Failed)?.let { failed ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.error_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val clipboard = LocalClipboardManager.current
                    SelectionContainer {
                        Text(
                            text = failed.error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(failed.error)) }) {
                        Text(stringResource(R.string.copy_error_button))
                    }
                    failed.authUrl?.let { authUrl ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.auth_url_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        SelectionContainer {
                            Text(
                                text = authUrl,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)),
                            )
                        }) {
                            Text(stringResource(R.string.open_auth_url_button))
                        }
                    }
                    if (failed.authRequired && failed.authUrl == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.auth_url_pending_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(onClick = { saveAndConnect() }, enabled = !connecting) {
                    Text(
                        stringResource(
                            if (connection is FrigateConnection.Failed) R.string.retry_button
                            else R.string.connect_button,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun connectionStateLabel(
    connection: FrigateConnection?,
    connecting: Boolean,
): String {
    if (connecting) return stringResource(R.string.connection_state_connecting)
    return when (val c = connection) {
        null -> stringResource(R.string.connection_state_idle)
        is FrigateConnection.Connected -> stringResource(
            R.string.connection_state_connected,
            when (c.transport) {
                TransportKind.LOCAL -> stringResource(R.string.transport_local)
                TransportKind.TAILSCALE -> stringResource(R.string.transport_tailscale)
            },
        )
        is FrigateConnection.Failed -> stringResource(R.string.connection_state_failed)
    }
}
