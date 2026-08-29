package org.hearthlane.ui.locations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [DeviceColorResolver]: the color is derived deterministically from
 * the stable deviceId, distributes across the palette, and never depends on
 * the nickname (the resolver only ever sees the device id).
 */
class DeviceColorResolverTest {

    @Test
    fun `same device id always resolves to the same color`() {
        assertEquals(
            DeviceColorResolver.colorFor("hearthlane-ab12cd34"),
            DeviceColorResolver.colorFor("hearthlane-ab12cd34"),
        )
        assertEquals(
            DeviceColorResolver.colorFor("d1"),
            DeviceColorResolver.colorFor("d1"),
        )
    }

    @Test
    fun `resolver is deterministic across calls`() {
        repeat(50) {
            val id = "device-$it"
            assertEquals("stableIndex must be deterministic", DeviceColorResolver.stableIndex(id), DeviceColorResolver.stableIndex(id))
        }
    }

    @Test
    fun `different device ids distribute across the palette`() {
        val ids = (0 until 100).map { "device-$it" }
        val usedIndices = ids.map { DeviceColorResolver.stableIndex(it) }.distinct()
        assertTrue(
            "a hundred ids should use most of the palette, got ${usedIndices.size}",
            usedIndices.size >= DeviceColorResolver.palette.size / 2,
        )
        for (index in usedIndices) {
            assertTrue("index must be in palette bounds", index in DeviceColorResolver.palette.indices)
        }
    }

    @Test
    fun `palette colors are visually distinct`() {
        val distinct = DeviceColorResolver.palette.distinct()
        assertEquals(
            "the palette must not contain duplicate colors",
            DeviceColorResolver.palette.size,
            distinct.size,
        )
    }

    @Test
    fun `nickname is not part of the resolution`() {
        // The resolver has no nickname input: the color depends only on the
        // stable device id, so a label change never shifts the color.
        val id = "hearthlane-abc12345"
        val color = DeviceColorResolver.colorFor(id)
        assertEquals(color, DeviceColorResolver.colorFor(id))
        // Calling again after a label would-be change still resolves identically.
        assertEquals(DeviceColorResolver.stableIndex(id), DeviceColorResolver.stableIndex(id))
    }
}