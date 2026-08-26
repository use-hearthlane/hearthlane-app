package com.homelab.poc.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.homelab.poc.core.connectivity.HttpStream
import com.homelab.poc.core.connectivity.HttpStreamGetter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Contract tests for [StreamingHttpDataSource] against a fake
 * [HttpStream]/[HttpStreamGetter] under Robolectric (a working `android.net.Uri`
 * is required for the Media3 [DataSpec] and [android.net.Uri] contract).
 *
 * They verify the Media3 [DataSource] contract: incremental reads, position via
 * progressive discard, length bounds, EOF, HTTP status preservation, read
 * errors and close semantics, without materializing the body.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamingHttpDataSourceTest {

    private val baseUrl = "http://frigate:5000/api/events/evt-1/clip.mp4"

    @Test
    fun `open returns LENGTH_UNSET for a body of unknown size`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(200, "video/mp4", baseUrl, "0123456789"))

        val length = open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        assertEquals(C.LENGTH_UNSET.toLong(), length)
        dataSource.close()
    }

    @Test
    fun `open preserves content type and final url`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(200, "video/mp4", baseUrl, "0123456789"))

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        assertEquals(
            mapOf("Content-Type" to listOf("video/mp4")),
            dataSource.getResponseHeaders(),
        )
        assertTrue(
            "getUri must be the stream's final URL",
            dataSource.getUri()?.toString() == baseUrl,
        )
        dataSource.close()
    }

    @Test
    fun `reads the body incrementally and reaches EOF`() = runBlocking {
        val body = "0123456789abcdef"
        val stream = FakeStream(200, "video/mp4", baseUrl, body)
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        val buffer = ByteArray(4)
        val out = StringBuilder()
        var n = dataSource.read(buffer, 0, 4)
        var reads = 0
        while (n > 0) {
            out.append(String(buffer, 0, n, Charsets.UTF_8))
            reads++
            n = dataSource.read(buffer, 0, 4)
        }
        assertEquals(-1, n) // EOF, not an error
        assertEquals(body, out.toString())
        assertTrue("the body must be read in multiple bounded reads", reads >= 4)
        dataSource.close()
    }

    @Test
    fun `large content is consumed without requesting the whole body`() = runBlocking {
        // A logical 100 MB body delivered in chunks, never materialized.
        val size = 100L * 1024 * 1024
        val stream = SyntheticStream(200, "video/mp4", baseUrl, size)
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = dataSource.read(buffer, 0, buffer.size)
            if (n < 0) break
            total += n
        }
        assertEquals(size, total)
        assertTrue(
            "reads must be bounded by the provided buffer, never the whole content",
            stream.maxRequestedRead <= buffer.size,
        )
        dataSource.close()
    }

    @Test
    fun `position zero starts at the first byte`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(200, "video/mp4", baseUrl, "0123456789"))

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        assertEquals("0123456789", readAll(dataSource))
        dataSource.close()
    }

    @Test
    fun `a non-zero position discards bytes progressively`() = runBlocking {
        val body = "0123456789"
        val stream = FakeStream(200, "video/mp4", baseUrl, body)
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 4, length = C.LENGTH_UNSET.toLong())

        // The first bytes were discarded; reads start exactly at position 4.
        assertEquals(4L, stream.offset)
        assertEquals("456789", readAll(dataSource))
        dataSource.close()
    }

    @Test
    fun `a position beyond the content reaches EOF`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(200, "video/mp4", baseUrl, "0123456789"))

        open(dataSource, position = 20, length = C.LENGTH_UNSET.toLong())

        assertEquals("", readAll(dataSource))
        dataSource.close()
    }

    @Test
    fun `an explicit length bounds the delivered bytes`() = runBlocking {
        // content = ABCDEFGHIJ, position = 2, length = 3 -> CDE, then EOF.
        val stream = FakeStream(200, "video/mp4", baseUrl, "ABCDEFGHIJ")
        val dataSource = dataSource(stream = stream)

        val length = open(dataSource, position = 2, length = 3)

        assertEquals(3L, length)
        assertEquals("CDE", readAll(dataSource))
        assertEquals("the stream must not be read beyond the requested length", 5L, stream.offset)
        dataSource.close()
    }

    @Test
    fun `HTTP 404 surfaces HttpStatusIOException with the status`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(404, "text/plain", baseUrl, ""))

        var thrown: Exception? = null
        try {
            open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is HttpStatusIOException)
        assertEquals(404, (thrown as HttpStatusIOException).statusCode)
    }

    @Test
    fun `HTTP 500 surfaces HttpStatusIOException with the status`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(500, "text/plain", baseUrl, ""))

        var thrown: Exception? = null
        try {
            open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is HttpStatusIOException)
        assertEquals(500, (thrown as HttpStatusIOException).statusCode)
    }

    @Test
    fun `a transport error on open is propagated`() = runBlocking {
        val getter = RecordingGetter(FakeStream(200, "video/mp4", baseUrl, "0123456789"))
        getter.openError = IOException("connection refused")
        val dataSource = StreamingHttpDataSource(getter, 2_000)

        var thrown: Exception? = null
        try {
            open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is IOException)
        assertTrue((thrown as IOException).cause is IOException)
    }

    @Test
    fun `an error during read is propagated and not converted to EOF`() = runBlocking {
        val stream = FakeStream(200, "video/mp4", baseUrl, "0123456789")
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        assertEquals("0123", readN(dataSource, 4))
        stream.throwOnRead = true
        var thrown: Exception? = null
        try {
            dataSource.read(ByteArray(4), 0, 4)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue("a mid-stream failure must surface as IOException", thrown is IOException)
        dataSource.close()
    }

    @Test
    fun `close closes the underlying stream and releases the reference`() = runBlocking {
        val stream = FakeStream(200, "video/mp4", baseUrl, "0123456789")
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())
        assertEquals("0123", readN(dataSource, 4))
        dataSource.close()

        assertTrue("close must close the HttpStream", stream.closed)
    }

    @Test
    fun `close is idempotent`() = runBlocking {
        val dataSource = dataSource(stream = FakeStream(200, "video/mp4", baseUrl, "0123456789"))

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())
        dataSource.close()
        dataSource.close()
    }

    @Test
    fun `read after close returns EOF without touching the transport`() = runBlocking {
        val stream = FakeStream(200, "video/mp4", baseUrl, "0123456789")
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())
        val readsBeforeClose = stream.readCount
        dataSource.close()

        assertEquals(-1, dataSource.read(ByteArray(4), 0, 4))
        assertEquals("no transport read must happen after close", readsBeforeClose, stream.readCount)
    }

    @Test
    fun `close during a pending read unblocks it`() = runBlocking {
        val stream = BlockingStream(200, "video/mp4", baseUrl)
        val dataSource = dataSource(stream = stream)

        open(dataSource, position = 0, length = C.LENGTH_UNSET.toLong())

        val result = java.util.concurrent.atomic.AtomicInteger(-2)
        val reader = kotlin.concurrent.thread(isDaemon = true) {
            result.set(dataSource.read(ByteArray(4), 0, 4))
        }
        Thread.sleep(100) // let the read block on the stream
        dataSource.close()
        reader.join(2_000)

        assertEquals("the pending read must be interrupted by close", -1, result.get())
    }

    @Test
    fun `the factory creates a fresh DataSource per request`() {
        val factory = StreamingHttpDataSourceFactory(
            RecordingGetter(FakeStream(200, "video/mp4", baseUrl, "x")),
            2_000,
        )

        val first = factory.createDataSource()
        val second = factory.createDataSource()

        assertTrue(first is StreamingHttpDataSource)
        assertTrue(second is StreamingHttpDataSource)
        assertNotSame("players must not share a DataSource", first, second)
    }

    // --- helpers ---

    private fun dataSource(stream: HttpStream): StreamingHttpDataSource =
        StreamingHttpDataSource(RecordingGetter(stream), 2_000)

    private fun open(dataSource: StreamingHttpDataSource, position: Long, length: Long): Long {
        val spec = DataSpec.Builder()
            .setUri(baseUrl)
            .setPosition(position)
            .setLength(length)
            .build()
        return dataSource.open(spec)
    }

    private fun readAll(dataSource: StreamingHttpDataSource): String {
        val buffer = ByteArray(16)
        val out = StringBuilder()
        while (true) {
            val n = dataSource.read(buffer, 0, buffer.size)
            if (n < 0) break
            out.append(String(buffer, 0, n, Charsets.UTF_8))
        }
        return out.toString()
    }

    private fun readN(dataSource: StreamingHttpDataSource, length: Int): String {
        val buffer = ByteArray(length)
        val n = dataSource.read(buffer, 0, length)
        if (n < 0) return ""
        return String(buffer, 0, n, Charsets.UTF_8)
    }

    private class RecordingGetter(
        private val stream: HttpStream,
    ) : HttpStreamGetter {
        var openError: Exception? = null
        var lastUrl: String? = null
        var lastConnectTimeout: Long = 0
        var openCount = 0

        override suspend fun open(url: String, connectTimeoutMs: Long): HttpStream {
            openCount++
            lastUrl = url
            lastConnectTimeout = connectTimeoutMs
            openError?.let { throw it }
            return stream
        }
    }

    private open class FakeStream(
        override val statusCode: Int,
        override val contentType: String?,
        override val finalUrl: String,
        private val body: String,
    ) : HttpStream {
        val closedFlag = AtomicBoolean(false)
        val closed: Boolean get() = closedFlag.get()
        var offset: Long = 0
        var readCount = 0
        @Volatile var throwOnRead = false

        override fun read(buffer: ByteArray, offsetIn: Int, length: Int): Int {
            if (closedFlag.get()) return -1
            if (length <= 0) return 0
            readCount++
            if (throwOnRead) throw IOException("read failed")
            if (this.offset >= body.length) return -1
            val toRead = minOf(length.toLong(), body.length - this.offset).toInt()
            body.substring(this.offset.toInt(), this.offset.toInt() + toRead)
                .toByteArray(Charsets.UTF_8)
                .copyInto(buffer, destinationOffset = offsetIn)
            this.offset += toRead
            return toRead
        }

        override fun close() {
            closedFlag.set(true)
        }
    }

    /** A logical body of [size] bytes delivered on demand, never materialized. */
    private class SyntheticStream(
        override val statusCode: Int,
        override val contentType: String?,
        override val finalUrl: String,
        private val size: Long,
    ) : HttpStream {
        var offset = 0L
        var maxRequestedRead = 0
        private val closedFlag = AtomicBoolean(false)

        override fun read(buffer: ByteArray, offsetIn: Int, length: Int): Int {
            if (closedFlag.get()) return -1
            if (length <= 0) return 0
            maxRequestedRead = maxOf(maxRequestedRead, length)
            if (offset >= size) return -1
            val toRead = minOf(length.toLong(), size - offset).toInt()
            buffer.fill(0x41.toByte(), offsetIn, offsetIn + toRead)
            offset += toRead
            return toRead
        }

        override fun close() {
            closedFlag.set(true)
        }
    }

    /** A stream whose read blocks until [close] is called. */
    private class BlockingStream(
        override val statusCode: Int,
        override val contentType: String?,
        override val finalUrl: String,
    ) : HttpStream {
        private val closedFlag = AtomicBoolean(false)

        override fun read(buffer: ByteArray, offsetIn: Int, length: Int): Int {
            while (!closedFlag.get()) {
                Thread.sleep(10)
            }
            return -1
        }

        override fun close() {
            closedFlag.set(true)
        }
    }
}