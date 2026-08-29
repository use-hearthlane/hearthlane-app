package org.hearthlane.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for the location source's monotonic-clock helpers. No
 * Robolectric: these helpers must be deterministic without an Android runtime.
 */
class LocationTimeTest {

    @Test
    fun `age converts a nanos delta to milliseconds`() {
        assertEquals(5_000L, LocationTime.ageMs(1_000_000_000L, 6_000_000_000L))
    }

    @Test
    fun `age clamps a future-recorded timestamp to zero`() {
        assertEquals(0L, LocationTime.ageMs(9_000_000_000L, 1_000_000_000L))
    }

    @Test
    fun `duration converts a nanos delta to milliseconds`() {
        assertEquals(1_250L, LocationTime.durationMs(0L, 1_250_000_000L))
    }

    @Test
    fun `duration clamps to zero when end precedes start`() {
        assertEquals(0L, LocationTime.durationMs(5_000_000_000L, 1_000_000_000L))
    }

    @Test
    fun `sub-millisecond deltas collapse to zero`() {
        assertEquals(0L, LocationTime.ageMs(1_000_000_000L, 1_000_000_500L))
    }
}