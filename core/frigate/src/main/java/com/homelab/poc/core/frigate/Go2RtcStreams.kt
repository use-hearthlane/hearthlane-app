package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import java.io.IOException

/**
 * Discovers go2rtc streams and builds the HLS live URL, always through an
 * injected [HttpBytesGetter] so the same logic serves the local and the
 * Tailscale path.
 *
 * Frigate bundles go2rtc and exposes it behind its own origin:
 *
 * - stream list:  `GET /go2rtc/streams` (Frigate HTTP API)
 * - HLS/fMP4:     `GET /go2rtc/api/stream.m3u8?src={name}&mp4`
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
        val result = getter.getBytes("$baseUrl/go2rtc/streams", timeoutMs)
        if (result.statusCode !in 200..299) {
            throw IOException("GET /go2rtc/streams -> HTTP ${result.statusCode}")
        }
        return firstTopLevelKey(result.body.toString(Charsets.UTF_8))
    }

    /**
     * HLS/fMP4 live URL for a go2rtc stream, proxied through Frigate. `mp4`
     * selects fMP4 output (preferred by ExoPlayer) instead of MPEG-TS.
     */
    fun hlsUrl(baseUrl: String, streamName: String): String =
        "$baseUrl/go2rtc/api/stream.m3u8?src=$streamName&mp4"

    companion object {
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
