package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.data.repository.CalendarEntry
import com.nuvio.tv.data.repository.groupByLocalDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarGroupingTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `the same instant falls on different local days in Madrid and New York`() {
        val entry = entry("tt1", "2026-11-15T02:00:00Z")
        val today = LocalDate.of(2026, 11, 14)

        val madridDays = groupByLocalDay(listOf(entry), madrid, today)
        val newYorkDays = groupByLocalDay(listOf(entry), newYork, today)

        assertEquals(LocalDate.of(2026, 11, 15), madridDays.single().date)
        assertEquals(LocalDate.of(2026, 11, 14), newYorkDays.single().date)
    }

    @Test
    fun `the window starts today and excludes day thirty`() {
        val today = LocalDate.of(2026, 11, 14)
        val entries = listOf(
            entry("past", "2026-11-13T22:00:00Z"),
            entry("today", "2026-11-14T12:00:00Z"),
            entry("last", "2026-12-13T12:00:00Z"),
            entry("outside", "2026-12-14T12:00:00Z")
        )

        val ids = groupByLocalDay(entries, madrid, today).flatMap { day -> day.entries.map { it.contentId } }

        assertEquals(listOf("today", "last"), ids)
    }

    @Test
    fun `entries inside a day are ordered by time`() {
        val today = LocalDate.of(2026, 11, 14)
        val entries = listOf(
            entry("late", "2026-11-15T22:00:00Z"),
            entry("early", "2026-11-15T09:00:00Z"),
            entry("middle", "2026-11-15T15:30:00Z")
        )

        val day = groupByLocalDay(entries, madrid, today).single()

        assertEquals(listOf("early", "middle", "late"), day.entries.map { it.contentId })
    }

    @Test
    fun `only days with entries are returned`() {
        val today = LocalDate.of(2026, 11, 14)
        val entries = listOf(
            entry("a", "2026-11-14T20:00:00Z"),
            entry("b", "2026-11-18T20:00:00Z")
        )

        val days = groupByLocalDay(entries, madrid, today)

        assertEquals(
            listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 18)),
            days.map { it.date }
        )
    }

    @Test
    fun `an empty list produces no days`() {
        assertTrue(groupByLocalDay(emptyList(), madrid, LocalDate.of(2026, 11, 14)).isEmpty())
    }

    @Test
    fun `a hidden movie key does not hide a series entry`() {
        val entry = entry("777", "2026-11-15T02:00:00Z")
        assertFalse(isHiddenCalendarEntry(entry, setOf("movie:777")))
        assertTrue(isHiddenCalendarEntry(entry, setOf("series:777")))
        assertTrue(isHiddenCalendarEntry(entry, setOf("tv:777")))
    }

    private fun entry(contentId: String, instant: String) = CalendarEntry(
        contentId = contentId,
        seriesName = "Show $contentId",
        poster = null,
        season = 1,
        episode = 1,
        episodeTitle = null,
        airInstant = Instant.parse(instant),
        hasTime = true
    )
}
