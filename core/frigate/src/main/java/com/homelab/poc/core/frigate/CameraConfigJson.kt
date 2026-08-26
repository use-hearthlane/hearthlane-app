package com.homelab.poc.core.frigate

/**
 * Minimal dependency-free JSON extraction helpers used to parse the Frigate
 * `/api/config` payload without modeling it, keeping the module JVM-testable
 * without a JSON library.
 *
 * Only the operations required by camera discovery are exposed: locating a
 * top-level member, enumerating object keys, decoding strings, and parsing
 * booleans. Malformed input raises [IllegalArgumentException] so the caller
 * can surface a controlled error.
 */
internal object CameraConfigJson {

    /**
     * Returns the raw JSON text of the top-level member [name], or null when
     * the member is absent.
     *
     * @throws IllegalArgumentException when [json] is not a JSON object.
     */
    fun memberValue(json: String, name: String): String? {
        var i = skipWs(json, 0)
        expect(json, i, '{')
        i++
        while (true) {
            i = skipWs(json, i)
            if (i >= json.length) fail("unterminated object")
            if (json[i] == '}') return null
            val (key, keyEnd) = parseString(json, i)
            i = skipWs(json, keyEnd)
            expect(json, i, ':')
            val valueStart = skipWs(json, i + 1)
            val valueEnd = valueEnd(json, valueStart)
            if (key == name) return json.substring(valueStart, valueEnd)
            i = skipWs(json, valueEnd)
            if (i >= json.length) fail("unterminated object")
            when (json[i]) {
                ',' -> i++
                '}' -> return null
                else -> fail("expected ',' or '}' after member")
            }
        }
    }

    /**
     * Returns the keys of a JSON object in document order.
     *
     * @throws IllegalArgumentException when [json] is not a JSON object.
     */
    fun objectKeys(json: String): List<String> {
        val keys = ArrayList<String>()
        var i = skipWs(json, 0)
        expect(json, i, '{')
        i++
        while (true) {
            i = skipWs(json, i)
            if (i >= json.length) fail("unterminated object")
            if (json[i] == '}') return keys
            val (key, keyEnd) = parseString(json, i)
            keys.add(key)
            i = skipWs(json, keyEnd)
            expect(json, i, ':')
            i = valueEnd(json, skipWs(json, i + 1))
            i = skipWs(json, i)
            if (i >= json.length) fail("unterminated object")
            when (json[i]) {
                ',' -> i++
                '}' -> return keys
                else -> fail("expected ',' or '}' after member")
            }
        }
    }

    /** True when [raw] is a JSON object. */
    fun isObject(raw: String): Boolean {
        val start = skipWs(raw, 0)
        return start < raw.length && raw[start] == '{'
    }

    /**
     * Returns the raw JSON text of each element of a top-level array, in
     * document order.
     *
     * @throws IllegalArgumentException when [json] is not a JSON array.
     */
    fun arrayElements(json: String): List<String> {
        var i = skipWs(json, 0)
        expect(json, i, '[')
        i++
        val out = ArrayList<String>()
        while (true) {
            i = skipWs(json, i)
            if (i >= json.length) fail("unterminated array")
            if (json[i] == ']') return out
            val elementEnd = valueEnd(json, i)
            out.add(json.substring(i, elementEnd))
            i = skipWs(json, elementEnd)
            if (i >= json.length) fail("unterminated array")
            when (json[i]) {
                ',' -> i++
                ']' -> return out
                else -> fail("expected ',' or ']' after element")
            }
        }
    }

    /**
     * Decodes a JSON string token; returns null when [raw] is not a JSON
     * string (including the `null` literal).
     */
    fun stringValue(raw: String): String? {
        val start = skipWs(raw, 0)
        if (start >= raw.length || raw[start] != '"') return null
        return parseString(raw, start).first
    }

    /** Parses a JSON `true`/`false` literal; returns null otherwise. */
    fun booleanValue(raw: String): Boolean? = when (raw.trim()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    /** Parses a JSON number literal; returns null when [raw] is not a number. */
    fun numberValue(raw: String): Double? = raw.trim().toDoubleOrNull()

    private fun parseString(json: String, start: Int): Pair<String, Int> {
        if (start >= json.length || json[start] != '"') fail("expected string")
        val out = StringBuilder()
        var i = start + 1
        while (i < json.length) {
            when (val c = json[i]) {
                '"' -> return out.toString() to (i + 1)
                '\\' -> {
                    if (i + 1 >= json.length) fail("unterminated string escape")
                    when (val e = json[i + 1]) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (i + 5 >= json.length) fail("truncated \\u escape")
                            val hex = json.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                                ?: fail("invalid \\u escape: \\u$hex")
                            out.append(code.toChar())
                            i += 4
                        }
                        else -> fail("invalid escape: \\$e")
                    }
                    i += 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        fail("unterminated string")
    }

    private fun valueEnd(json: String, start: Int): Int {
        if (start >= json.length) fail("missing value")
        return when (val c = json[start]) {
            '{' -> skipContainer(json, start, '{', '}')
            '[' -> skipContainer(json, start, '[', ']')
            '"' -> parseString(json, start).second
            else -> {
                var i = start
                while (i < json.length && !json[i].isJsonDelimiter()) i++
                i
            }
        }
    }

    private fun skipContainer(json: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var i = start
        while (i < json.length) {
            when (json[i]) {
                '"' -> i = parseString(json, i).second - 1
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        fail("unterminated container")
    }

    private fun skipWs(s: String, i: Int): Int {
        var j = i
        while (j < s.length && s[j].isWhitespace()) j++
        return j
    }

    private fun expect(s: String, i: Int, c: Char) {
        if (i >= s.length || s[i] != c) fail("expected '$c'")
    }

    private fun Char.isJsonDelimiter(): Boolean =
        this == ',' || this == '}' || this == ']' || this.isWhitespace()

    private fun fail(msg: String): Nothing =
        throw IllegalArgumentException("invalid JSON: $msg")
}
