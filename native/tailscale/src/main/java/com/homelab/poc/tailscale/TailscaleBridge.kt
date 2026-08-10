package com.homelab.poc.tailscale

import com.homelab.poc.core.connectivity.ConnectivityState
import com.homelab.poc.core.connectivity.ConnectivityStatus
import com.homelab.poc.tsembed.Tsembed
import org.json.JSONObject

/**
 * Minimal Kotlin bridge over the gomobile-generated [Tsembed] binding.
 *
 * This is the only place in the app that knows about the generated
 * `com.homelab.poc.tsembed` classes. Callers get a dependency-free
 * [ConnectivityStatus] instead of Go JSON strings.
 */
object TailscaleBridge {

    fun start(hostname: String, authKey: String, stateDir: String) {
        Tsembed.start(hostname, authKey, stateDir)
    }

    fun stop() {
        Tsembed.stop()
    }

    /**
     * Reports the current embedded node state. Cheap to poll for a UI loop.
     * Runs a blocking Go call and should not be invoked on the main thread.
     */
    fun status(): ConnectivityStatus {
        val json = JSONObject(Tsembed.status())
        return ConnectivityStatus(
            state = parseState(json.optString("state")),
            authUrl = json.optString("authUrl").takeIf { it.isNotBlank() },
            error = json.optString("error").takeIf { it.isNotBlank() },
        )
    }

    private fun parseState(raw: String): ConnectivityState = when (raw) {
        "Disconnected" -> ConnectivityState.DISCONNECTED
        "Authenticating" -> ConnectivityState.AUTHENTICATING
        "Connecting" -> ConnectivityState.CONNECTING
        "Connected" -> ConnectivityState.CONNECTED
        "Failed" -> ConnectivityState.FAILED
        "Stopped" -> ConnectivityState.STOPPED
        else -> ConnectivityState.DISCONNECTED
    }
}
