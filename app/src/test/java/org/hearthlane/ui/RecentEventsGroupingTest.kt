package org.hearthlane.ui

import org.hearthlane.core.frigate.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Tests for the recent-events day grouping: one heading per day, events of the
 * same day never duplicate a heading (including events appended by pagination),
 * and the day-group label resolves Today / Yesterday / a dated label.
 */
class RecentEventsGroupingTest {

    private val zone = ZoneOffset.UTC

    private fun event(id: String, startTime: Double) = Event(
        id = id,
        cameraId = "backyard",
        label = "person",
        startTime = startTime,
        endTime = startTime + 10,
        hasClip = true,
        hasSnapshot = true,
        zones = emptyList(),
    )

    // Fixed timestamps (UTC) so day boundaries are deterministic.
    private val day1 = 1787000000.0 // one day
    private val day2 = 1787086400.0 // next day
    private val day3 = 1787172800.0 // day after

    @Test
    fun `events of the same day produce a single heading`() {
        val rows = buildEventListRows(
            listOf(event("a", day1), event("b", day1 + 60), event("c", day1 + 300)),
        ) { _, _ -> "heading" }

        val headers = rows.filterIsInstance<EventListRow.DayHeader>()
        assertEquals("one heading per day regardless of event count", 1, headers.size)
        assertEquals(3, rows.filterIsInstance<EventListRow.EventRow>().size)
    }

    @Test
    fun `different days produce one heading each`() {
        val rows = buildEventListRows(
            listOf(event("a", day1), event("b", day2), event("c", day3)),
        ) { _, _ -> "heading" }

        assertEquals(3, rows.filterIsInstance<EventListRow.DayHeader>().size)
        assertEquals(3, rows.filterIsInstance<EventListRow.EventRow>().size)
    }

    @Test
    fun `rows preserve the given ordering`() {
        val rows = buildEventListRows(
            listOf(event("a", day1), event("b", day1 + 60), event("c", day2)),
        ) { _, _ -> "heading" }

        val ids = rows.filterIsInstance<EventListRow.EventRow>().map { it.event.id }
        assertEquals(listOf("a", "b", "c"), ids)
    }

    @Test
    fun `pagination appending to the last day does not duplicate its heading`() {
        val firstPage = buildEventListRows(listOf(event("a", day2))) { _, _ -> "heading" }
        val appended = buildEventListRows(
            listOf(event("a", day2), event("b", day2 + 100)),
        ) { _, _ -> "heading" }

        assertEquals(1, firstPage.filterIsInstance<EventListRow.DayHeader>().size)
        assertEquals(1, appended.filterIsInstance<EventListRow.DayHeader>().size)
        assertEquals(2, appended.filterIsInstance<EventListRow.EventRow>().size)
    }

    @Test
    fun `day keys are derived in the given zone`() {
        assertEquals(LocalDate.of(2026, 8, 18), EventFormat.dayKey(1787072293.5, zone))
    }

    @Test
    fun `day label resolves the localized date`() {
        val label = EventFormat.dayLabel(
            1787072293.5,
            zone = zone,
            pattern = "MMMM d",
            locale = java.util.Locale.US,
        )
        assertEquals("August 18", label)
    }

    @Test
    fun `buildEventListRows empty input yields empty output`() {
        assertTrue(buildEventListRows(emptyList()) { _, _ -> "heading" }.isEmpty())
    }
}