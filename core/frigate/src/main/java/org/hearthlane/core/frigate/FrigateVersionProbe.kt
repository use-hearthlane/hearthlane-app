package org.hearthlane.core.frigate

/**
 * Probes Frigate's version endpoint over an injected [HttpGetter], so the same
 * request logic is shared by the local and the Tailscale transports.
 *
 * Success is defined by an HTTP 2xx response (enforced by the getter); the
 * response body is only used to derive a best-effort version string.
 */
class FrigateVersionProbe(private val getter: HttpGetter) {

    /**
     * Returns a best-effort Frigate version string.
     *
     * @throws Exception when the request fails (including non-2xx responses).
     */
    suspend fun probe(baseUrl: String, timeoutMs: Long): String {
        val body = getter.get("$baseUrl/api/version", timeoutMs)
        return parseVersion(body)
    }

    companion object {
        /**
         * `GET /api/version` returns `{"version":"0.15.1"}` in some versions and
         * the bare version text (for example `0.17.1-416a9b7`) in others. Parse
         * the JSON form when present and otherwise treat the trimmed body as the
         * version. A 2xx response is already a success regardless of the body.
         */
        fun parseVersion(body: String): String {
            val match = VERSION_PATTERN.find(body)
            if (match != null) {
                return match.groupValues[1]
            }
            return body.trim().ifEmpty { "unknown" }
        }

        private val VERSION_PATTERN = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
    }
}
