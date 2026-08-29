package org.hearthlane.core.connectivity

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Exercises [HttpUrlConnectionBytesClient] against a minimal local HTTP server
 * built on a plain [ServerSocket], so the tests run on any JVM without pulling
 * in an external HTTP server dependency.
 */
class HttpUrlConnectionBytesClientTest {

    private class MiniServer(
        var statusCode: Int = 204,
        var onRequest: ((method: String, headers: Map<String, String>, body: String) -> Unit)? = null,
    ) {
        private val serverSocket = ServerSocket(0, 1, InetSocketAddress("127.0.0.1", 0).address)
        private var thread: Thread? = null

        val port: Int get() = serverSocket.localPort

        fun start() {
            thread = Thread {
                try {
                    val socket = serverSocket.accept()
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val requestLine = reader.readLine() ?: return@Thread
                    val method = requestLine.substringBefore(" ")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                        val idx = line.indexOf(':')
                        if (idx > 0) headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                    }
                    val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                    val body = CharArray(contentLength).let { reader.read(it); String(it) }
                    onRequest?.invoke(method, headers, body)

                    val responseBody = when (statusCode) {
                        200 -> "{\"devices\":[]}"
                        204 -> ""
                        503 -> "{\"error\":\"boom\"}"
                        else -> "ok"
                    }
                    val statusText = when (statusCode) {
                        200 -> "OK"
                        204 -> "No Content"
                        503 -> "Service Unavailable"
                        else -> "OK"
                    }
                    val bytes = responseBody.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().use { out ->
                        out.write("HTTP/1.1 $statusCode $statusText\r\n".toByteArray(Charsets.US_ASCII))
                        out.write("Content-Type: application/json\r\n".toByteArray(Charsets.US_ASCII))
                        if (statusCode != 204) {
                            out.write("Content-Length: ${bytes.size}\r\n".toByteArray(Charsets.US_ASCII))
                        }
                        out.write("Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        if (statusCode != 204) out.write(bytes)
                        out.flush()
                    }
                    socket.close()
                } catch (_: Exception) {
                    // Request already answered or refused; nothing to do in the server thread.
                } finally {
                    runCatching { serverSocket.close() }
                }
            }.apply { isDaemon = true }.also { it.start() }
        }

        fun awaitClose() {
            thread?.join(5_000)
        }
    }

    @Test
    fun request_sendsMethodBodyContentTypeAndHeaders() = runTest {
        var receivedMethod: String? = null
        var receivedHeaders: Map<String, String>? = null
        var receivedBody: String? = null
        val server = MiniServer(statusCode = 204).apply {
            onRequest = { method, headers, body ->
                receivedMethod = method
                receivedHeaders = headers
                receivedBody = body
            }
        }
        server.start()
        try {
            val result = HttpUrlConnectionBytesClient().request(
                method = "PUT",
                url = "http://127.0.0.1:${server.port}/v1/devices/d1/location",
                contentType = "application/json",
                body = "{\"latitude\":-23.5}",
                headers = mapOf(
                    "Authorization" to "Bearer abc.def",
                    "X-Custom" to "custom-value",
                ),
                timeoutMs = 5_000,
            )
            assertEquals(204, result.statusCode)
            assertEquals("PUT", receivedMethod)
            assertEquals("{\"latitude\":-23.5}", receivedBody)
            assertEquals("Bearer abc.def", receivedHeaders?.get("Authorization"))
            assertEquals("custom-value", receivedHeaders?.get("X-Custom"))
            assertEquals("application/json", receivedHeaders?.get("Content-Type"))
        } finally {
            server.awaitClose()
        }
    }

    @Test
    fun request_getSendsNoBodyButKeepsHeaders() = runTest {
        var receivedMethod: String? = null
        var receivedBody: String? = null
        var receivedAuthorization: String? = null
        val server = MiniServer(statusCode = 200).apply {
            onRequest = { method, headers, body ->
                receivedMethod = method
                receivedBody = body
                receivedAuthorization = headers["Authorization"]
            }
        }
        server.start()
        try {
            val result = HttpUrlConnectionBytesClient().request(
                method = "GET",
                url = "http://127.0.0.1:${server.port}/devices",
                contentType = null,
                body = null,
                headers = mapOf("Authorization" to "Bearer abc.def"),
                timeoutMs = 5_000,
            )
            assertEquals(200, result.statusCode)
            assertEquals("GET", receivedMethod)
            assertEquals("", receivedBody)
            assertEquals("Bearer abc.def", receivedAuthorization)
            assertEquals("{\"devices\":[]}", String(result.body, Charsets.UTF_8))
        } finally {
            server.awaitClose()
        }
    }

    @Test
    fun request_returnsNon2xxInsteadOfThrowing() = runTest {
        val server = MiniServer(statusCode = 503)
        server.start()
        try {
            val result = HttpUrlConnectionBytesClient().request(
                method = "PUT",
                url = "http://127.0.0.1:${server.port}/fail",
                contentType = "application/json",
                body = "{}",
                headers = emptyMap(),
                timeoutMs = 5_000,
            )
            assertEquals(503, result.statusCode)
            assertTrue(String(result.body, Charsets.UTF_8).contains("boom"))
        } finally {
            server.awaitClose()
        }
    }

    @Test
    fun request_throwsOnUnreachableServer() = runTest {
        val result = runCatching {
            HttpUrlConnectionBytesClient().request(
                method = "PUT",
                url = "http://127.0.0.1:1/v1/devices/d1/location",
                contentType = "application/json",
                body = "{}",
                headers = mapOf("Authorization" to "Bearer abc.def"),
                timeoutMs = 2_000,
            )
        }
        if (result.isSuccess) {
            fail("expected a transport error for an unreachable server")
        }
    }
}