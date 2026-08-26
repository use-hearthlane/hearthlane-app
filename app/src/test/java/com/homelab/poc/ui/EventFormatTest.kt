package com.homelab.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

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
    fun `timeLabel formats the epoch in a fixed zone`() {
        assertEquals(
            "Aug 18, 16:58",
            EventFormat.timeLabel(1787072293.5, ZoneOffset.UTC),
        )
    }

    @Test
    fun `dateTimeLabel formats the epoch with date, year and seconds in a fixed zone`() {
        assertEquals(
            "Aug 18, 2026 16:58:13",
            EventFormat.dateTimeLabel(1787072293.5, ZoneOffset.UTC),
        )
    }
}
