package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M31: the Notflix search grid merges every catalog without inventing a second search path. */
class NotflixSearchResultsTest {
    private fun item(id: String) = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        name = "Title $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    private fun row(key: String, vararg items: MetaPreview) = CatalogRow(
        addonId = "addon",
        addonName = "Addon",
        addonBaseUrl = "https://addon.invalid",
        catalogId = key,
        catalogName = key,
        type = ContentType.MOVIE,
        items = items.toList()
    )

    @Test
    fun mergesRowsRemovesDuplicatesAndKeepsTheFirstAppearanceOrder() {
        val result = notflixSearchResults(
            listOf(
                row("a", item("1"), item("2")),
                row("b", item("2"), item("3"), item("1")),
                row("c", item("4"))
            )
        )

        assertEquals(listOf("1", "2", "3", "4"), result.map { it.id })
    }

    @Test
    fun withAQueryTheBestMatchLeadsAcrossRowsInsteadOfTheMovieRowFirst() {
        fun titled(id: String, name: String, rating: Float? = null) = item(id).copy(name = name, imdbRating = rating)
        val movies = row("movies", titled("m1", "Breaking Bad: El Camino", 7.3f), titled("m2", "Breaking Dawn"),
            titled("m3", "Crepúsculo: Amanecer"), titled("m4", "Making of: Breaking Bad"))
        val series = row("series", titled("s1", "Breaking Bad", 9.5f), titled("s2", "Prison Break"))

        val result = notflixSearchResults(listOf(movies, series), "breaking")

        assertEquals(listOf("s1", "m1", "m2", "m4", "s2", "m3"), result.map { it.id })
    }

    @Test
    fun titleMatchRanks() {
        assertEquals(0, notflixTitleMatchRank("Breaking Bad", "breaking bad"))
        assertEquals(1, notflixTitleMatchRank("Breaking Bad", "breaking"))
        assertEquals(2, notflixTitleMatchRank("Making of: Breaking Bad", "breaking"))
        assertEquals(3, notflixTitleMatchRank("Icebreakers", "breaker"))
        assertEquals(4, notflixTitleMatchRank("The Wolf of Wall Street", "wolf wall"))
        assertEquals(5, notflixTitleMatchRank("Crepúsculo", "breaking"))
    }

    @Test
    fun emptyRowsProduceAnEmptyResult() {
        assertTrue(notflixSearchResults(emptyList()).isEmpty())
        assertTrue(notflixSearchResults(listOf(row("a"))).isEmpty())
    }

    @Test
    fun skeletonPlaceholdersNeverBecomeResults() {
        val result = notflixSearchResults(
            listOf(row("a", item("__placeholder_skeleton_0_1"), item("real")))
        )

        assertEquals(listOf("real"), result.map { it.id })
    }

    @Test
    fun exploreKeyIncludesTheTypeSoEqualIdsFromDifferentCatalogsDoNotCollide() {
        val movie = item("shared")
        val series = item("shared").copy(type = ContentType.SERIES, rawType = "series")

        assertEquals("movie:shared", notflixDiscoverKey(movie))
        assertEquals("series:shared", notflixDiscoverKey(series))
        assertNotEquals(notflixDiscoverKey(movie), notflixDiscoverKey(series))
    }
}
