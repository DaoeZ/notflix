package com.nuvio.tv.ui.screens.collection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryPage
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryTab
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryCardIdentity
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import com.nuvio.tv.ui.screens.home.NotflixCardActionsHost
import com.nuvio.tv.ui.screens.home.NotflixCardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * M63: la tarjeta abierta de la rejilla de colección se dibuja encima de las vecinas y no las mueve.
 * Movimiento reducido y reloj controlado para medir la apertura; sin tráiler ni red.
 */
@OptIn(ExperimentalTestApi::class)
class NotflixCategoryExpandedCardTest {
    @get:Rule val compose = createComposeRule()

    private val tabKey = "tab-0-Acción"

    private fun meta(id: String): MetaPreview = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        rawType = "movie",
        name = "Título $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Sinopsis $id",
        releaseInfo = "2026",
        imdbRating = 7.5f,
        genres = listOf("Acción")
    )

    private fun show(plays: MutableList<String>, rtl: Boolean = false, tabs: Boolean = false) {
        compose.mainClock.autoAdvance = false
        val metas = (1..8).map { meta("$it") }
        val specs = metas.map { it.toNotflixPosterSpec() }
        val host = NotflixCardActionsHost(
            status = { NotflixCardStatus(inLibrary = null, watched = null) },
            manageLists = false,
            onExpanded = {},
            onLibrary = { _, _ -> },
            onWatched = {},
            onHide = {}
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                NotflixPresentationTheme(true) {
                    NotflixCategoryPage(
                        title = "Acción",
                        collectionLabel = null,
                        tabs = listOf(NotflixCategoryTab(tabKey, "Acción", specs)) +
                            if (tabs) listOf(NotflixCategoryTab("other", "Other", specs)) else emptyList(),
                        selectedTabIndex = 0,
                        saveScope = "m63-test",
                        onSelectTab = {},
                        headerFor = { null },
                        onPosterClick = { _, _ -> },
                        onLoadMore = {},
                        onRetry = {},
                        itemsByKey = specs.zip(metas).associate { (spec, item) -> spec.key to item },
                        cardActions = host,
                        canPlay = true,
                        onCardPlay = { plays += it.id }
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun theOpenCardIsDrawnAboveTheGridWithoutMovingItsNeighbours() {
        val plays = mutableListOf<String>()
        show(plays)
        // La vecina y las de su fila antes de abrir nada.
        val neighbour = bounds("notflix-poster-movie:2")
        val firstRow = (2..6).map { bounds("notflix-poster-movie:$it") }

        compose.mainClock.advanceTimeBy(1600)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("notflix-category-expanded").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()

        compose.onNodeWithTag("notflix-category-expanded", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(neighbour, bounds("notflix-poster-movie:2"))
        assertEquals(firstRow, (2..6).map { bounds("notflix-poster-movie:$it") })
        // La tarjeta abierta empieza en el póster enfocado y se extiende a su derecha.
        val poster = bounds("notflix-poster-movie:1")
        val opened = bounds("notflix-category-expanded")
        assertEquals(poster.left, opened.left, 1f)
        assertTrue("$opened debe crecer a la derecha de $poster", opened.right > poster.right)
        assertTrue(plays.isEmpty())
    }

    @Test
    fun downEntersTheButtonsAndOKPlaysTheTitle() {
        val plays = mutableListOf<String>()
        show(plays)
        compose.mainClock.advanceTimeBy(1600)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("notflix-category-expanded").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()

        val poster = compose.onNodeWithTag("notflix-poster-movie:1", useUnmergedTree = true)
        poster.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        val identity = notflixCategoryCardIdentity(tabKey, "movie:1")
        compose.onNodeWithTag("notflix-card-action-$identity-play", useUnmergedTree = true).assertExists()
        // OK lo recibe el póster enfocado: la tarjeta abierta no toma el foco de Compose.
        poster.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        // OK sobre Reproducir entrega el título a la pantalla, que decide cómo reproducir.
        assertEquals(listOf("1"), plays)
    }
    private fun assertLastColumn(rtl: Boolean) {
        show(mutableListOf(), rtl)
        val before = bounds("notflix-poster-movie:6")
        val neighbour = bounds("notflix-poster-movie:5")
        compose.onNodeWithTag("notflix-poster-movie:6", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        val identity = notflixCategoryCardIdentity(tabKey, "movie:6")
        val art = bounds("notflix-card-art-$identity")
        val opened = bounds("notflix-category-expanded")
        assertEquals(before.left, art.left, 1f)
        assertEquals(before.right, art.right, 1f)
        assertEquals(before.top, opened.top, 1f)
        assertEquals(before.height, opened.height, 1f)
        assertEquals(neighbour, bounds("notflix-poster-movie:5"))
        if (rtl) assertTrue(opened.right > before.right) else assertTrue(opened.left < before.left)
    }

    @Test fun lastColumnKeepsThePosterOnTheRightWhenOpeningLeft() = assertLastColumn(false)
    @Test fun rtlLastColumnKeepsThePosterOnTheLeftWhenOpeningRight() = assertLastColumn(true)

    @Test fun leavingTheGridForTheTabsClosesTheOverlay() {
        show(mutableListOf(), tabs = true)
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-category-expanded", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("notflix-poster-movie:1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-category-expanded", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun fastFocusChangesDoNotOpenIntermediateCards() {
        show(mutableListOf())
        compose.onNodeWithTag("notflix-poster-movie:6", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("notflix-poster-movie:5", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-category-expanded", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        val identity = notflixCategoryCardIdentity(tabKey, "movie:5")
        compose.onNodeWithTag("notflix-card-art-$identity", useUnmergedTree = true).assertExists()
    }
}
