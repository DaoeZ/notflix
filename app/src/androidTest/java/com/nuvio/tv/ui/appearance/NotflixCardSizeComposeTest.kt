package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.stableItemKeys
import com.nuvio.tv.ui.screens.home.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** M64: the real Home renderer draws the approved poster width for each card size. */
@OptIn(ExperimentalTestApi::class)
class NotflixCardSizeComposeTest {
    @get:Rule val compose = createComposeRule()
    private val cardSize = mutableStateOf(NotflixCardSize.LARGE)
    private val memory = NotflixHomeMemory()
    private val navigation = NotflixNavigationState()

    private fun item(index: Int) = MetaPreview("size$index", ContentType.MOVIE, name = "Size title $index",
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = "Size fixture information $index", releaseInfo = "2026", imdbRating = null, genres = emptyList())
    private val catalog = CatalogRow("fixture", "Fixture", "https://example.invalid", "size", "size", ContentType.MOVIE,
        items = (0..2).map(::item))
    private val row = NotflixCatalogRow("size", "size", "Size fixture", NotflixRowPhase.READY, catalog)
    private fun tag(index: Int) = "notflix-card-size|${catalog.stableItemKeys()[index]}"

    private fun show() {
        compose.setContent {
            val focus = remember { FocusRequester() }
            val held = remember { mutableStateOf(false) }
            val shell = rememberNotflixNavigationShell(navigation, "home", "home", setOf("home", "settings"), "home",
                listOf(NotflixDestination("home", "Home"), NotflixDestination("settings", "Settings")), focus, null, {}, {}, {}, held)
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true,
                navigation = navigation, reduceHomeMotion = true, cardSize = cardSize.value)) {
                PresentationShellHost(shell, focus) {
                    NotflixHomeContent(HomeUiState(layoutPreferencesReady = true, isLoading = false,
                        notflixCatalog = NotflixCatalogSnapshot(1, 1, true, listOf(row), emptyList()),
                        notflixCwPhase = NotflixRowPhase.READY), memory, navigation = navigation,
                        onDetail = { _, _, _ -> }, onContinue = {}, onFolder = { _, _ -> }, onVisibleRow = {},
                        onLongPress = { _, _ -> }, onRetry = {})
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag(1)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun width(index: Int): Float {
        compose.waitForIdle()
        return compose.onNodeWithTag(tag(index)).fetchSemanticsNode().config[NotflixVisualSpec.RenderedCardWidthDp]
    }

    @Test fun posterWidthFollowsTheThreeCardSizes() {
        show()
        assertEquals(210f, width(1), 0.01f)
        compose.runOnIdle { cardSize.value = NotflixCardSize.MEDIUM }
        assertEquals(183f, width(1), 0.01f)
        compose.runOnIdle { cardSize.value = NotflixCardSize.SMALL }
        assertEquals(158f, width(1), 0.01f)
        compose.runOnIdle { cardSize.value = NotflixCardSize.LARGE }
        assertEquals(210f, width(1), 0.01f)
    }
}
