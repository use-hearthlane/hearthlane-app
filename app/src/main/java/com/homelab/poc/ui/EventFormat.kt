package com.homelab.poc.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Small, testable formatting helpers for the recent-events UI. Kept in one
 * place so duration/time are never formatted repeatedly across the UI.
 */
internal object EventFormat {

    private val TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    private val DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss")

    /**
     * Formats a duration in seconds as a compact label ("45s", "2m 05s",
     * "1h 03m"). Sub-second precision is dropped for display.
     */
    fun duration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "%dh %02dm".format(h, m)
            m > 0 -> "%dm %02ds".format(m, s)
            else -> "%ds".format(s)
        }
    }

    /**
     * Formats an epoch start time as a local "MMM d, HH:mm" label. [zone]
     * defaults to the device zone and is injectable for deterministic tests.
     */
    fun timeLabel(epochSeconds: Double, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME.format(Instant.ofEpochSecond(epochSeconds.toLong()).atZone(zone))

    /**
     * Formats an epoch timestamp as a full "MMM d, yyyy HH:mm:ss" label used by
     * the event-detail screen. Same testability contract as [timeLabel].
     */
    fun dateTimeLabel(epochSeconds: Double, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(Instant.ofEpochSecond(epochSeconds.toLong()).atZone(zone))

    /** Test-friendly fixed zone (UTC) so time labels are deterministic. */
    fun utcZone(): ZoneId = ZoneOffset.UTC
}
