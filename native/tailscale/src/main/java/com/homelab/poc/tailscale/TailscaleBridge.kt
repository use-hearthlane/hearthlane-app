package com.homelab.poc.tailscale

import com.homelab.poc.core.connectivity.ConnectivityState
import com.homelab.poc.core.connectivity.ConnectivityStatus
import com.homelab.poc.core.connectivity.HttpBytesResult
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
     * Performs an HTTP GET exclusively over the embedded tailnet (the Go side
     * dials with tsnet's own transport). Blocks the calling thread and must
     * not be invoked on the main thread.
     *
     * @throws Exception when the node is not running or the request fails.
     */
    fun httpGet(url: String, timeoutMs: Long): String = Tsembed.httpGet(url, timeoutMs)

    /**
     * Performs an HTTP GET exclusively over the embedded tailnet and returns
     * the full response (status, content type, final URL and body). Blocks the
     * calling thread and must not be invoked on the main thread. Non-2xx
     * statuses are returned, not thrown.
     *
     * @throws Exception when the node is not running or the request cannot
     *   reach the server.
     */
    fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult {
        val result = Tsembed.httpGetBytes(url, timeoutMs)
        return HttpBytesResult(
            statusCode = result.statusCode.toInt(),
            contentType = result.contentType,
            finalUrl = result.finalURL,
            body = result.body,
        )
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
