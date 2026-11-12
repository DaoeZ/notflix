package com.nuvio.tv.ui.screens.collection

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryCardIdentity
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryClamp
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryExpandToStart
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryExpandedOriginX
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryFocusTarget
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryGridSaveKey
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryPosterKeys
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryShouldLoadMore
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryShouldPreview
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

    // ─── M63: tarjeta abierta de la rejilla ───

    @Test
    fun onlyTheLastColumnOpensTheCardTowardsTheStartEdge() {
        // 6 columnas: la sexta es la última.
        assertFalse(notflixCategoryExpandToStart(0, 6))
        assertFalse(notflixCategoryExpandToStart(4, 6))
        assertTrue(notflixCategoryExpandToStart(5, 6))
        assertTrue(notflixCategoryExpandToStart(11, 6))
        // M64: la rejilla pequeña usa 7 columnas y la regla la sigue.
        assertFalse(notflixCategoryExpandToStart(5, 7))
        assertTrue(notflixCategoryExpandToStart(6, 7))
        // Sin columnas no hay lado: nunca se abre al revés.
        assertFalse(notflixCategoryExpandToStart(0, 0))
        assertFalse(notflixCategoryExpandToStart(-1, 6))
    }

    @Test
    fun theOpenCardStaysAnchoredToItsPoster() {
        // Hacia el final: el borde de inicio es el del póster; las vecinas de la derecha quedan tapadas.
        assertEquals(100f, notflixCategoryExpandedOriginX(100f, 210f, 378f, openToStart = false), 0.01f)
        // Hacia el inicio: el borde final es el del póster y la tarjeta crece hacia la izquierda.
        assertEquals(-68f, notflixCategoryExpandedOriginX(100f, 210f, 378f, openToStart = true), 0.01f)
        // Durante la apertura el ancho crece y el ancla sigue pegada al póster.
        assertEquals(100f, notflixCategoryExpandedOriginX(100f, 210f, 210f, openToStart = true), 0.01f)
    }

    @Test
    fun theTrailerIsOnlyAskedWithTheCardOpenAndTheGridStill() {
        assertTrue(notflixCategoryShouldPreview(expanded = true, scrolling = false))
        assertFalse(notflixCategoryShouldPreview(expanded = true, scrolling = true))
        assertFalse(notflixCategoryShouldPreview(expanded = false, scrolling = false))
    }

    @Test
    fun theCardIdentityIsStableAndDifferentPerTab() {
        assertEquals("tab:tab-0-Acción|movie:1", notflixCategoryCardIdentity("tab-0-Acción", "movie:1"))
        assertNotEquals(
            notflixCategoryCardIdentity("tab-0-Acción", "movie:1"),
            notflixCategoryCardIdentity("tab-1-Comedia", "movie:1")
        )
    }
    @Test
    fun repeatedTitleUsesTheSelectedTabsMetadataAndAddon() {
        val first = NotflixFolderTabData(
            com.nuvio.tv.ui.appearance.notflix.NotflixCategoryTab("first", "First"),
            mapOf("movie:1" to meta("1").copy(name = "First name")), "https://first.invalid")
        val second = NotflixFolderTabData(
            com.nuvio.tv.ui.appearance.notflix.NotflixCategoryTab("second", "Second"),
            mapOf("movie:1" to meta("1").copy(name = "Second name")), "https://second.invalid")
        val selected = requireNotNull(notflixFolderSelectedTab(listOf(first, second), 1))
        assertEquals("Second name", selected.itemsByKey.getValue("movie:1").name)
        assertEquals("https://second.invalid", selected.addonBaseUrl)
        assertEquals(first, notflixFolderSelectedTab(listOf(first, second), -1))
        assertEquals(second, notflixFolderSelectedTab(listOf(first, second), 99))
        assertNull(notflixFolderSelectedTab(emptyList(), 0))
    }

    @org.junit.Test
    fun expandedCardStaysInsideTheGrid() {
        // Fits: unchanged. Too far right/down: shifted back. Wider than the grid: pinned to the start.
        assertEquals(100f, notflixCategoryClamp(100f, 300f, 68f, 1852f))
        assertEquals(1552f, notflixCategoryClamp(1700f, 300f, 68f, 1852f))
        assertEquals(68f, notflixCategoryClamp(10f, 300f, 68f, 1852f))
        assertEquals(68f, notflixCategoryClamp(500f, 2000f, 68f, 1852f))
    }
}
