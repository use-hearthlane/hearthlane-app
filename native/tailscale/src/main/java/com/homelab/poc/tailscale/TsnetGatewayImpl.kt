package com.homelab.poc.tailscale

import android.os.SystemClock
import android.util.Log
import com.homelab.poc.core.connectivity.ConnectivityState
import com.homelab.poc.core.frigate.TailscaleAuthRequired
import com.homelab.poc.core.frigate.TsnetGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [TsnetGateway] backed by the gomobile binding. This is the only component
 * that starts the embedded node for the Frigate connection path; the fallback
 * strategy only reaches it after the local probe fails.
 */
class TsnetGatewayImpl(
    private val hostname: String,
    private val stateDir: String,
    private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
) : TsnetGateway {

    override suspend fun ensureRunning() {
        var status = TailscaleBridge.status()
        if (status.state == ConnectivityState.CONNECTED) {
            return
        }
        if (status.state == ConnectivityState.STOPPED ||
            status.state == ConnectivityState.DISCONNECTED ||
            status.state == ConnectivityState.FAILED
        ) {
            Log.i(TAG, "starting embedded Tailscale node")
            TailscaleBridge.start(hostname, authKey = "", stateDir)
        }

        val deadline = SystemClock.elapsedRealtime() + connectTimeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            status = TailscaleBridge.status()
            when (status.state) {
                ConnectivityState.CONNECTED -> {
                    Log.i(TAG, "Tailscale connected")
                    return
                }
                ConnectivityState.AUTHENTICATING -> {
                    Log.i(TAG, "Tailscale requires authentication")
                    throw TailscaleAuthRequired(status.authUrl)
                }
                ConnectivityState.FAILED ->
                    throw IOException("tailscale failed: ${status.error ?: "unknown error"}")
                else -> {
                    // CONNECTING / DISCONNECTED: keep waiting.
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        throw IOException("tailscale did not reach Running within ${connectTimeoutMs}ms")
    }

    override suspend fun httpGet(url: String, timeoutMs: Long): String =
        withContext(Dispatchers.IO) {
            TailscaleBridge.httpGet(url, timeoutMs)
        }

    private companion object {
        const val TAG = "FrigateConnection"
        const val POLL_INTERVAL_MS = 500L
        const val DEFAULT_CONNECT_TIMEOUT_MS = 45_000L
    }
}
