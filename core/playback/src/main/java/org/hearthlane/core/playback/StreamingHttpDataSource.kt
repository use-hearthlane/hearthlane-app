package org.hearthlane.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.hearthlane.core.connectivity.HttpStream
import org.hearthlane.core.connectivity.HttpStreamGetter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * [DataSource] for Media3/ExoPlayer that serves a resource progressively
 * through an injected [HttpStreamGetter], without ever materializing the whole
 * response in memory.
 *
 * This is the streaming sibling of [HttpBytesDataSource]: it is meant for
 * resources that stay open and are consumed incrementally (a Frigate event
 * `clip.mp4`), where buffering the full body is impossible. Like its sibling,
 * the getter is the ONLY network path the source can use, so a Tailscale
 * request can never escape to the Android network.
 *
 * The Frigate endpoint ignores `Range` and answers a 200 with the whole body,
 * so [DataSpec.position] is honored by reading and discarding bytes
 * progressively from the start, and a missing `Content-Length` is the norm
 * (the chunked body is read until EOF).
 */
@UnstableApi
class StreamingHttpDataSource(
    private val getter: HttpStreamGetter,
    private val connectTimeoutMs: Long,
    private val onBytes: (Long) -> Unit = {},
) : DataSource {

    private var stream: HttpStream? = null
    private var uri: Uri? = null
    private var bytesRead = 0L
    private var totalBytes: Long = C.LENGTH_UNSET.toLong()
    private var closed = true

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op: bandwidth estimation and cache hooks are out of scope.
    }

    override fun open(dataSpec: DataSpec): Long {
        val opened = try {
            runBlocking { getter.open(dataSpec.uri.toString(), connectTimeoutMs) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException(
                "StreamingHttpDataSource: GET failed: ${e.message ?: e.javaClass.simpleName}",
                e,
            )
        }
        if (opened.statusCode !in 200..299) {
            val message = "StreamingHttpDataSource: GET failed -> HTTP ${opened.statusCode}"
            throw HttpStatusIOException(opened.statusCode, message)
        }
        stream = opened
        uri = Uri.parse(opened.finalUrl)
        // The server ignores Range and returns the whole body as a 200, so a
        // non-zero position is reached by consuming (and discarding) bytes
        // progressively, never by downloading the whole response first.
        skipToPosition(opened, dataSpec.position)
        bytesRead = 0L
        totalBytes = dataSpec.length
        closed = false
        return totalBytes
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) return C.RESULT_END_OF_INPUT
        if (totalBytes != C.LENGTH_UNSET.toLong() && bytesRead >= totalBytes) {
            return C.RESULT_END_OF_INPUT
        }
        val opened = stream ?: return C.RESULT_END_OF_INPUT
        val toRead = if (totalBytes != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), totalBytes - bytesRead).toInt()
        } else {
            length
        }
        if (toRead <= 0) return C.RESULT_END_OF_INPUT
        val n = opened.read(buffer, offset, toRead)
        if (n < 0) return C.RESULT_END_OF_INPUT
        bytesRead += n
        onBytes(n.toLong())
        return n
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        stream?.contentType?.let { mapOf("Content-Type" to listOf(it)) } ?: emptyMap()

    override fun close() {
        stream?.close()
        stream = null
        uri = null
        bytesRead = 0L
        totalBytes = C.LENGTH_UNSET.toLong()
        closed = true
    }

    /** Consumes and discards [position] bytes so reads start exactly there. */
    private fun skipToPosition(opened: HttpStream, position: Long) {
        var remaining = position
        val buffer = ByteArray(SKIP_BUFFER_SIZE)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val n = opened.read(buffer, 0, toRead)
            if (n < 0) return // the content ended before the requested position
            remaining -= n
        }
    }

    private companion object {
        const val SKIP_BUFFER_SIZE = 8192
    }
}