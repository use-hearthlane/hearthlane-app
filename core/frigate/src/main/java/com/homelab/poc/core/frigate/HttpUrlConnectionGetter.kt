package com.homelab.poc.core.frigate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [HttpGetter] over the normal Android network stack (`HttpURLConnection`).
 * Used only for the home-LAN attempt and never for the Tailscale path.
 */
class HttpUrlConnectionGetter : HttpGetter {

    override suspend fun get(url: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs.toInt()
            connection.readTimeout = timeoutMs.toInt()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code for $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
