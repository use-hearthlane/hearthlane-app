package com.homelab.poc.tailscale

import com.homelab.poc.core.connectivity.ConnectivityState
import com.homelab.poc.core.connectivity.ConnectivityStatus
import com.homelab.poc.core.connectivity.HttpBytesResult
import com.homelab.poc.core.connectivity.HttpStream
import com.homelab.poc.tsembed.Tsembed
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal Kotlin bridge over the gomobile-generated [Tsembed] binding.
 *
 * This is the only place in the app that knows about the generated
 * `com.homelab.poc.tsembed` classes. Callers get a dependency-free
 * [ConnectivityStatus] instead of Go JSON strings.
 */
object TailscaleBridge {

    init {
        // Debug builds enable the Go DNS debug logs (cache hit/miss) so tailnet
        // DNS behavior is confirmable in logcat during device validation.
        Tsembed.setDebugDNS(BuildConfig.DEBUG)
    }

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
     * Opens an HTTP GET exclusively over the embedded tailnet as a progressive
     * [HttpStream]. Blocks the calling thread and must not be invoked on the
     * main thread. The body is read incrementally through [HttpStream.read],
     * which pulls bounded chunks from the Go side; it is never buffered whole.
     */
    fun httpOpenStream(url: String, connectTimeoutMs: Long): HttpStream {
        val info = Tsembed.openHttpStream(url, connectTimeoutMs)
        return TsnetHttpStream(info.id, info.statusCode.toInt(), info.contentType, info.finalURL)
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

/**
 * [HttpStream] backed by a stateful Go stream handle. Each [read] pulls one
 * bounded chunk from the Go side; EOF is reported by an empty chunk. [close]
 * is idempotent and closes the Go stream, which also unblocks a pending read.
 */
private class TsnetHttpStream(
    private val id: Long,
    override val statusCode: Int,
    override val contentType: String?,
    override val finalUrl: String,
) : HttpStream {

    private val closed = AtomicBoolean(false)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        if (closed.get()) return -1
        val chunk = Tsembed.readChunk(id, length.toLong())
        if (chunk.isEmpty()) return -1
        chunk.copyInto(buffer, destinationOffset = offset, startIndex = 0, endIndex = chunk.size)
        return chunk.size
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            Tsembed.closeStream(id)
        }
    }
}
