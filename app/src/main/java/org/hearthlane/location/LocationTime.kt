package org.hearthlane.location

import android.location.Location

/**
 * Pure time helpers for the location source.
 *
 * Duration and age measurements always use the monotonic clock
 * (Location.getElapsedRealtimeNanos / SystemClock.elapsedRealtimeNanos),
 * never the wall clock (Location.getTime): the wall clock can jump, so it is
 * only recorded as payload metadata and never used to measure acquisition
 * latency or fix freshness.
 */
internal object LocationTime {

    /** Age of a position captured at [recordedAtElapsedNanos], in ms. */
    fun ageMs(recordedAtElapsedNanos: Long, nowElapsedNanos: Long): Long =
        ((nowElapsedNanos - recordedAtElapsedNanos) / 1_000_000L).coerceAtLeast(0L)

    /** Duration between two monotonic timestamps, in ms. */
    fun durationMs(startElapsedNanos: Long, endElapsedNanos: Long): Long =
        ((endElapsedNanos - startElapsedNanos) / 1_000_000L).coerceAtLeast(0L)

    /** Age of [location] at the moment [nowElapsedNanos] was sampled, in ms. */
    fun ageMs(location: Location, nowElapsedNanos: Long): Long =
        ageMs(location.elapsedRealtimeNanos, nowElapsedNanos)
}