package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.WatchProgress
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NotflixPersonalRowsTest {
    private fun entry(id: String, at: Long, type: String = "movie", base: String? = "https://addon.invalid/$id") =
        LibraryEntry(id = id, type = type, name = "Title $id", poster = null, background = null, logo = null, description = null,
            releaseInfo = null, imdbRating = null, genres = emptyList(), addonBaseUrl = base, listedAt = at)
    private fun progress(id: String, at: Long, type: String = "movie") = WatchProgress(contentId = id, contentType = type, name = "Seen $id",
        poster = null, backdrop = null, logo = null, videoId = id, season = null, episode = null, episodeTitle = null,
        position = 1, duration = 10, lastWatched = at)
    private fun meta(id: String, type: ContentType = ContentType.MOVIE, released: String? = null) = MetaPreview(id = id, type = type,
        name = "Rec $id", poster = null, posterShape = PosterShape.POSTER, background = null, logo = null, description = null,
        releaseInfo = null, imdbRating = null, genres = emptyList(), released = released)

    @Test fun `my list is newest first, unique, limited and keeps each title's addon`() {
        val list = notflixMyList(listOf(entry("a", 1), entry("b", 3), entry("a", 2), entry("s", 4, "series")) + (10..40).map { entry("x$it", 0) })
        assertEquals(listOf("s", "b", "a"), list.take(3).map { it.id })
        assertEquals(ContentType.SERIES, list.first().type)
        assertEquals("https://addon.invalid/s", list.first().sourceAddonBaseUrl)
        assertEquals(NOTFLIX_PERSONAL_ROW_LIMIT, list.size)
        assertTrue(notflixMyList(emptyList()).isEmpty())
    }

    @Test fun `anchor is the last watched movie or series, never another type or a blank id`() {
        assertEquals("s1", notflixAnchor(listOf(progress("m1", 10), progress("s1", 30, "series"), progress("c1", 50, "channel"),
            progress("", 60))).let { it?.contentId })
        assertNull(notflixAnchor(emptyList()))
        assertNull(notflixAnchor(listOf(progress("c1", 5, "channel"))))
    }

    @Test fun `candidates drop the anchor, watched, unreleased when hidden and duplicates`() {
        val today = LocalDate.of(2026, 11, 6)
        val candidates = listOf(meta("tt-anchor"), meta("tt-seen"), meta("tt-show", ContentType.SERIES), meta("tt-future", released = "2027-05-01"),
            meta("tt-ok"), meta("TT-OK"), meta("tt-ok2"))
        val out = notflixBecauseCandidates(candidates, setOf("tt-anchor"), setOf("tt-seen"), setOf("tt-show"), hideUnreleased = true, today = today)
        assertEquals(listOf("tt-ok", "tt-ok2"), out.map { it.id })
        val withFuture = notflixBecauseCandidates(candidates, setOf("tt-anchor"), emptySet(), emptySet(), hideUnreleased = false, today = today)
        assertTrue(withFuture.any { it.id == "tt-future" })
        assertEquals(NOTFLIX_PERSONAL_ROW_LIMIT, notflixBecauseCandidates((1..50).map { meta("tt$it") }, emptySet(), emptySet(), emptySet(), false, today).size)
    }

    @Test fun `no titles means no row, otherwise a ready row with its own key and title`() {
        assertNull(notflixPersonalRow(NOTFLIX_MY_LIST_KEY, "My list", emptyList()))
        val row = requireNotNull(notflixPersonalRow(NOTFLIX_BECAUSE_KEY, "Because you watched X", listOf(meta("tt1"))))
        assertEquals(NOTFLIX_BECAUSE_KEY, row.key); assertEquals("Because you watched X", row.title)
        assertEquals(NotflixRowPhase.READY, row.phase); assertEquals(listOf("tt1"), row.catalog?.items?.map { it.id })
        assertFalse(row.catalog!!.hasMore)
    }
}
