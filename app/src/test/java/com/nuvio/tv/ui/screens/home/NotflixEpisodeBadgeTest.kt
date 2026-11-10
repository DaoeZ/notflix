package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotflixEpisodeBadgeTest {
    private fun series(id: String) = MetaPreview(
        id = id,
        type = ContentType.SERIES,
        rawType = "series",
        name = "Series $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    private fun catalog() = CatalogRow(
        addonId = "addon",
        addonName = "Addon",
        addonBaseUrl = "https://addon.invalid",
        catalogId = "catalog",
        catalogName = "Catalog",
        type = ContentType.SERIES,
        items = listOf(series("tt1"))
    )

    private fun nextUp(isReleaseAlert: Boolean, isNewSeasonRelease: Boolean = false) = ContinueWatchingItem.NextUp(
        NextUpInfo(
            contentId = "tt1",
            contentType = "series",
            name = "Series tt1",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = "tt1:2:1",
            season = 2,
            episode = 1,
            episodeTitle = "Episode",
            thumbnail = null,
            hasAired = true,
            lastWatched = 1L,
            sortTimestamp = 1L,
            isReleaseAlert = isReleaseAlert,
            isNewSeasonRelease = isNewSeasonRelease
        )
    )

    private fun rows(alert: Boolean, newSeason: Boolean = false): List<NotflixDisplayRow> {
        val state = HomeUiState(
            notflixCatalog = NotflixCatalogSnapshot(
                configured = true,
                rows = listOf(
                    NotflixCatalogRow(
                        key = "catalog-row",
                        loadKey = "catalog-row",
                        title = "Series",
                        phase = NotflixRowPhase.READY,
                        catalog = catalog()
                    )
                )
            ),
            continueWatchingItems = listOf(nextUp(alert, newSeason))
        )
        return notflixRows(state, keepPendingCw = false, cwTitle = "Continue watching",
            episodePattern = "S%1\$s:E%2\$s", seeAll = "See all")
    }

    @Test
    fun `new episode alert labels the series card`() {
        val catalogRow = rows(alert = true).single { it.key == "catalog-row" }
        val tile = catalogRow.tiles.single { it.meta?.id == "tt1" }

        assertEquals(NotflixNewEpisodeBadge.NEW_EPISODE, tile.newEpisodeBadge)
    }

    @Test
    fun `new season uses its own label`() {
        val catalogRow = rows(alert = true, newSeason = true).single { it.key == "catalog-row" }
        val tile = catalogRow.tiles.single { it.meta?.id == "tt1" }

        assertEquals(NotflixNewEpisodeBadge.NEW_SEASON, tile.newEpisodeBadge)
    }

    @Test
    fun `without alert the card has no badge`() {
        val catalogRow = rows(alert = false).single { it.key == "catalog-row" }
        val tile = catalogRow.tiles.single { it.meta?.id == "tt1" }

        assertNull(tile.newEpisodeBadge)
    }

    @Test
    fun `the alert is not duplicated on continue watching`() {
        val cwRow = rows(alert = true).single { it.key == "cw" }

        assertEquals(1, cwRow.tiles.size)
        assertNull(cwRow.tiles.single().newEpisodeBadge)
    }
}
