package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import java.io.IOException
import java.net.URI

/**
 * Discovers go2rtc streams and builds the HLS live URL, always through an
 * injected [HttpBytesGetter] so the same logic serves the local and the
 * Tailscale path.
 *
 * Frigate bundles go2rtc and exposes it behind its own origin under the
 * `/api/go2rtc/` prefix (verified against Frigate 0.17.1):
 *
 * - stream list:  `GET /api/go2rtc/streams` (Frigate HTTP API)
 * - HLS/fMP4:     `GET /api/go2rtc/api/stream.m3u8?src={name}&mp4`
 *
 * Note: on Frigate 0.17.x a request to `/go2rtc/...` (without the `/api/`
 * prefix) is caught by the web UI SPA and returns HTML, so the prefix is
 * mandatory.
 *
 * Only the first stream is returned: the POC plays a single camera. The first
 * stream is the first top-level key in document order, so the selection is
 * deterministic and matches the order go2rtc reports its configured streams.
 */
class Go2RtcStreams(private val getter: HttpBytesGetter) {

    /**
     * Returns the name of the first available go2rtc stream, or null when
     * Frigate reports no streams.
     *
     * @throws Exception when the request fails or returns a non-2xx status.
     */
    suspend fun firstStreamName(baseUrl: String, timeoutMs: Long): String? {
        val result = getter.getBytes("$baseUrl/api/go2rtc/streams", timeoutMs)
        if (result.statusCode !in 200..299) {
            throw IOException("GET /api/go2rtc/streams -> HTTP ${result.statusCode}")
        }
        return firstTopLevelKey(result.body.toString(Charsets.UTF_8))
    }

    /**
     * HLS/fMP4 live URL for a go2rtc stream, proxied through Frigate under the
     * `/api/go2rtc/` prefix. `mp4` selects fMP4 output (preferred by
     * ExoPlayer) instead of MPEG-TS.
     */
    fun hlsUrl(baseUrl: String, streamName: String): String =
        "$baseUrl/api/go2rtc/api/stream.m3u8?src=$streamName&mp4"

    /**
     * Creates a go2rtc HLS session for [streamName] and returns the media
     * playlist URL of that session.
     *
     * go2rtc starts a camera producer lazily when the first HLS consumer
     * attaches (this master request) and drops the session after its 5s
     * keepalive. Playing the returned media playlist URL reuses that same
     * session, so ExoPlayer requests init/segments against a session that is
     * already consuming. Requesting a fresh master inside ExoPlayer instead
     * would race the cold producer start and the session keepalive.
     *
     * @throws Exception when the master request fails, returns a non-2xx
     *   status, or does not return a valid m3u8 playlist. go2rtc answers HTTP
     *   200 with an empty body when it cannot attach an HLS consumer; the
     *   exception message keeps the response body preview so the cause is
     *   visible.
     */
    suspend fun resolveMediaPlaylistUrl(baseUrl: String, streamName: String, timeoutMs: Long): String {
        val masterUrl = hlsUrl(baseUrl, streamName)
        val result = getter.getBytes(masterUrl, timeoutMs)
        if (result.statusCode !in 200..299) {
            throw IOException("HLS master GET $masterUrl -> HTTP ${result.statusCode}")
        }
        val body = result.body.toString(Charsets.UTF_8)
        val mediaUrl = resolveMediaPlaylistUrl(masterUrl, body)
        if (!body.startsWith("#EXTM3U") || mediaUrl == null) {
            val preview = body.take(200)
            throw IOException(
                "HLS master is not an m3u8 playlist (HTTP ${result.statusCode}, " +
                    "type=${result.contentType}): \"$preview\"",
            )
        }
        return mediaUrl
    }

    companion object {
        /**
         * Resolves the media playlist reference inside a go2rtc HLS master
         * playlist against the master URL. Returns null when the master does
         * not reference a media playlist.
         */
        fun resolveMediaPlaylistUrl(masterUrl: String, masterBody: String): String? {
            val marker = "playlist.m3u8?id="
            val start = masterBody.indexOf(marker)
            if (start < 0) return null
            val lineStart = masterBody.lastIndexOf('\n', start).let { if (it < 0) 0 else it + 1 }
            val lineEnd = masterBody.indexOfAny(charArrayOf('\r', '\n'), start)
                .let { if (it < 0) masterBody.length else it }
            val token = masterBody.substring(lineStart, lineEnd).trim()
            return runCatching { URI(masterUrl).resolve(token).toString() }.getOrNull()
        }

        /**
         * Returns the first top-level key of a JSON object, in document order.
         * Handles string values and nested objects/arrays at any depth; returns
         * null for empty or non-object payloads. Kept dependency-free so the
         * module stays JVM-testable without a JSON library.
         */
        fun firstTopLevelKey(json: String): String? {
            var depth = 0
            var inString = false
            var keyStart = -1
            var i = 0
            while (i < json.length) {
                val c = json[i]
                if (inString) {
                    when (c) {
                        '\\' -> i++ // skip the escaped character
                        '"' -> {
                            inString = false
                            if (keyStart >= 0) {
                                val key = json.substring(keyStart, i)
                                var j = i + 1
                                while (j < json.length && json[j].isWhitespace()) j++
                                if (j < json.length && json[j] == ':') {
                                    return key
                                }
                                keyStart = -1
                                i = j
                            }
                        }
                    }
                } else {
                    when (c) {
                        '"' -> {
                            inString = true
                            keyStart = if (depth == 1) i + 1 else -1
                        }
                        '{' -> depth++
                        '}' -> depth--
                    }
                }
                i++
            }
            return null
        }
    }
}
