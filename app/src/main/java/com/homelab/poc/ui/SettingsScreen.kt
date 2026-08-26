package com.homelab.poc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homelab.poc.R
import com.homelab.poc.controller.ConnectionStatus
import com.homelab.poc.controller.SettingsController

/**
 * Product-oriented Settings screen.
 *
 * Settings is organized around what the user controls, not the infrastructure
 * behind it: the server address, remote access, diagnostics entry and app
 * information. Technical details (node hostname, transport, counters) live in
 * the Diagnostics screen, never here.
 *
 * The screen renders [SettingsController.state] and delegates navigation and
 * actions to the composition root; it holds no Frigate/Tailscale logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    controller: SettingsController,
    onOpenServerSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onReconfigureRemoteAccess: () -> Unit,
    onBack: () -> Unit,
) {
    val state by controller.state.collectAsState()
    var confirmReconfigure by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button),
                        )
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
            // -- Server section --
            SettingsSectionHeader(stringResource(R.string.settings_section_server))
            SettingsRow(
                title = stringResource(R.string.settings_server_address),
                value = state.serverUrl,
                onClick = onOpenServerSettings,
            )
            Text(
                text = connectionStatusText(state.connectionStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
            )

            SettingsDivider()

            // -- Remote access section --
            SettingsSectionHeader(stringResource(R.string.settings_section_access))
            SettingsRow(
                title = stringResource(R.string.settings_remote_access_reconfigure),
                onClick = { confirmReconfigure = true },
            )

            SettingsDivider()

            // -- Playback section --
            SettingsSectionHeader(stringResource(R.string.settings_section_playback))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_autoplay_event_clips),
                checked = state.autoPlayEventClips,
                onCheckedChange = controller::setAutoPlayEventClips,
            )

            SettingsDivider()

            // -- Diagnostics section (single clickable entry) --
            SettingsSectionLink(
                title = stringResource(R.string.diagnostics_title),
                onClick = onOpenDiagnostics,
            )

            SettingsDivider()

            // -- About section --
            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_app_version, state.appVersion),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_app_build, state.appBuild),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    if (confirmReconfigure) {
        AlertDialog(
            onDismissRequest = { confirmReconfigure = false },
            title = { Text(stringResource(R.string.settings_reconfigure_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reconfigure_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReconfigure = false
                    onReconfigureRemoteAccess()
                }) {
                    Text(stringResource(R.string.settings_reconfigure_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReconfigure = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/**
 * A section header that is itself the entry point (used by Diagnostics, where
 * a separate header and row would duplicate the same label).
 */
@Composable
private fun SettingsSectionLink(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun connectionStatusText(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.Connected -> stringResource(R.string.settings_connection_connected)
    ConnectionStatus.Connecting -> stringResource(R.string.settings_connection_connecting)
    ConnectionStatus.Unavailable -> stringResource(R.string.settings_connection_unavailable)
}
