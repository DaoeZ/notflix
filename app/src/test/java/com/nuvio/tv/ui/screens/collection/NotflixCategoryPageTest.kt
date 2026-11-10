package com.nuvio.tv.ui.screens.collection

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryFocusTarget
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryGridSaveKey
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryPosterKeys
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryShouldLoadMore
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryShowTabs
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryTabIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M49: reglas de la página de categoría (carpeta y «Ver todo»). Puras, sin Compose ni recursos. */
class NotflixCategoryPageTest {

    private fun meta(id: String, type: String = "movie"): MetaPreview = MetaPreview(
        id = id,
        type = ContentType.fromString(type),
        rawType = type,
        name = "Título $id",
        poster = "https://example.com/$id.jpg",
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    // ─── Visibilidad de pestañas ───

    @Test
    fun tabsAreOnlyShownWithMoreThanOneCatalog() {
        assertFalse(notflixCategoryShowTabs(0))
        assertFalse(notflixCategoryShowTabs(1))
        assertTrue(notflixCategoryShowTabs(2))
        assertTrue(notflixCategoryShowTabs(7))
    }

    // ─── Pestaña inicial ───

    @Test
    fun selectedTabIsKeptWhileItStillExists() {
        assertEquals(0, notflixCategoryTabIndex(0, 3))
        assertEquals(1, notflixCategoryTabIndex(1, 3))
        assertEquals(2, notflixCategoryTabIndex(2, 3))
    }

    @Test
    fun anIndexOutOfRangeFallsBackInsideTheTabs() {
        assertEquals(2, notflixCategoryTabIndex(9, 3))
        assertEquals(0, notflixCategoryTabIndex(-4, 3))
    }

    @Test
    fun aFolderWithoutCatalogsHasNoTab() {
        assertEquals(0, notflixCategoryTabIndex(3, 0))
        assertEquals(0, notflixCategoryTabIndex(0, 0))
    }

    // ─── Restauración del foco por clave ───

    @Test
    fun focusComesBackToTheSavedPoster() {
        assertEquals(
            "movie:2",
            notflixCategoryFocusTarget("movie:2", listOf("movie:1", "movie:2", "movie:3"))
        )
    }

    @Test
    fun focusFallsBackToTheFirstPosterWhenTheSavedKeyIsGone() {
        assertEquals(
            "movie:1",
            notflixCategoryFocusTarget("movie:99", listOf("movie:1", "movie:2"))
        )
        assertEquals(
            "movie:1",
            notflixCategoryFocusTarget(null, listOf("movie:1", "movie:2"))
        )
    }

    @Test
    fun withoutPostersThereIsNoFocusTarget() {
        assertNull(notflixCategoryFocusTarget("movie:1", emptyList()))
        assertNull(notflixCategoryFocusTarget(null, emptyList()))
    }

    @Test
    fun eachTabGridSavesItsOwnFocus() {
        val first = notflixCategoryGridSaveKey("folder-abc", "tab-0-Todo")
        val second = notflixCategoryGridSaveKey("folder-abc", "tab-1-Acción")
        assertEquals(first, notflixCategoryGridSaveKey("folder-abc", "tab-0-Todo"))
        assertNotEquals(first, second)
        assertNotEquals(
            first,
            notflixCategoryGridSaveKey("folder-other", "tab-0-Todo")
        )
    }

    // ─── Umbral de carga ───

    @Test
    fun nextPageIsAskedAtTheExistingDistanceFromTheEnd() {
        // El umbral es el que ya usa la pantalla oficial (10), no uno nuevo.
        assertTrue(notflixCategoryShouldLoadMore(30, 40, hasMore = true, isLoading = false))
        assertFalse(notflixCategoryShouldLoadMore(29, 40, hasMore = true, isLoading = false))
    }

    @Test
    fun aShortGridAsksForTheNextPageAsSoonAsItFits() {
        assertTrue(notflixCategoryShouldLoadMore(4, 5, hasMore = true, isLoading = false))
        assertTrue(notflixCategoryShouldLoadMore(0, 3, hasMore = true, isLoading = false))
    }

    @Test
    fun loadingOrFinishedRowsNeverAskAgain() {
        assertFalse(notflixCategoryShouldLoadMore(39, 40, hasMore = false, isLoading = false))
        assertFalse(notflixCategoryShouldLoadMore(39, 40, hasMore = true, isLoading = true))
        assertFalse(notflixCategoryShouldLoadMore(39, 0, hasMore = true, isLoading = false))
        assertFalse(notflixCategoryShouldLoadMore(-1, 40, hasMore = true, isLoading = false))
    }

    @Test
    fun theThresholdCanBeStatedExplicitly() {
        assertTrue(
            notflixCategoryShouldLoadMore(16, 20, hasMore = true, isLoading = false, threshold = 4)
        )
        assertFalse(
            notflixCategoryShouldLoadMore(15, 20, hasMore = true, isLoading = false, threshold = 4)
        )
    }

    // ─── Claves estables de los pósters ───

    @Test
    fun uniqueTitlesKeepTheirIdentityAsKey() {
        assertEquals(
            listOf("movie:1", "series:2"),
            notflixCategoryPosterKeys(listOf(meta("1"), meta("2", "series")))
        )
    }

    @Test
    fun aRepeatedTitleGetsAnOccurrenceSuffixSoKeysStayUnique() {
        val keys = notflixCategoryPosterKeys(listOf(meta("1"), meta("1"), meta("1")))
        assertEquals(listOf("movie:1", "movie:1#1", "movie:1#2"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theSameIdInAnotherTypeIsAnotherTitle() {
        assertEquals(
            listOf("movie:7", "series:7"),
            notflixCategoryPosterKeys(listOf(meta("7"), meta("7", "series")))
        )
    }
}
