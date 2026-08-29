package org.hearthlane.tailscale

import android.os.SystemClock
import android.util.Log
import org.hearthlane.core.connectivity.ConnectivityState
import org.hearthlane.core.connectivity.TailscaleAuthRequired
import org.hearthlane.core.connectivity.TsnetGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Serializes [ensureRunning] so concurrent callers cannot call start() simultaneously. */
    private val connectMutex = Mutex()

    override suspend fun ensureRunning() {
        connectMutex.withLock {
            var status = TailscaleBridge.status()
            if (status.state == ConnectivityState.CONNECTED) {
                return
            }
            if (status.state == ConnectivityState.STOPPED ||
                status.state == ConnectivityState.DISCONNECTED ||
                status.state == ConnectivityState.FAILED
            ) {
                Log.i(
                    TAG,
                    "[TsnetLifecycle] start pre: stateDir=$stateDir stateDirExists=${File(stateDir).exists()} logDirExists=${File(stateDir, "logs").exists()}",
                )
                Log.i(TAG, "starting embedded Tailscale node")
                // Another gateway instance (for example the location foreground
                // service) may have started the node concurrently: "already
                // started" is not a failure — the poll loop below resolves the
                // real CONNECTED / FAILED state.
                runCatching { TailscaleBridge.start(hostname, authKey = "", stateDir) }
                    .onFailure { Log.w(TAG, "start request did not run (concurrent start): ${it.message}") }
            }

            val deadline = SystemClock.elapsedRealtime() + connectTimeoutMs
            var sawAuth = false
            var authUrl: String? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                status = TailscaleBridge.status()
                when (status.state) {
                    ConnectivityState.CONNECTED -> {
                        Log.i(TAG, "Tailscale connected")
                        return
                    }
                    ConnectivityState.AUTHENTICATING -> {
                        val url = status.authUrl?.takeIf { it.isNotBlank() }
                        // A node with a persisted identity can briefly report
                        // AUTHENTICATING while restoring its session before
                        // reaching CONNECTED. Only a state that persists across
                        // two polls is treated as genuinely needing enrollment;
                        // a single observation lets the node continue to
                        // CONNECTED without routing the app into Setup.
                        if (sawAuth) {
                            Log.i(TAG, "Tailscale requires authentication (enrollment pending)")
                            throw TailscaleAuthRequired(url ?: authUrl)
                        }
                        sawAuth = true
                        authUrl = url ?: authUrl
                    }
                    ConnectivityState.FAILED ->
                        throw IOException("tailscale failed: ${status.error ?: "unknown error"}")
                    else -> {
                        // CONNECTING / DISCONNECTED: the node is progressing, so
                        // a previous AUTHENTICATING observation was transient.
                        sawAuth = false
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
            if (sawAuth) {
                throw TailscaleAuthRequired(authUrl)
            }
            throw IOException("tailscale did not reach Running within ${connectTimeoutMs}ms")
        }
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
            val dir = File(stateDir)
            val logsDir = File(dir, "logs")
            Log.i(
                TAG,
                "[TsnetLifecycle] reset: stateDir=$stateDir stateDirExists=${dir.exists()} logDirExists=${logsDir.exists()}",
            )
            if (dir.exists()) {
                // Remove ONLY the node identity/enrollment state. The runtime log
                // directory is infrastructure, not identity: logpolicy panics with
                // "no safe place found to store log state" when TS_LOGS_DIR points
                // to a deleted directory, so logs/ is preserved across the reset.
                dir.listFiles()?.forEach { child ->
                    if (child != logsDir) {
                        child.deleteRecursively()
                    }
                }
                logsDir.mkdirs()
            }
            Log.i(
                TAG,
                "[TsnetLifecycle] reset done: stateDir=$stateDir stateDirExists=${dir.exists()} logDirExists=${logsDir.exists()}",
            )
        }
    }

    override suspend fun httpGetBytes(url: String, timeoutMs: Long) =
        withContext(Dispatchers.IO) {
            TailscaleBridge.httpGetBytes(url, timeoutMs)
        }

    override suspend fun httpPut(
        url: String,
        contentType: String,
        body: String,
        headers: Map<String, String>,
        timeoutMs: Long,
    ) = httpRequest("PUT", url, contentType, body, headers, timeoutMs)

    override suspend fun httpRequest(
        method: String,
        url: String,
        contentType: String?,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Long,
    ) = withContext(Dispatchers.IO) {
        TailscaleBridge.httpRequest(
            method = method,
            url = url,
            contentType = contentType ?: "",
            body = body ?: "",
            headers = headers,
            timeoutMs = timeoutMs,
        )
    }

    override suspend fun httpOpenStream(url: String, connectTimeoutMs: Long) =
        withContext(Dispatchers.IO) {
            TailscaleBridge.httpOpenStream(url, connectTimeoutMs)
        }

    private companion object {
        const val TAG = "FrigateConnection"
        const val POLL_INTERVAL_MS = 500L
        const val DEFAULT_CONNECT_TIMEOUT_MS = 45_000L
    }
}
