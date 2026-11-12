package com.nuvio.tv.ui.screens.collection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
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
 * M67: la tarjeta abierta de una colecciÃ³n ofrece las mismas acciones que Inicio, cambia Mi lista y
 * Visto con los escritores que le pasa la pantalla y, al ocultar un tÃ­tulo, el foco pasa al vecino.
 * Sin trÃ¡iler ni red: el sonido no se dibuja porque no hay sesiÃ³n de vista previa.
 */
@OptIn(ExperimentalTestApi::class)
class NotflixCategoryCardActionsTest {
    @get:Rule val compose = createComposeRule()

    private val tabKey = "tab-0-Accion"
    private val identity get() = notflixCategoryCardIdentity(tabKey, "movie:1")
    private val calls = mutableListOf<String>()
    private val items = mutableStateListOf<MetaPreview>()
    private val inLibrary = mutableStateOf(emptySet<String>())
    private val watchedPending = mutableStateOf(false)
    private val watchedIds = mutableStateOf(emptySet<String>())

    private fun meta(id: String): MetaPreview = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        rawType = "movie",
        name = "TÃ­tulo $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Sinopsis $id",
        releaseInfo = "2026",
        imdbRating = 7.5f,
        genres = listOf("AcciÃ³n")
    )

    private fun show(count: Int = 6, withTabs: Boolean = false, slowWatched: Boolean = false) {
        compose.mainClock.autoAdvance = false
        items.clear()
        items += (1..count).map { meta("$it") }
        compose.setContent {
            val specs = items.map { it.toNotflixPosterSpec() }
            val host = NotflixCardActionsHost(
                status = { item ->
                    NotflixCardStatus(
                        inLibrary = item.id in inLibrary.value,
                        watched = item.id in watchedIds.value,
                        watchedPending = watchedPending.value
                    )
                },
                manageLists = false,
                onExpanded = {},
                onLibrary = { item, _ ->
                    calls += "library:${item.id}"
                    inLibrary.value = if (item.id in inLibrary.value) inLibrary.value - item.id
                    else inLibrary.value + item.id
                },
                onWatched = { item ->
                    calls += "watched:${item.id}"
                    if (slowWatched) watchedPending.value = true
                    else watchedIds.value = if (item.id in watchedIds.value) watchedIds.value - item.id
                    else watchedIds.value + item.id
                },
                onHide = { item ->
                    calls += "hide:${item.id}"
                    items.removeAll { it.id == item.id }
                }
            )
            CompositionLocalProvider(
                LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true),
                LocalLayoutDirection provides LayoutDirection.Ltr
            ) {
                NotflixPresentationTheme(true) {
                    NotflixCategoryPage(
                        title = "AcciÃ³n",
                        collectionLabel = null,
                        tabs = listOfNotNull(NotflixCategoryTab(tabKey, "AcciÃ³n", specs),
                            if (withTabs) NotflixCategoryTab("second", "Second", specs) else null),
                        selectedTabIndex = 0,
                        saveScope = "m67-test",
                        onSelectTab = {},
                        headerFor = { null },
                        onPosterClick = { _, _ -> },
                        onLoadMore = {},
                        onRetry = {},
                        itemsByKey = specs.zip(items).associate { (spec, item) -> spec.key to item },
                        cardActions = host,
                        canPlay = true,
                        onCardPlay = { calls += "play:${it.id}" }
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun openCard() {
        compose.mainClock.advanceTimeBy(1600)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("notflix-category-expanded").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun press(keys: List<Key>) {
        val poster = compose.onNodeWithTag("notflix-poster-movie:1", useUnmergedTree = true)
        keys.forEach { key ->
            poster.performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
    }

    private fun label() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theExpandedCollectionCardOffersTheSameActionsAsHome() {
        show()
        openCard()
        // Sin trÃ¡iler no hay sonido: las otras cinco acciones son las de Inicio (M67).
        for (action in listOf("play", "info", "library", "watched", "hide")) {
            compose.onNodeWithTag("notflix-card-action-$identity-$action", useUnmergedTree = true)
                .assertExists()
        }
    }

    @Test
    fun watchedChangesFromTheCollectionCard() {
        show()
        openCard()
        // Abajo entra en los botones; el cursor nace en Reproducir.
        press(listOf(Key.DirectionDown, Key.DirectionRight, Key.DirectionRight, Key.DirectionRight))
        compose.onNodeWithTag("notflix-card-action-label-$identity", useUnmergedTree = true)
            .assertTextEquals(label().getString(R.string.hero_mark_watched))
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf("watched:1"), calls)
        compose.onNodeWithTag("notflix-card-action-label-$identity", useUnmergedTree = true)
            .assertTextEquals(label().getString(R.string.hero_mark_unwatched))
    }

    @Test
    fun libraryChangesFromTheCollectionCard() {
        show()
        openCard()
        press(listOf(Key.DirectionDown, Key.DirectionRight, Key.DirectionRight))
        compose.onNodeWithTag("notflix-card-action-label-$identity", useUnmergedTree = true)
            .assertTextEquals(label().getString(R.string.hero_add_to_library))
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf("library:1"), calls)
        compose.onNodeWithTag("notflix-card-action-label-$identity", useUnmergedTree = true)
            .assertTextEquals(label().getString(R.string.hero_remove_from_library))
    }

    @Test
    fun hidingATitleTakesItOutAndMovesTheFocusToItsNeighbour() {
        show()
        openCard()
        press(listOf(Key.DirectionDown, Key.DirectionRight, Key.DirectionRight, Key.DirectionRight, Key.DirectionRight))
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf("hide:1"), calls)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onAllNodesWithTag("notflix-poster-movie:1").fetchSemanticsNodes().let {
            assertTrue("el tÃ­tulo oculto no puede seguir en la rejilla", it.isEmpty())
        }
        // El vecino que ocupa su sitio recibe el foco, sin quedarse sin destino.
        compose.onNodeWithTag("notflix-poster-movie:2", useUnmergedTree = true).assertIsFocused()
    }
    @Test
    fun hidingTheLastTitleMovesFocusToThePreviousNeighbour() {
        show(count = 7)
        press(listOf(Key.DirectionDown)) // Closed card: go straight to the last grid row.
        openCard()
        val last = compose.onNodeWithTag("notflix-poster-movie:7", useUnmergedTree = true)
        last.assertIsFocused()
        listOf(Key.DirectionDown, Key.DirectionRight, Key.DirectionRight,
            Key.DirectionRight, Key.DirectionRight, Key.DirectionCenter).forEach { key ->
            last.performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertEquals(listOf("hide:7"), calls)
        compose.onNodeWithTag("notflix-poster-movie:6", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun removingAPosterWhileTheTabHasFocusDoesNotStealItBack() {
        show(withTabs = true)
        openCard()
        press(listOf(Key.DirectionUp))
        val tab = compose.onNodeWithTag("notflix-category-tab-$tabKey", useUnmergedTree = true)
        tab.assertIsFocused()
        compose.runOnIdle { items.removeAt(0) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        tab.assertIsFocused()
    }

    @Test
    fun aPendingWatchedButtonCannotWriteAgainAndWorksWhenSavingFinishes() {
        show(slowWatched = true)
        openCard()
        press(listOf(Key.DirectionDown, Key.DirectionRight, Key.DirectionRight,
            Key.DirectionRight, Key.DirectionCenter, Key.DirectionCenter))
        assertEquals(listOf("watched:1"), calls)
        compose.runOnIdle { watchedIds.value = setOf("1"); watchedPending.value = false }
        compose.waitForIdle()
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf("watched:1", "watched:1"), calls)
    }

}
