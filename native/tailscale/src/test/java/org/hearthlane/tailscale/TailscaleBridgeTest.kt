package org.hearthlane.tailscale

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the gomobile empty-body quirk: gomobile maps a Go byte
 * slice with a nil data pointer to Java null (`go_seq_to_java_bytearray`
 * returns NULL when `s.ptr == NULL`), so a valid HTTP response without content
 * (204, or a 200 with no body) used to NPE at `getBody()`. [TailscaleBridge]
 * normalizes the boundary so an empty body is a zero-length non-null
 * [ByteArray] for every HTTP consumer.
 */
class TailscaleBridgeTest {

    private fun normalize(body: ByteArray?) = toBytesResult(
        statusCode = 204,
        contentType = null,
        finalUrl = "http://relay",
        body = body,
    )

    @Test
    fun `null body becomes a non-null empty byte array`() {
        val result = normalize(null)

        assertArrayEquals("a null gomobile body must become ByteArray(0)", ByteArray(0), result.body)
        assertEquals(204, result.statusCode)
    }

    @Test
    fun `empty non-null body stays an empty byte array`() {
        val result = normalize(ByteArray(0))

        assertArrayEquals(ByteArray(0), result.body)
    }

    @Test
    fun `present body content is preserved`() {
        val result = toBytesResult(200, "application/json", "http://relay", "hello".toByteArray())

        assertArrayEquals("hello".toByteArray(), result.body)
        assertEquals(200, result.statusCode)
        assertEquals("application/json", result.contentType)
    }

    @Test
    fun `error status with null body keeps the status and a safe body`() {
        val result = toBytesResult(500, null, "http://relay", null)

        assertEquals(500, result.statusCode)
        assertArrayEquals(ByteArray(0), result.body)
    }
}