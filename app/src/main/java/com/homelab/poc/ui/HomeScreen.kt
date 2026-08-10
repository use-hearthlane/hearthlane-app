package com.homelab.poc.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homelab.poc.R
import com.homelab.poc.core.connectivity.ConnectivityState
import com.homelab.poc.core.connectivity.ConnectivityStatus
import com.homelab.poc.tailscale.TailscaleBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PocCamera"

@Composable
fun HomeScreen(hostname: String, stateDir: String) {
    var status by remember { mutableStateOf(ConnectivityStatus(ConnectivityState.DISCONNECTED)) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var lastState by remember { mutableStateOf<ConnectivityState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val fresh = withContext(Dispatchers.IO) { TailscaleBridge.status() }
            if (fresh.state != lastState) {
                Log.i(
                    TAG,
                    "connectivity state: ${fresh.state}" +
                        (fresh.authUrl?.let { " authUrl=$it" } ?: "") +
                        (fresh.error?.let { " error=${it}" } ?: ""),
                )
                lastState = fresh.state
            }
            status = fresh
            delay(2_000)
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
                    text = stringResource(R.string.phase_one_subtitle, hostname),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.connection_state_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.connection_state_value, status.state.name),
                    style = MaterialTheme.typography.titleLarge,
                )
                status.authUrl?.let { authUrl ->
                    Spacer(Modifier.height(16.dp))
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
                }
                lastError?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.error_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        lastError = null
                        Log.i(TAG, "start requested")
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    TailscaleBridge.start(hostname, authKey = "", stateDir)
                                }
                            }.onSuccess {
                                Log.i(TAG, "start succeeded")
                            }.onFailure {
                                Log.e(TAG, "start failed: ${it.message}", it)
                                lastError = it.message
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.start_button))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        lastError = null
                        Log.i(TAG, "stop requested")
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { TailscaleBridge.stop() }
                            }.onSuccess {
                                Log.i(TAG, "stop succeeded")
                            }.onFailure {
                                Log.e(TAG, "stop failed: ${it.message}", it)
                                lastError = it.message
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.stop_button))
                }
            }
        }
    }
}
