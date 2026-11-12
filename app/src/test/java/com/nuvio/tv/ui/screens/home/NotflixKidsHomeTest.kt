package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.NotflixKidsHomePreset
import com.nuvio.tv.ui.appearance.NotflixKidsHomeTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M66: kids rows replace the account rows, the strict age filter stays, and the notice is honest. */
class NotflixKidsHomeTest {
    private val texts = NotflixKidsHomeTexts("Anim movies", "Family movies", "Anim series", "Family series")
    private val kids = NotflixKidsHomePreset.create(texts)
    private val addon = Addon("com.linvo.cinemeta", "Cinemeta", version = "1", description = null, logo = null,
        baseUrl = "https://cinemeta.invalid", catalogs = emptyList(),
        types = listOf(ContentType.MOVIE, ContentType.SERIES), resources = emptyList())

    private fun item(id: String, type: ContentType = ContentType.MOVIE, rating: String? = null) = MetaPreview(
        id = id, type = type, rawType = if (type == ContentType.SERIES) "series" else "movie",
        name = "Synthetic $id", poster = null, posterShape = PosterShape.POSTER, background = null,
        logo = null, description = null, releaseInfo = null, imdbRating = null, genres = emptyList(),
        ageRating = rating
    )

    private fun catalogRow(items: List<MetaPreview>, type: ContentType = ContentType.MOVIE, key: String = "account") =
        CatalogRow(addon.id, addon.name, addon.baseUrl, "top", "Top", type, items = items)

    private fun folderRow(folderId: String, title: String, items: List<MetaPreview>, type: ContentType = ContentType.MOVIE) =
        NotflixCatalogRow("collection_notflix-kids:$folderId", "collection_notflix-kids:$folderId", title,
            NotflixRowPhase.READY, catalogRow(items, type, folderId), folderTarget = "notflix-kids" to folderId)

    private fun accountRow() = NotflixCatalogRow(
        "account", "account", "Account row", NotflixRowPhase.READY,
        catalogRow(listOf(item("tt-account")), key = "account")
    )

    private fun project(rows: List<NotflixCatalogRow>, folders: List<NotflixCatalogRow>, kidsHome: Boolean,
                        collections: List<Collection> = emptyList(), disabled: Set<String> = emptySet()) =
        projectNotflixCatalogs(
            captured = NotflixCatalogSnapshot(2, 7, true, rows), ordered = rows.map { it.loadKey },
            fullRows = rows.mapNotNull { it.catalog }, collections = collections, disabled = disabled,
            customTitles = emptyMap(), descriptors = emptyList(), showTypeSuffix = false,
            movieLabel = "Movie", seriesLabel = "Series", projectedFolders = folders, kidsHome = kidsHome
        )

    @Test fun kidsProjectionShowsOnlyTheKidsRowsInOrder() {
        val folders = listOf(
            folderRow("kids-animacion", "Anim movies", listOf(item("tt1"))),
            folderRow("kids-familia", "Family movies", listOf(item("tt2"))),
            folderRow("kids-series-animacion", "Anim series", listOf(item("tt3", ContentType.SERIES)), ContentType.SERIES),
            folderRow("kids-series-familia", "Family series", listOf(item("tt4", ContentType.SERIES)), ContentType.SERIES)
        )
        val result = project(listOf(accountRow()), folders, kidsHome = true)
        assertEquals(texts.let { listOf(it.animatedMovies, it.familyMovies, it.animatedSeries, it.familySeries) },
            result.rows.map { it.title })
        assertEquals(4, result.rows.size)
        assertTrue(result.rows.all { it.folderTarget?.first == "notflix-kids" })
        assertTrue(result.rows.none { it.loadKey == "account" })
    }

    @Test fun kidsProjectionIgnoresTheAccountCollectionAndNormalProfilesKeepTheirRows() {
        val collection = Collection("notflix-inicio", "Inicio Notflix", pinToTop = true,
            folders = listOf(CollectionFolder("animacion", "Animated movies")))
        val folders = listOf(folderRow("kids-animacion", "Anim movies", listOf(item("tt1"))))
        val kidsResult = project(listOf(accountRow()), folders, kidsHome = true, collections = listOf(collection))
        assertEquals(listOf("Anim movies"), kidsResult.rows.map { it.title })

        val normal = project(listOf(accountRow()), folders, kidsHome = false, collections = listOf(collection))
        assertEquals(listOf("Inicio Notflix", "Account row"), normal.rows.map { it.title })
    }

    @Test fun kidsRowsGoThroughTheAgeGateWithTheSameStrictRule() {
        val folders = listOf(folderRow("kids-animacion", "Anim movies",
            listOf(item("tt1", rating = "16"), item("tt2"), item("tt3", rating = "TP"))))
        val kidsState = HomeUiState(
            notflixCatalog = NotflixCatalogSnapshot(configured = true, rows = project(listOf(accountRow()), folders, true).rows),
            ageLimit = AgeLevel.TP, ageTmdbKeyAvailable = true
        )
        val rows = notflixRows(kidsState, keepPendingCw = false, cwTitle = "Continue watching",
            episodePattern = "S%1\$s:E%2\$s", seeAll = "See all")
        val tiles = rows.single().tiles.filter { it.meta != null }
        assertEquals(listOf("tt3"), tiles.map { it.meta!!.id })
    }

    @Test fun kidsNoticeOnlyAppearsForTheKidsPresetWithoutTheTmdbKey() {
        assertTrue(notflixKidsNeedsTmdbKey(AgeLevel.TP, tmdbKeyAvailable = false))
        assertFalse(notflixKidsNeedsTmdbKey(AgeLevel.TP, tmdbKeyAvailable = true))
        assertFalse(notflixKidsNeedsTmdbKey(AgeLevel.A7, tmdbKeyAvailable = false))
        assertFalse(notflixKidsNeedsTmdbKey(null, tmdbKeyAvailable = false))
    }

    @Test fun kidsSpecsUseTheKidsRowGroupAndItsLocalizedTitles() {
        val specs = notflixCollectionSpecsFor(kids, listOf(addon), emptySet())
        assertEquals(listOf("collection_notflix-kids:kids-animacion", "collection_notflix-kids:kids-familia",
            "collection_notflix-kids:kids-series-animacion", "collection_notflix-kids:kids-series-familia"),
            specs.map { it.key })
        assertEquals(listOf("Anim movies", "Family movies", "Anim series", "Family series"), specs.map { it.title })
        assertEquals("movie" to "top", specs[0].request!!.type to specs[0].request!!.catalogId)
        assertEquals(mapOf("genre" to "Family"), specs[1].request!!.extraArgs)
        assertEquals("series" to "top", specs[3].request!!.type to specs[3].request!!.catalogId)
        assertTrue(specs.all { it.folder.first == "notflix-kids" })
        assertTrue(notflixCollectionSpecsFor(kids, listOf(addon), setOf("collection_notflix-kids")).isEmpty())
    }
    @Test fun missingKeySuppressesRealEmptyFolderActionsInsteadOfMaskingTheNotice() {
        val folders = listOf(folderRow("kids-animacion", "Anim movies", emptyList()))
        val state = HomeUiState(ageLimit = AgeLevel.TP, ageTmdbKeyAvailable = false,
            notflixCatalog = project(emptyList(), folders, true))
        assertTrue(notflixRows(state, false, "CW", "S%1\$s:E%2\$s", "See all").isEmpty())
        // The key transition must allow the same page's rows again.
        assertTrue(notflixRows(state.copy(ageTmdbKeyAvailable = true), false, "CW", "S%1\$s:E%2\$s", "See all").isNotEmpty())
    }

    @Test fun kidsBuiltinRowsCannotBeRemovedByTheAccountsDisabledRowPreference() {
        val folder = folderRow("kids-animacion", "Anim movies", listOf(item("tt1", rating = "TP")))
        val result = project(emptyList(), listOf(folder), true,
            disabled = setOf("collection_notflix-kids", folder.loadKey))
        assertEquals(listOf(folder.key), result.rows.map { it.key })
    }
}
