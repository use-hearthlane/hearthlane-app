package com.homelab.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class EventFormatTest {

    @Test
    fun `duration formats plain seconds`() {
        assertEquals("45s", EventFormat.duration(45.0))
    }

    @Test
    fun `duration formats minutes and seconds`() {
        assertEquals("2m 05s", EventFormat.duration(125.0))
    }

    @Test
    fun `duration formats hours and minutes`() {
        assertEquals("1h 03m", EventFormat.duration(3780.0))
    }

    @Test
    fun `duration handles zero`() {
        assertEquals("0s", EventFormat.duration(0.0))
    }

    @Test
    fun `duration truncates sub-second precision for display`() {
        assertEquals("9s", EventFormat.duration(9.7))
    }

    @Test
    fun `timeLabel formats the epoch in english layout and month names`() {
        assertEquals(
            "Aug 18, 16:58",
            EventFormat.timeLabel(
                1787072293.5,
                zone = ZoneOffset.UTC,
                pattern = "MMM d, HH:mm",
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun `dateTimeLabel formats the epoch with date, year and seconds in english`() {
        assertEquals(
            "Aug 18, 2026 16:58:13",
            EventFormat.dateTimeLabel(
                1787072293.5,
                zone = ZoneOffset.UTC,
                pattern = "MMM d, yyyy HH:mm:ss",
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun `timeLabel formats the epoch in the pt-br layout and month names`() {
        assertEquals(
            "18 de ago., 16:58",
            EventFormat.timeLabel(
                1787072293.5,
                zone = ZoneOffset.UTC,
                pattern = "d 'de' MMM, HH:mm",
                locale = Locale.forLanguageTag("pt-BR"),
            ),
        )
    }

    @Test
    fun `dateTimeLabel formats the epoch with date, year and seconds in pt-br`() {
        assertEquals(
            "18 de ago. de 2026, 16:58:13",
            EventFormat.dateTimeLabel(
                1787072293.5,
                zone = ZoneOffset.UTC,
                pattern = "d 'de' MMM 'de' yyyy, HH:mm:ss",
                locale = Locale.forLanguageTag("pt-BR"),
            ),
        )
    }
}
