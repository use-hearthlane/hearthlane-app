package org.hearthlane.core.connectivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * [HttpBytesGetter] over the normal Android network stack
 * (`HttpURLConnection`). Used only for the home-LAN attempt and never for the
 * Tailscale path.
 */
class HttpUrlConnectionBytesGetter : HttpBytesGetter {

    override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = timeoutMs.toInt()
                connection.readTimeout = timeoutMs.toInt()
                val code = connection.responseCode
                val contentType = connection.getHeaderField("Content-Type")
                val finalUrl = connection.url.toString()
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { it.readBytes() } ?: ByteArray(0)
                HttpBytesResult(
                    statusCode = code,
                    contentType = contentType,
                    finalUrl = finalUrl,
                    body = body,
                )
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * [HttpBytesClient] over the normal Android network stack
 * (`HttpURLConnection`). Used only for the home-LAN attempt (for example
 * publishing the device location to the relay) and never for the Tailscale
 * path.
 */
class HttpUrlConnectionBytesClient : HttpBytesClient {

    override suspend fun request(
        method: String,
        url: String,
        contentType: String?,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): HttpBytesResult = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs.toInt()
            connection.readTimeout = timeoutMs.toInt()
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { it.readBytes() } ?: ByteArray(0)
            HttpBytesResult(
                statusCode = code,
                contentType = connection.getHeaderField("Content-Type"),
                finalUrl = connection.url.toString(),
                body = responseBody,
            )
        } finally {
            connection.disconnect()
        }
    }
}