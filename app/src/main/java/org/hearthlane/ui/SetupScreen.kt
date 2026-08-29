package org.hearthlane.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.hearthlane.R
import org.hearthlane.controller.FrigateConnectionController
import org.hearthlane.controller.LocationSharingController
import org.hearthlane.controller.LocationSharingStep
import org.hearthlane.core.connectivity.HearthlaneEndpointResolver
import org.hearthlane.diagnostics.DiagnosticsReport
import org.hearthlane.settings.AppSettings
import org.hearthlane.setup.SetupFlow
import org.hearthlane.setup.SetupLocationFlow
import org.hearthlane.setup.SetupLocationPhase
import org.hearthlane.setup.SetupLocationPhases
import kotlinx.coroutines.launch

/**
 * V1.1 administrator setup screen. Shown as the first-run gate and reopened
 * from Settings to edit the Hearthlane server; the two modes differ only in
 * [title] and whether an explicit [onBack] exists.
 *
 * The flow is intentionally narrow: enter a single Hearthlane server domain
 * (for example `hearthlane.omni.corp`), test it through the proven connection
 * strategy, complete the interactive Tailscale enrollment when the probe asks
 * for it, then save. The Frigate and Relay endpoints are derived from the
 * domain by [HearthlaneEndpointResolver]; the administrator never sees or
 * edits two URLs. Persistence happens only on finish, so abandoning the
 * reopened screen leaves the existing configuration intact. Messages stay
 * simple; the technical error text is only exposed through the copy action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    controller: FrigateConnectionController,
    settings: AppSettings,
    title: String,
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    enrollmentRequired: Boolean = false,
    authUrl: String? = null,
    syncNickname: suspend (String) -> Unit = {},
    locationSharingController: LocationSharingController,
) {
    val scope = rememberCoroutineScope()
    val flow = remember(controller, enrollmentRequired, authUrl) {
        SetupFlow(
            probe = controller::testConnection,
            enrollmentRequired = enrollmentRequired,
            authUrl = authUrl,
        )
    }
    val state by flow.state.collectAsState()
    val invalidDomainMessage = stringResource(R.string.setup_invalid_domain)

    // Location provisioning for the Setup: reuses the shared controller so
    // Settings and Setup agree on the exact same permission states.
    val locationFlow = remember(locationSharingController) {
        SetupLocationFlow(controller = locationSharingController)
    }
    val locationControllerState by locationFlow.controllerState.collectAsState()
    val locationAcknowledged by locationFlow.acknowledged.collectAsState()
    val locationPhase = SetupLocationPhases.phaseFor(locationControllerState.status, locationAcknowledged)

    // The field is an editable view over the persisted domain and is only
    // committed to settings when the administrator finishes the setup.
    val persistedBaseDomain by settings.baseDomain.collectAsState()
    var domain by remember { mutableStateOf(persistedBaseDomain) }
    LaunchedEffect(persistedBaseDomain) { domain = persistedBaseDomain }

    // Optional presentation-only nickname for this device. Never an identity;
    // the persisted node suffix remains the deviceId.
    val persistedNickname by settings.deviceNickname.collectAsState()
    var deviceNickname by remember { mutableStateOf(persistedNickname) }
    LaunchedEffect(persistedNickname) { deviceNickname = persistedNickname }

    fun onDomainChanged(new: String) {
        domain = new
        // A domain edited after a result was never the one that was tested:
        // discard the result so the administrator has to test again before
        // finishing (an untested value must never be saved).
        val tested = flow.lastTestedUrl
        if (tested != null && new.trim() != tested) flow.reset()
    }

    fun test() {
        val trimmed = domain.trim()
        if (trimmed.isBlank()) return
        val frigateUrl = HearthlaneEndpointResolver.frigateEndpoint(trimmed)
        if (frigateUrl == null) {
            flow.fail(invalidDomainMessage)
            return
        }
        scope.launch { flow.test(frigateUrl) }
    }

    fun finish() {
        if (!flow.canComplete()) return
        if (!SetupLocationPhases.canFinish(locationPhase)) return
        val normalized = HearthlaneEndpointResolver.normalizeBaseDomain(domain)
        if (normalized == null) {
            flow.fail(invalidDomainMessage)
            return
        }
        scope.launch {
            settings.setBaseDomain(normalized)
            settings.setSetupComplete(true)
            onFinished()
        }
        // The nickname sync is best-effort and never blocks finishing: a relay
        // outage keeps the nickname local and a later connection re-syncs it.
        scope.launch { syncNickname(deviceNickname.trim()) }
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_button),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            if (onBack == null) {
                Spacer(Modifier.height(24.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.setup_url_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = domain,
                onValueChange = ::onDomainChanged,
                label = { Text(stringResource(R.string.setup_domain_label)) },
                supportingText = { Text(stringResource(R.string.setup_domain_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_device_nickname_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = deviceNickname,
                onValueChange = { deviceNickname = it },
                label = { Text(stringResource(R.string.setup_device_nickname_field)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (deviceNickname.isBlank()) R.string.setup_device_nickname_optional
                            else R.string.setup_device_nickname_hint,
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            SetupLocationSection(flow = locationFlow, phase = locationPhase)
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.connection_state_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            when (val current = state) {
                SetupFlow.State.EnterConfig ->
                    Text(
                        text = stringResource(R.string.setup_enter_config),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                SetupFlow.State.Testing ->
                    Text(
                        text = stringResource(R.string.setup_testing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                is SetupFlow.State.Connected -> {
                    Text(
                        text = stringResource(R.string.setup_connected),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setup_connected_domain, domain.trim()),
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
            Spacer(Modifier.height(24.dp))
            if (state is SetupFlow.State.Connected) {
                Button(
                    onClick = ::finish,
                    enabled = SetupLocationPhases.canFinish(locationPhase),
                ) {
                    Text(stringResource(R.string.setup_finish_button))
                }
                Spacer(Modifier.height(8.dp))
            }
            val testing = state is SetupFlow.State.Testing
            OutlinedButton(
                onClick = ::test,
                enabled = domain.isNotBlank() && !testing,
            ) {
                Text(
                    stringResource(
                        if (state is SetupFlow.State.EnterConfig) R.string.setup_test_button
                        else R.string.setup_retry_button,
                    ),
                )
            }
            if (onBack != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.back_button))
                }
            }
        }
    }
}

/**
 * Location provisioning phase of the Setup, shown only when sharing is desired
 * (ON by default on new installs). It reuses [SetupLocationFlow] / the shared
 * [org.hearthlane.controller.LocationSharingController] to conduct the
 * foreground -> background -> location-services flow with the exact Android
 * rules (never both foreground and background in the same request). The user
 * may also finish with an explicit, communicated limitation.
 */
@Composable
private fun SetupLocationSection(flow: SetupLocationFlow, phase: SetupLocationPhase) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controllerState by flow.controllerState.collectAsState()
    var showBackgroundExplanation by remember { mutableStateOf(false) }

    if (phase == SetupLocationPhase.Hidden) return

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        flow.onForegroundPermissionResult(granted)
    }

    LaunchedEffect(controllerState.step) {
        when (controllerState.step) {
            LocationSharingStep.RequestForegroundPermission -> {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
                flow.consumeStep()
            }
            LocationSharingStep.ExplainBackgroundPermission -> {
                showBackgroundExplanation = true
                flow.consumeStep()
            }
            LocationSharingStep.None -> Unit
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) flow.onSettingsReturned()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text(
        text = stringResource(R.string.setup_location_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.setup_location_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = when (phase) {
            SetupLocationPhase.NeedsForegroundPermission ->
                stringResource(R.string.setup_location_needs_foreground)
            SetupLocationPhase.NeedsBackgroundPermission ->
                stringResource(R.string.setup_location_needs_background)
            SetupLocationPhase.LocationServicesDisabled ->
                stringResource(R.string.setup_location_services_off)
            SetupLocationPhase.Operational ->
                stringResource(R.string.setup_location_active)
            SetupLocationPhase.Limited ->
                stringResource(R.string.setup_location_limited)
            SetupLocationPhase.Hidden -> ""
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    when (phase) {
        SetupLocationPhase.NeedsForegroundPermission,
        SetupLocationPhase.NeedsBackgroundPermission,
        -> {
            Button(onClick = { flow.start() }) {
                Text(stringResource(R.string.setup_location_continue))
            }
        }
        SetupLocationPhase.LocationServicesDisabled -> {
            OutlinedButton(onClick = { openDeviceLocationSettings(context) }) {
                Text(stringResource(R.string.setup_location_open_settings))
            }
        }
        else -> Unit
    }
    if (SetupLocationPhases.canFinish(phase)) {
        if (phase != SetupLocationPhase.Operational) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { flow.acknowledgeLimitation() }) {
                Text(stringResource(R.string.setup_location_skip))
            }
        }
    }

    if (showBackgroundExplanation) {
        AlertDialog(
            onDismissRequest = { showBackgroundExplanation = false },
            title = { Text(stringResource(R.string.settings_location_background_title)) },
            text = { Text(stringResource(R.string.settings_location_background_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundExplanation = false
                    flow.onBackgroundExplanationConfirmed()
                    openBackgroundPermissionSettings(context)
                }) {
                    Text(stringResource(R.string.settings_location_background_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundExplanation = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
            Text(stringResource(R.string.open_auth_url_button))
        }
    } else {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_url_pending_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}