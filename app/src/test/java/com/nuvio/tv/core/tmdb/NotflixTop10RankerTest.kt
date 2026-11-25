package com.nuvio.tv.core.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixTop10RankerTest {
    @Test
    fun enforcesOnePerSagaAndCapsExclusivePlatformDominance() {
        val candidates = listOf(
            Top10RankedCandidate(1, "Dune: Part One", listOf("Netflix"), 1.9),
            Top10RankedCandidate(2, "Dune: Part Two", listOf("Netflix"), 1.8),
            Top10RankedCandidate(3, "Show A", listOf("Netflix"), 1.7),
            Top10RankedCandidate(4, "Show B", listOf("Netflix"), 1.6),
            Top10RankedCandidate(5, "Show C", listOf("Netflix"), 1.5),
            Top10RankedCandidate(6, "Show D", listOf("Netflix"), 1.4),
            Top10RankedCandidate(7, "Show E", listOf("Netflix"), 1.3),
            Top10RankedCandidate(8, "Prime Hit 1", listOf("Prime Video"), 1.2),
            Top10RankedCandidate(9, "Disney Hit 1", listOf("Disney+"), 1.1),
            Top10RankedCandidate(10, "HBO Hit 1", listOf("HBO Max"), 1.0),
            Top10RankedCandidate(11, "Apple Hit 1", listOf("Apple TV+"), 0.9),
            Top10RankedCandidate(12, "Sky Hit 1", listOf("SkyShowtime"), 0.8),
            Top10RankedCandidate(13, "Multi Hit 1", listOf("Netflix", "Prime Video"), 0.7),
        )
        val result = NotflixTop10Ranker.selectBalancedTop10(candidates, limit = 10, maxExclusivePerPlatform = 4)
        assertEquals(10, result.size)
        // Only one Dune entry allowed
        assertEquals(1, result.count { it.title.startsWith("Dune") })
        // Max 4 Netflix-exclusive titles when other platforms are available
        val netflixExclusive = result.count { it.platforms == listOf("Netflix") }
        assertTrue("Expected <= 4 Netflix-exclusive titles, got $netflixExclusive", netflixExclusive <= 4)
        // Multi-platform title is not penalized by single-platform exclusive cap
        assertTrue(result.any { it.tmdbId == 13 })
    }

    @Test
    fun parsesLatestWeekMoviesAndSeriesFromNetflixTudumHtml() {
        val sample = """
            "PulseTop10ItemEntity:top10-old":{"__typename":"PulseTop10ItemEntity","weekEndDate":"2026-11-08","category":"MOVIES","weeklyRank":1,"top10":{"title":"Old Movie"}},
            "PulseTop10ItemEntity:top10-m1":{"__typename":"PulseTop10ItemEntity","weekEndDate":"2026-11-15","category":"MOVIES","weeklyRank":2,"top10":{"title":"The Good Lawyer"}},
            "PulseTop10ItemEntity:top10-m2":{"__typename":"PulseTop10ItemEntity","weekEndDate":"2026-11-15","category":"MOVIES","weeklyRank":1,"top10":{"title":"Collision Course"}},
            "PulseTop10ItemEntity:top10-s1":{"__typename":"PulseTop10ItemEntity","weekEndDate":"2026-11-15","category":"SERIES","weeklyRank":1,"top10":{"title":"Black Doves: Season 2"}},
            "PulseTop10ItemEntity:top10-s2":{"__typename":"PulseTop10ItemEntity","weekEndDate":"2026-11-15","category":"SERIES","weeklyRank":2,"top10":{"title":"Black Doves: Season 1"}},
            "PulseTop10ItemEntity:top10-s3":{"__typename":"PulseTop10ItemEntity","weekEndDate":"2026-11-15","category":"SERIES","weeklyRank":3,"top10":{"title":"Lupin: Part 4"}}
        """.trimIndent()
        val parsed = NotflixTop10Ranker.parseNetflixTudumHtml(sample)
        assertEquals(listOf("Collision Course", "The Good Lawyer"), parsed.movies)
        assertEquals(listOf("Black Doves", "Lupin"), parsed.series)
    }

    @Test
    fun penalizesOldCatalogMoviesComparedToRecentReleases() {
        val recent = NotflixTop10Ranker.recencyMultiplier(2026, isTv = false, currentYear = 2026)
        val old2012 = NotflixTop10Ranker.recencyMultiplier(2012, isTv = false, currentYear = 2026)
        val old2006 = NotflixTop10Ranker.recencyMultiplier(2006, isTv = false, currentYear = 2026)
        assertTrue(recent > 1.0 && old2012 < 0.3 && old2006 < 0.3)
        assertTrue(0.5 * recent > 1.0 * old2012)
    }
}
