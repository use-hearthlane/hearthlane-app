package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult
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
