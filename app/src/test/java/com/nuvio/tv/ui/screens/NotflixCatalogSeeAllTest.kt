package com.nuvio.tv.ui.screens

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryPosterKeys
import com.nuvio.tv.ui.screens.home.homeItemStatusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M49: reglas propias de «Ver todo» (etiqueta del add-on, vistos y claves de la cuadrícula). */
class NotflixCatalogSeeAllTest {

    private fun meta(id: String, type: String = "movie", name: String = "Título $id"): MetaPreview =
        MetaPreview(
            id = id,
            type = ContentType.fromString(type),
            rawType = type,
            name = name,
            poster = "https://example.com/$id.jpg",
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList()
        )

    // ─── Etiqueta de la colección / add-on ───

    @Test
    fun theAddonNameIsShownWhenTheSettingAllowsIt() {
        assertEquals("Stremio", notflixCatalogCollectionLabel("  Stremio ", enabled = true))
    }

    @Test
    fun theAddonNameIsHiddenByTheSettingOrWhenItIsBlank() {
        assertNull(notflixCatalogCollectionLabel("Stremio", enabled = false))
        assertNull(notflixCatalogCollectionLabel("   ", enabled = true))
        assertNull(notflixCatalogCollectionLabel(null, enabled = true))
    }

    // ─── Vistos ───

    @Test
    fun homeWatchedStatusMarksMoviesAndSeries() {
        val status = mapOf(
            homeItemStatusKey("tt1", "movie") to true,
            homeItemStatusKey("tt2", "series") to true
        )
        assertTrue(watched(meta("tt1"), status = status))
        assertTrue(watched(meta("tt2", "series"), status = status))
        assertFalse(watched(meta("tt3"), status = status))
    }

    @Test
    fun searchModeUsesTheSearchSetsAndSeparatesMoviesFromSeries() {
        val movies = setOf("tt1")
        val series = setOf("tt2")
        assertTrue(
            watched(meta("tt1"), searchMode = true, searchMovies = movies, searchSeries = series)
        )
        assertTrue(
            watched(meta("tt2", "series"), searchMode = true, searchMovies = movies, searchSeries = series)
        )
        assertTrue(
            watched(meta("tt2", "tv"), searchMode = true, searchMovies = movies, searchSeries = series)
        )
        assertFalse(
            watched(meta("tt2"), searchMode = true, searchMovies = movies, searchSeries = series)
        )
        assertFalse(
            watched(meta("tt1", "series"), searchMode = true, searchMovies = movies, searchSeries = series)
        )
    }

    @Test
    fun anUnknownTypeIsNeverWatchedWithoutAnExplicitStatus() {
        assertFalse(watched(meta("x", "channel"), status = emptyMap()))
    }

    // ─── Claves de la cuadrícula ───

    @Test
    fun catalogKeysAreStableAndUniqueWithDuplicates() {
        val keys = notflixCategoryPosterKeys(listOf(meta("1"), meta("2"), meta("1")))
        assertEquals(listOf("movie:1", "movie:2", "movie:1#1"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun catalogKeysSurviveAReloadOfTheSameItems() {
        val first = notflixCategoryPosterKeys(listOf(meta("1"), meta("2", "series")))
        val second = notflixCategoryPosterKeys(listOf(meta("1"), meta("2", "series")))
        assertEquals(first, second)
    }

    private fun watched(
        item: MetaPreview,
        searchMode: Boolean = false,
        searchMovies: Set<String> = emptySet(),
        searchSeries: Set<String> = emptySet(),
        status: Map<String, Boolean> = emptyMap()
    ): Boolean = notflixCatalogItemWatched(
        item = item,
        searchMode = searchMode,
        searchWatchedMovieIds = searchMovies,
        searchWatchedSeriesIds = searchSeries,
        movieWatchedStatus = status
    )
}
