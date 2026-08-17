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
import com.homelab.poc.diagnostics.DiagnosticsReport
import com.homelab.poc.settings.AppSettings
import com.homelab.poc.setup.SetupFlow
import kotlinx.coroutines.launch

/**
 * V1.1 administrator setup screen. Shown as the first-run gate and reopened
 * from Settings to edit the server URL; the two modes differ only in [title]
 * and whether an explicit [onBack] exists.
 *
 * The flow is intentionally narrow: enter the Frigate URL, test it through the
 * proven connection strategy, complete the interactive Tailscale enrollment
 * when the probe asks for it, then save. Persistence happens only on finish,
 * so abandoning the reopened screen leaves the existing configuration intact.
 * Messages stay simple; the technical error text is only exposed through the
 * copy action (the full Diagnostics screen arrives in a later milestone).
 */
@Composable
fun SetupScreen(
    controller: FrigateConnectionController,
    settings: AppSettings,
    title: String,
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    enrollmentRequired: Boolean = false,
    authUrl: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(controller, enrollmentRequired, authUrl) {
        SetupFlow(
            probe = controller::testConnection,
            enrollmentRequired = enrollmentRequired,
            authUrl = authUrl,
        )
    }
    val state by flow.state.collectAsState()

    // The field is an editable view over the persisted URL and is only
    // committed to settings when the administrator finishes the setup.
    val persistedBaseUrl by settings.baseUrl.collectAsState()
    var url by remember { mutableStateOf(persistedBaseUrl) }
    LaunchedEffect(persistedBaseUrl) { url = persistedBaseUrl }

    fun onUrlChanged(new: String) {
        url = new
        // A URL edited after a result was never the one that was tested:
        // discard the result so the administrator has to test again before
        // finishing (an untested value must never be saved).
        val tested = flow.lastTestedUrl
        if (tested != null && new.trim() != tested) flow.reset()
    }

    fun test() {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        scope.launch { flow.test(trimmed) }
    }

    fun finish() {
        if (!flow.canComplete()) return
        scope.launch {
            settings.setBaseUrl(url.trim())
            settings.setSetupComplete(true)
            onFinished()
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
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.setup_url_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = ::onUrlChanged,
                    label = { Text(stringResource(R.string.frigate_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.connection_state_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                when (val current = state) {
                    SetupFlow.State.EnterConfig ->
                        Text(
                            text = stringResource(R.string.setup_enter_config),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    SetupFlow.State.Testing ->
                        Text(
                            text = stringResource(R.string.setup_testing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    is SetupFlow.State.Connected -> {
                        Text(
                            text = stringResource(R.string.setup_connected),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.frigate_version_value, current.version),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    is SetupFlow.State.EnrollmentRequired ->
                        EnrollmentSection(authUrl = current.authUrl)
                    is SetupFlow.State.Failed -> {
                        Text(
                            text = stringResource(R.string.setup_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val clipboard = LocalClipboardManager.current
                        TextButton(onClick = { clipboard.setText(AnnotatedString(DiagnosticsReport.sanitize(current.message))) }) {
                            Text(stringResource(R.string.copy_error_button))
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                if (state is SetupFlow.State.Connected) {
                    Button(onClick = ::finish) {
                        Text(stringResource(R.string.setup_finish_button))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val testing = state is SetupFlow.State.Testing
                Button(
                    onClick = ::test,
                    enabled = url.isNotBlank() && !testing,
                ) {
                    Text(
                        stringResource(
                            if (state is SetupFlow.State.EnterConfig) R.string.setup_test_button
                            else R.string.setup_retry_button,
                        ),
                    )
                }
                onBack?.let {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = it) {
                        Text(stringResource(R.string.back_button))
                    }
                }
            }
        }
    }
}

/**
 * The explicit administrative enrollment action: the one-time login URL is
 * shown and opened here and never written to logcat. The URL is transient and
 * is not persisted anywhere.
 */
@Composable
private fun EnrollmentSection(authUrl: String?) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.setup_enrollment_hint),
        style = MaterialTheme.typography.titleLarge,
    )
    val url = authUrl
    if (url != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_url_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectionContainer {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
            Text(stringResource(R.string.open_auth_url_button))
        }
    } else {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_url_pending_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
