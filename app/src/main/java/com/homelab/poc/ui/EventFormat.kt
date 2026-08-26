package com.homelab.poc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.homelab.poc.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Small, testable formatting helpers for the recent-events UI. Kept in one
 * place so duration/time are never formatted repeatedly across the UI.
 *
 * Time and datetime labels are locale-aware: the layout pattern comes from an
 * Android string resource ([R.string.event_time_format] /
 * [R.string.event_datetime_format]) selected by the device locale, and the
 * formatter resolves month names in that same locale. The pure formatting
 * logic lives in [EventFormat] (unit-testable with an explicit [Locale]) while
 * the [formattedEventTime]/[formattedEventDateTime] wrappers read the
 * resources, keeping locale resolution in the presentation layer.
 */
internal object EventFormat {

    private const val DEFAULT_TIME_PATTERN = "MMM d, HH:mm"
    private const val DEFAULT_DATE_TIME_PATTERN = "MMM d, yyyy HH:mm:ss"

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
     * Formats an epoch start time as a "MMM d, HH:mm"-style label. [pattern]
     * is the localized layout (English default); [locale] resolves month names
     * and [zone] defaults to the device zone (injectable for deterministic
     * tests).
     */
    fun timeLabel(
        epochSeconds: Double,
        zone: ZoneId = ZoneId.systemDefault(),
        pattern: String = DEFAULT_TIME_PATTERN,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT),
    ): String =
        DateTimeFormatter.ofPattern(pattern, locale)
            .format(Instant.ofEpochSecond(epochSeconds.toLong()).atZone(zone))

    /**
     * Formats an epoch timestamp as a "MMM d, yyyy HH:mm:ss"-style label used
     * by the event-detail screen. Same testability contract as [timeLabel].
     */
    fun dateTimeLabel(
        epochSeconds: Double,
        zone: ZoneId = ZoneId.systemDefault(),
        pattern: String = DEFAULT_DATE_TIME_PATTERN,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT),
    ): String =
        DateTimeFormatter.ofPattern(pattern, locale)
            .format(Instant.ofEpochSecond(epochSeconds.toLong()).atZone(zone))

    /** Test-friendly fixed zone (UTC) so time labels are deterministic. */
    fun utcZone(): ZoneId = ZoneOffset.UTC
}

/** Device locale currently in effect for Compose resource resolution. */
@Composable
private fun currentLocale(): Locale = LocalLocale.current.platformLocale

/**
 * Localized time label for an event start time, resolved for the device locale
 * via [R.string.event_time_format]. [zone] defaults to the device zone and is
 * injectable for deterministic tests.
 */
@Composable
internal fun formattedEventTime(
    epochSeconds: Double,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    EventFormat.timeLabel(
        epochSeconds,
        zone = zone,
        pattern = stringResource(R.string.event_time_format),
        locale = currentLocale(),
    )

/**
 * Localized datetime label for an event start time, resolved for the device
 * locale via [R.string.event_datetime_format]. [zone] defaults to the device
 * zone and is injectable for deterministic tests.
 */
@Composable
internal fun formattedEventDateTime(
    epochSeconds: Double,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    EventFormat.dateTimeLabel(
        epochSeconds,
        zone = zone,
        pattern = stringResource(R.string.event_datetime_format),
        locale = currentLocale(),
    )
