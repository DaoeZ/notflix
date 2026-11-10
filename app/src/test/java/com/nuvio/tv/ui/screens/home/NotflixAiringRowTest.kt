package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.PosterShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixAiringRowTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun item(
        id: String,
        instant: Instant?,
        hasAired: Boolean = false,
        released: String? = instant?.toString()
    ) = ContinueWatchingItem.NextUp(
        NextUpInfo(
            contentId = id,
            contentType = "series",
            name = "Series $id",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = "$id:1:1",
            season = 1,
            episode = 1,
            episodeTitle = "Episode",
            thumbnail = null,
            released = released,
            hasAired = hasAired,
            lastWatched = 1L,
            sortTimestamp = 1L,
            releaseTimestamp = instant?.toEpochMilli()
        )
    )

    @Test
    fun `window includes today and day six but excludes day seven`() {
        val result = airingThisWeek(
            upcoming = listOf(
                item("today", Instant.parse("2026-11-09T13:00:00Z")),
                item("day6", Instant.parse("2026-11-15T13:00:00Z")),
                item("day7", Instant.parse("2026-11-16T13:00:00Z"))
            ),
            now = now,
            zone = madrid
        )

        assertEquals(listOf("today", "day6"), result.map { it.info.contentId })
    }

    @Test
    fun `entries are sorted by date ascending`() {
        val result = airingThisWeek(
            upcoming = listOf(
                item("day3", Instant.parse("2026-11-12T13:00:00Z")),
                item("today", Instant.parse("2026-11-09T13:00:00Z")),
                item("day1", Instant.parse("2026-11-10T13:00:00Z"))
            ),
            now = now,
            zone = madrid
        )

        assertEquals(listOf("today", "day1", "day3"), result.map { it.info.contentId })
    }

    @Test
    fun `limit is twenty after sorting`() {
        val entries = (1..25).map { index ->
            item("id$index", Instant.parse("2026-11-10T13:00:00Z").plusSeconds(index.toLong()))
        }

        assertEquals(20, airingThisWeek(entries, now, madrid).size)
    }

    @Test
    fun `entries without date or already aired are excluded`() {
        val result = airingThisWeek(
            upcoming = listOf(
                item("no-date", null),
                item("already-aired", Instant.parse("2026-11-09T13:00:00Z"), hasAired = true),
                item("valid", Instant.parse("2026-11-10T13:00:00Z"))
            ),
            now = now,
            zone = madrid
        )

        assertEquals(listOf("valid"), result.map { it.info.contentId })
    }

    @Test
    fun `local date depends on the device zone`() {
        val instant = Instant.parse("2026-11-10T02:00:00Z")
        val newYork = ZoneId.of("America/New_York")
        val entry = item("zone", instant)

        val madridEntry = airingThisWeek(listOf(entry), now, madrid).single()
        val newYorkEntry = airingThisWeek(listOf(entry), now, newYork).single()

        assertEquals(LocalDate.of(2026, 11, 10), madridEntry.localDate)
        assertEquals(LocalDate.of(2026, 11, 9), newYorkEntry.localDate)
        assertEquals(AiringDayLabel.TODAY, newYorkEntry.dayLabel)
        assertEquals(AiringDayLabel.TOMORROW, madridEntry.dayLabel)
    }

    @Test
    fun `day labels distinguish today tomorrow and weekday`() {
        val result = airingThisWeek(
            upcoming = listOf(
                item("today", Instant.parse("2026-11-09T13:00:00Z")),
                item("tomorrow", Instant.parse("2026-11-10T13:00:00Z")),
                item("weekday", Instant.parse("2026-11-12T13:00:00Z"))
            ),
            now = now,
            zone = madrid
        )

        assertEquals(AiringDayLabel.TODAY, result[0].dayLabel)
        assertEquals(AiringDayLabel.TOMORROW, result[1].dayLabel)
        assertEquals(AiringDayLabel.WEEKDAY, result[2].dayLabel)
        assertTrue(result.all { it.info.contentType == "series" })
    }
}
