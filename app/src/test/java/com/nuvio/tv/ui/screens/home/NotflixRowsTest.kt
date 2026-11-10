package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixRowsTest {
    private fun movie(id: String) = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        rawType = "movie",
        name = "Movie $id",
        poster = "https://images.metahub.space/poster/small/$id/img",
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    private fun series(id: String) = movie(id).copy(type = ContentType.SERIES, rawType = "series", name = "Series $id")

    private fun catalog(vararg items: MetaPreview) = CatalogRow(
        addonId = "addon",
        addonName = "Addon",
        addonBaseUrl = "https://addon.invalid",
        catalogId = "catalog",
        catalogName = "Catalog",
        type = ContentType.MOVIE,
        items = items.toList()
    )

    private fun state(
        rows: List<NotflixCatalogRow> = emptyList(),
        continueWatching: List<ContinueWatchingItem> = emptyList(),
        watchedMovies: Set<String> = emptySet(),
        watchedSeries: Set<String> = emptySet()
    ) = HomeUiState(
        notflixCatalog = NotflixCatalogSnapshot(configured = true, rows = rows),
        continueWatchingItems = continueWatching,
        watchedMovieIds = watchedMovies,
        fullyWatchedSeriesIds = watchedSeries
    )

    private fun rows(uiState: HomeUiState) = notflixRows(
        state = uiState,
        keepPendingCw = false,
        cwTitle = "Continue watching",
        episodePattern = "S%1\$s:E%2\$s",
        seeAll = "See all"
    )

    @Test
    fun `catalog row without folder target gets see all`() {
        val row = NotflixCatalogRow(
            key = "catalog-row",
            loadKey = "catalog-row",
            title = "Movies",
            phase = NotflixRowPhase.READY,
            catalog = catalog(movie("tt1"))
        )

        val tiles = rows(state(rows = listOf(row))).single().tiles

        assertEquals("see-all", tiles.last().key)
        assertEquals("See all", tiles.last().title)
        assertEquals(Triple("catalog", "addon", "movie"), tiles.last().seeAllCatalog)
    }

    @Test
    fun `collection row keeps its own see all and does not duplicate it`() {
        val collection = Collection(
            id = "collection",
            title = "Collection",
            folders = listOf(CollectionFolder(id = "folder", title = "Folder"))
        )
        val row = NotflixCatalogRow(
            key = "collection-row",
            loadKey = "collection-row",
            title = "Collection",
            phase = NotflixRowPhase.READY,
            collection = collection,
            folderTarget = "collection" to "folder"
        )

        val tiles = rows(state(rows = listOf(row))).single().tiles
        val seeAll = tiles.filter { it.key == "see-all" }

        assertEquals(1, seeAll.size)
        assertEquals("collection" to "folder", seeAll.single().folder)
        assertNull(seeAll.single().seeAllCatalog)
    }

    @Test
    fun `continue watching gets no see all`() {
        val progress = WatchProgress(
            contentId = "tt1",
            contentType = "movie",
            name = "Movie",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = "tt1",
            season = null,
            episode = null,
            episodeTitle = null,
            position = 10,
            duration = 100,
            lastWatched = 1
        )

        val cwRow = rows(state(continueWatching = listOf(ContinueWatchingItem.InProgress(progress)))).single()

        assertTrue(cwRow.tiles.isNotEmpty())
        assertTrue(cwRow.tiles.none { it.key == "see-all" || it.seeAllCatalog != null })
    }

    @Test
    fun `watched movie is marked for dimming`() {
        val row = NotflixCatalogRow("row", "row", "Movies", NotflixRowPhase.READY, catalog = catalog(movie("tt1")))

        val tile = rows(state(rows = listOf(row), watchedMovies = setOf("tt1"))).single().tiles.first()

        assertTrue(tile.watched)
    }

    @Test
    fun `half watched series is not dimmed`() {
        val row = NotflixCatalogRow("row", "row", "Series", NotflixRowPhase.READY, catalog = catalog(series("tt2")))

        val tile = rows(state(rows = listOf(row))).single().tiles.first()

        assertFalse(tile.watched)
    }

    @Test
    fun `fully watched series is dimmed`() {
        val row = NotflixCatalogRow("row", "row", "Series", NotflixRowPhase.READY, catalog = catalog(series("tt2")))

        val tile = rows(state(rows = listOf(row), watchedSeries = setOf("tt2"))).single().tiles.first()

        assertTrue(tile.watched)
    }

    @Test
    fun `continue watching is never dimmed`() {
        val progress = WatchProgress(
            contentId = "tt1",
            contentType = "movie",
            name = "Movie",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = "tt1",
            season = null,
            episode = null,
            episodeTitle = null,
            position = 10,
            duration = 100,
            lastWatched = 1
        )

        val tile = rows(
            state(
                continueWatching = listOf(ContinueWatchingItem.InProgress(progress)),
                watchedMovies = setOf("tt1")
            )
        ).single().tiles.first()

        assertFalse(tile.watched)
    }
}
