package org.hearthlane.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import org.hearthlane.core.frigate.HttpUrlConnectionStreamGetter
import org.hearthlane.core.frigate.TransportKind
import org.hearthlane.core.frigate.streamGetterFor
import org.hearthlane.core.connectivity.HttpStreamGetter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * LOCAL end-to-end test: a real [HttpUrlConnectionStreamGetter] (selected by
 * [streamGetterFor] for LOCAL) feeds [StreamingHttpDataSource] against a tiny
 * dependency-free HTTP server that behaves like Frigate:
 *
 * - responds `video/mp4`;
 * - sends no Content-Length (chunked);
 * - ignores Range (returns the whole body as 200);
 * - delivers the content in several chunks.
 *
 * Proves the progressive contract works over the real LOCAL transport.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamingHttpDataSourceLocalTest {

    private lateinit var server: TestHttpServer

    @Before
    fun setUp() {
        server = TestHttpServer()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `LOCAL getter streams a chunked clip progressively through the DataSource`() = runBlocking {
        val chunks = listOf(
            "part1-".toByteArray(),
            "part2-".toByteArray(),
            "part3-".toByteArray(),
        )
        server.handler = { _ -> Response.Chunked(200, "video/mp4", chunks) }

        val getter: HttpStreamGetter = streamGetterFor(TransportKind.LOCAL, NoGateway())
        val dataSource = StreamingHttpDataSource(getter, 2_000)

        val length = dataSource.open(spec(server.url + "/clip", position = 0, length = C.LENGTH_UNSET.toLong()))

        assertEquals(C.LENGTH_UNSET.toLong(), length)
        assertEquals(
            mapOf("Content-Type" to listOf("video/mp4")),
            dataSource.getResponseHeaders(),
        )
        assertEquals("part1-part2-part3-", readAll(dataSource))
        dataSource.close()
    }

    @Test
    fun `LOCAL getter honors position by discarding bytes from a chunked clip`() = runBlocking {
        server.handler = {
            Response.Chunked(200, "video/mp4", listOf("0123456789".toByteArray()))
        }
        val dataSource = StreamingHttpDataSource(
            streamGetterFor(TransportKind.LOCAL, NoGateway()),
            2_000,
        )

        dataSource.open(spec(server.url + "/clip", position = 4, length = C.LENGTH_UNSET.toLong()))

        assertEquals("456789", readAll(dataSource))
        dataSource.close()
    }

    private fun spec(url: String, position: Long, length: Long): DataSpec =
        DataSpec.Builder().setUri(url).setPosition(position).setLength(length).build()

    private fun readAll(dataSource: StreamingHttpDataSource): String {
        val buffer = ByteArray(64)
        val out = StringBuilder()
        while (true) {
            val n = dataSource.read(buffer, 0, buffer.size)
            if (n < 0) break
            out.append(String(buffer, 0, n, Charsets.UTF_8))
        }
        return out.toString()
    }

    /** streamGetterFor only needs a gateway for the TAILSCALE branch. */
    private class NoGateway : org.hearthlane.core.connectivity.TsnetGateway {
        override suspend fun ensureRunning() = Unit
        override suspend fun stopIfRunning() = Unit
        override suspend fun reset() = Unit
        override suspend fun httpGet(url: String, timeoutMs: Long): String = ""
        override suspend fun httpGetBytes(
            url: String,
            timeoutMs: Long,
        ): org.hearthlane.core.connectivity.HttpBytesResult =
            org.hearthlane.core.connectivity.HttpBytesResult(200, "application/json", url, ByteArray(0))
    }
}

private sealed class Response {
    abstract val status: Int
    abstract val contentType: String

    class Chunked(
        override val status: Int,
        override val contentType: String,
        private val chunks: List<ByteArray>,
    ) : Response() {
        fun write(out: OutputStream) {
            val header = buildString {
                appendLine("HTTP/1.1 ${status} ${reason(status)}")
                appendLine("Content-Type: $contentType")
                appendLine("Transfer-Encoding: chunked")
                appendLine("Connection: close")
                appendLine()
            }.toByteArray(Charsets.US_ASCII)
            out.write(header)
            for (chunk in chunks) {
                out.write("%x\r\n".format(chunk.size).toByteArray(Charsets.US_ASCII))
                out.write(chunk)
                out.write("\r\n".toByteArray(Charsets.US_ASCII))
            }
            out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        }
    }
}

private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "Status"
    }

private class TestHttpServer {
    @Volatile
    var handler: (path: String) -> Response = {
        Response.Chunked(404, "text/plain", listOf())
    }

    private val serverSocket = ServerSocket(0)
    private val acceptThread = thread(isDaemon = true, name = "test-http-server") {
        while (!serverSocket.isClosed) {
            try {
                val socket = serverSocket.accept()
                thread(isDaemon = true) { handle(socket) }
            } catch (_: Exception) {
                break
            }
        }
    }

    val url: String get() = "http://127.0.0.1:${serverSocket.localPort}"

    fun stop() {
        serverSocket.close()
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream()
            readRequestLine(input)
            readUntilEmptyLine(input)
            val response = handler("/")
            val out = s.getOutputStream()
            when (response) {
                is Response.Chunked -> response.write(out)
            }
            out.flush()
        }
    }

    private fun readRequestLine(input: java.io.InputStream) {
        readLine(input)
    }

    private fun readUntilEmptyLine(input: java.io.InputStream) {
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) return
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.US_ASCII.name())
            if (b == '\n'.code) return bytes.toString(Charsets.US_ASCII.name()).trimEnd('\r')
            bytes.write(b)
        }
    }
}