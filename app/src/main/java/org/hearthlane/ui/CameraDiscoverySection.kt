package org.hearthlane.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.hearthlane.R
import org.hearthlane.controller.CameraDiscoveryController
import org.hearthlane.core.frigate.Camera
import org.hearthlane.core.frigate.CameraDiscoveryState

/**
 * V1.2 provisional camera list. This is the minimal UI needed to validate
 * camera discovery; the final Home cards and thumbnails arrive in V1.3.
 */
@Composable
fun CameraDiscoverySection(
    controller: CameraDiscoveryController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.camera_discovery_title),
            style = MaterialTheme.typography.titleLarge,
        )
        when (val current = state) {
            CameraDiscoveryState.Loading ->
                Text(
                    text = stringResource(R.string.camera_discovery_loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            CameraDiscoveryState.Empty ->
                Text(
                    text = stringResource(R.string.camera_discovery_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            is CameraDiscoveryState.Error -> DiscoveryError(current.message)
            is CameraDiscoveryState.Loaded ->
                CameraList(cameras = current.cameras)
        }
        TextButton(onClick = controller::refresh) {
            Text(stringResource(R.string.camera_discovery_refresh))
        }
    }
}

@Composable
private fun CameraList(cameras: List<Camera>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cameras.forEach { camera ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = camera.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = camera.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryError(message: String) {
    val clipboard = LocalClipboardManager.current
    Column {
        SelectionContainer {
            Text(
                text = stringResource(R.string.camera_discovery_error, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { clipboard.setText(AnnotatedString(message)) }) {
            Text(stringResource(R.string.copy_error_button))
        }
    }
}
