package com.homelab.poc.core.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * [DataSource] for Media3/ExoPlayer that performs every HTTP GET through an
 * injected [HttpBytesGetter].
 *
 * The getter is the ONLY network path this source can use. For the Tailscale
 * path the getter is the tsnet getter, so no media request can escape to the
 * Android network. Redirects are followed by the getter and [getUri] returns
 * the final URL so relative HLS segment references resolve correctly.
 *
 * Each `open` buffers the whole response in memory, which is deliberately
 * acceptable for the Phase 4 spike: every HLS request is a bounded response
 * (one playlist or one media segment). It would not suit progressive or
 * long-lived streams, and that is documented as a POC limitation.
 */
@UnstableApi
class HttpBytesDataSource(
    private val getter: HttpBytesGetter,
    private val timeoutMs: Long,
) : DataSource {

    private var result: HttpBytesResult? = null
    private var uri: Uri? = null
    private var readOffset = 0L
    private var bytesRead = 0L
    private var totalBytes = 0L
    private var closed = true

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op: bandwidth estimation and cache hooks are out of POC scope.
    }

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        val fetched = runBlocking { getter.getBytes(url, timeoutMs) }
        result = fetched
        uri = Uri.parse(fetched.finalUrl)
        val available = fetched.body.size.toLong() - dataSpec.position
        totalBytes = if (dataSpec.length >= 0) {
            minOf(available, dataSpec.length)
        } else {
            available
        }
        readOffset = dataSpec.position
        bytesRead = 0L
        closed = false
        Log.d(
            TAG,
            "GET $url -> HTTP ${fetched.statusCode}, ${fetched.body.size} bytes, " +
                "${fetched.contentType ?: "no content type"}",
        )
        return maxOf(0L, totalBytes)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) return C.RESULT_END_OF_INPUT
        if (bytesRead >= totalBytes) return C.RESULT_END_OF_INPUT
        val body = result?.body ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), totalBytes - bytesRead).toInt()
        val from = readOffset.toInt()
        if (from < 0 || from + toRead > body.size) {
            throw IOException("HttpBytesDataSource read past buffer end")
        }
        body.copyInto(buffer, destinationOffset = offset, startIndex = from, endIndex = from + toRead)
        readOffset += toRead
        bytesRead += toRead
        return toRead
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        result?.contentType?.let { mapOf("Content-Type" to listOf(it)) } ?: emptyMap()

    override fun close() {
        result = null
        uri = null
        bytesRead = 0L
        totalBytes = 0L
        closed = true
    }

    private companion object {
        const val TAG = "PocCamera"
    }
}
