package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.home.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native renderer with declared synthetic metadata; no stores or live catalogs changed. */
@OptIn(ExperimentalTestApi::class)
class NotflixVisualTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NotflixNavigationState()
    private val memory = NotflixHomeMemory()
    private val details = mutableListOf<String>()
    private val navigated = mutableListOf<String>()
    private var profiles = 0
    private fun item(id: String, rating: Float? = null) = MetaPreview(id, ContentType.MOVIE, name = "Title $id",
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = "Known fixture description for $id", releaseInfo = "2026", imdbRating = rating, genres = listOf("Drama"))
    private fun row(key: String): NotflixCatalogRow {
        val catalog = CatalogRow("fixture", "Fixture", "https://example.invalid", key, key, ContentType.MOVIE,
            items = listOf(item("${key}a", 7.1f), item("${key}b", 11f), item("${key}c")))
        return NotflixCatalogRow(key, key, "Row $key", NotflixRowPhase.READY, catalog)
    }
    private fun tag(row: NotflixCatalogRow, index: Int) = "notflix-card-${row.key}|${row.catalog!!.stableItemKeys()[index]}"
    private fun key(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    private fun show(rows: List<NotflixCatalogRow>, hero: Boolean, heroLogo: String? = null) {
        compose.setContent {
            val focus = remember { FocusRequester() }
            val held = remember { mutableStateOf(false) }
            val shell = rememberNotflixNavigationShell(navigation, "home", "home", setOf("home", "search", "library", "settings"), "home",
                listOf("home", "search", "library", "settings").map { NotflixDestination(it, it) }, focus, "Fixture",
                { profiles++ }, { navigated += it }, {}, held)
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true, navigation, true)) {
                PresentationShellHost(shell, focus) {
                    val heroes = if (hero) listOf(item("hero").copy(logo = heroLogo)) else emptyList()
                    NotflixHomeContent(HomeUiState(layoutPreferencesReady = true, isLoading = false,
                        notflixCatalog = NotflixCatalogSnapshot(1, 1, true, rows, heroes), notflixCwPhase = NotflixRowPhase.READY),
                        memory, navigation = navigation, onDetail = { id, _, _ -> details += id }, onContinue = {},
                        onFolder = { _, _ -> }, onVisibleRow = {}, onLongPress = { _, _ -> }, onRetry = {})
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { memory.focus != null }
    }

    @Test fun entryHeroIsFullBleedAndFirstRowStartsNearReferenceLowerQuarterWithHonestCta() {
        val row = row("one"); show(listOf(row), hero = true)
        compose.onNodeWithTag("notflix-editorial-details").assertIsFocused()
        val viewport = compose.onNodeWithTag("notflix-home").getUnclippedBoundsInRoot()
        val hero = compose.onNodeWithTag("notflix-editorial").getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithText("Row one").getUnclippedBoundsInRoot()
        assertEquals(viewport.left.value, hero.left.value, .5f)
        assertEquals(viewport.right.value, hero.right.value, .5f)
        val viewportHeight = (viewport.bottom - viewport.top).value
        assertTrue((hero.bottom - hero.top).value / viewportHeight in .68f.. .76f)
        assertTrue(heading.top.value / viewportHeight in .70f.. .79f)
        val copy = compose.onNodeWithTag("notflix-editorial-copy", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(copy.right < viewport.right / 2f)
        compose.onNodeWithText("Title hero").assertExists()
        compose.onNodeWithText("Play", substring = false).assertDoesNotExist()
        key("notflix-editorial-details", Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("hero"), details) }
    }

    @Test fun expansionStacksArtworkAbovePanelAndShowsOnlySuppliedRatingAndRealDetailAction() {
        val row = row("one"); show(listOf(row), hero = false)
        compose.waitUntil(10_000) { memory.focus?.expanded == true }
        val identity = "${row.key}|${row.catalog!!.stableItemKeys()[0]}"
        compose.onNodeWithTag(tag(row, 0)).assertIsFocused()
        val card = compose.onNodeWithTag(tag(row, 0)).getUnclippedBoundsInRoot()
        val art = compose.onNodeWithTag("notflix-card-art-$identity", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("notflix-card-panel-$identity", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue((art.bottom - art.top) > (panel.bottom - panel.top) && panel.top >= art.bottom && panel.bottom <= card.bottom)
        val viewport = compose.onNodeWithTag("notflix-home").getUnclippedBoundsInRoot()
        // M29/D4 (promoter on the Xiaomi): larger cards than the earlier 20–28 % share; the expanded card is now ~36 %.
        assertTrue((card.right - card.left).value / (viewport.right - viewport.left).value in .32f.. .40f)
        compose.onNodeWithText("7.1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("11.0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Title onea", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Drama", useUnmergedTree = true).assertExists()
        key(tag(row, 0), Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("onea"), details) }
    }

    @Test fun failedLogoFallsBackToActualNameWithoutLosingTheFocusedDetailAction() {
        val row = row("one")
        show(listOf(row), hero = true, heroLogo = "file:///android_asset/notflix-m10-missing-logo.png")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Title hero").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notflix-editorial-details").assertIsFocused()
        key("notflix-editorial-details", Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("hero"), details) }
        key("notflix-editorial-details", Key.DirectionDown)
        compose.waitUntil(10_000) { memory.focus?.expanded == true }
        compose.runOnIdle { assertFalse(navigation.homeAtTop) }
        key(tag(row, 0), Key.DirectionUp)
        compose.onNodeWithTag("notflix-editorial-details").assertIsFocused()
        compose.runOnIdle { assertTrue(navigation.homeAtTop) }
        val identity = "${row.key}|${row.catalog!!.stableItemKeys()[0]}"
        compose.onNodeWithTag("notflix-card-panel-$identity", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun browsingKeepsHeaderAndProfileFocusFollowsItsActualRightHandPosition() {
        val rows = listOf(row("one"), row("two"), row("three")); show(rows, hero = true)
        key("notflix-editorial-details", Key.DirectionDown)
        compose.onNodeWithTag(tag(rows[0], 0)).assertIsFocused()
        key(tag(rows[0], 0), Key.DirectionDown)
        compose.waitUntil(10_000) { memory.focus?.rowKey == "two" }
        compose.onNodeWithTag("notflix-navigation").assertIsDisplayed()
        compose.runOnIdle { navigation.focusTop() }
        compose.onNodeWithTag("notflix-destination-home").assertIsFocused().assertIsSelected()
        for (route in listOf("home", "search", "library", "settings")) key("notflix-destination-$route", Key.DirectionRight)
        compose.onNodeWithTag("notflix-destination-profile").assertIsFocused()
        compose.runOnIdle { assertTrue(navigated.isEmpty()); assertEquals(0, profiles) }
        key("notflix-destination-profile", Key.DirectionCenter)
        compose.runOnIdle { assertEquals(1, profiles); assertTrue(navigated.isEmpty()) }
    }
}
