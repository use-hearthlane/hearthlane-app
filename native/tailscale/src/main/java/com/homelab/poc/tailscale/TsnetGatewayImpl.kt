package com.homelab.poc.tailscale

import android.os.SystemClock
import android.util.Log
import com.homelab.poc.core.connectivity.ConnectivityState
import com.homelab.poc.core.frigate.TailscaleAuthRequired
import com.homelab.poc.core.frigate.TsnetGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
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
        var sawAuth = false
        while (SystemClock.elapsedRealtime() < deadline) {
            status = TailscaleBridge.status()
            when (status.state) {
                ConnectivityState.CONNECTED -> {
                    Log.i(TAG, "Tailscale connected")
                    return
                }
                ConnectivityState.AUTHENTICATING -> {
                    sawAuth = true
                    // The enrollment URL is published asynchronously by the
                    // node's interactive login flow: the first AUTHENTICATING
                    // poll can see the state before the URL is available. Keep
                    // polling so the UI can show the login link instead of a
                    // bare error with nothing to act on.
                    status.authUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        // The enrollment URL is a transient credential: it is
                        // surfaced to the UI but never written to logcat.
                        Log.i(TAG, "Tailscale requires authentication (enrollment pending)")
                        throw TailscaleAuthRequired(url)
                    }
                }
                ConnectivityState.FAILED ->
                    throw IOException("tailscale failed: ${status.error ?: "unknown error"}")
                else -> {
                    // CONNECTING / DISCONNECTED: keep waiting.
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        if (sawAuth) {
            // The node needs enrollment but never exposed a URL before the
            // deadline. Fail as auth-required so the UI explains what to do;
            // tsnet also prints the URL to logcat.
            throw TailscaleAuthRequired(null)
        }
        throw IOException("tailscale did not reach Running within ${connectTimeoutMs}ms")
    }

    override suspend fun stopIfRunning() {
        val status = TailscaleBridge.status()
        if (status.state == ConnectivityState.STOPPED ||
            status.state == ConnectivityState.DISCONNECTED ||
            status.state == ConnectivityState.FAILED
        ) {
            return
        }
        Log.i(TAG, "stopping embedded Tailscale node after local connection confirmed")
        TailscaleBridge.stop()
    }

    override suspend fun httpGet(url: String, timeoutMs: Long): String =
        withContext(Dispatchers.IO) {
            TailscaleBridge.httpGet(url, timeoutMs)
        }

    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "resetting embedded Tailscale node identity")
            runCatching { TailscaleBridge.stop() }
                .onFailure { Log.w(TAG, "stop during reset failed; state will still be cleared", it) }
            // The enrolled identity lives in the node state directory. Deleting
            // it makes the next start a fresh, unauthenticated node while the
            // persisted per-installation hostname suffix is untouched.
            File(stateDir).deleteRecursively()
        }
    }

    override suspend fun httpGetBytes(url: String, timeoutMs: Long) =
        withContext(Dispatchers.IO) {
            TailscaleBridge.httpGetBytes(url, timeoutMs)
        }

    private companion object {
        const val TAG = "FrigateConnection"
        const val POLL_INTERVAL_MS = 500L
        const val DEFAULT_CONNECT_TIMEOUT_MS = 45_000L
    }
}
