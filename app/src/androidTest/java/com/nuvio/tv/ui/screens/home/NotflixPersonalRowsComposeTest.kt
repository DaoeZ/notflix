package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production Home rows with declared synthetic personal rows (no library, history or network touched). */
@OptIn(ExperimentalTestApi::class)
class NotflixPersonalRowsComposeTest {
    @get:Rule val compose = createComposeRule()
    private val details = mutableListOf<Pair<String, String>>()
    private fun item(id: String, base: String?) = MetaPreview(id = id, type = ContentType.MOVIE, name = "Personal $id", poster = null,
        posterShape = PosterShape.POSTER, background = null, logo = null, description = "Synthetic", releaseInfo = "2026",
        imdbRating = 6.5f, genres = listOf("Drama"), sourceAddonBaseUrl = base)

    private fun show(s: PreviewFixtureState, myList: NotflixCatalogRow?, because: NotflixCatalogRow?) {
        compose.setContent {
            s.view = LocalView.current
            CompositionLocalProvider(LocalLifecycleOwner provides s.lifecycle,
                LocalNotflixPresentation provides NotflixPresentation(true, s.navigation, true)) {
                NotflixPresentationTheme(true) {
                    val focus = remember { FocusRequester() }; val held = remember { mutableStateOf(false) }
                    val shell = rememberNotflixNavigationShell(s.navigation, "home", "home", setOf("home", "search", "library", "settings"), "home",
                        listOf("home", "search", "library", "settings").map { NotflixDestination(it, it) }, focus, "Fixture", {}, {}, {}, held)
                    PresentationShellHost(shell, focus) {
                        CompositionLocalProvider(LocalContentFocusRequester provides focus) {
                            NotflixHomeContent(s.state, s.memory, navigation = s.navigation,
                                onDetail = { id, _, base -> details += id to base }, onContinue = {}, onFolder = { _, _ -> }, onVisibleRow = {},
                                myListRow = myList, becauseRow = because, onRetry = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun top(key: String) = compose.onNodeWithTag("notflix-row-$key").fetchSemanticsNode().boundsInRoot.top

    @Test fun myListFollowsContinueWatchingAndBecauseFollowsTheFirstCatalogueRow() {
        val s = PreviewFixtureState()
        show(s, notflixPersonalRow(NOTFLIX_MY_LIST_KEY, "My list", listOf(item("mine", "https://mine.invalid"))),
            notflixPersonalRow(NOTFLIX_BECAUSE_KEY, "Because you watched Synthetic", listOf(item("rec", null))))
        compose.onNodeWithText("My list", useUnmergedTree = true).assertExists()
        // Lazy rows: scroll the column to each key, then compare with its neighbour.
        compose.onNodeWithTag("notflix-home").performScrollToKey(NOTFLIX_MY_LIST_KEY)
        val myListTop = top(NOTFLIX_MY_LIST_KEY); val aTop = top("A")
        assertTrue("My list $myListTop before A $aTop", myListTop < aTop)
        compose.onNodeWithTag("notflix-home").performScrollToKey(NOTFLIX_BECAUSE_KEY)
        compose.onNodeWithText("Because you watched Synthetic", useUnmergedTree = true).assertExists()
        val becauseTop = top(NOTFLIX_BECAUSE_KEY); val bTop = top("B")
        assertTrue("Because $becauseTop before B $bTop", becauseTop < bTop)
    }

    @Test fun absentPersonalDataShowsNoRowAndNothingInvented() {
        val s = PreviewFixtureState(); show(s, null, null)
        compose.onNodeWithTag("notflix-row-$NOTFLIX_MY_LIST_KEY").assertDoesNotExist()
        compose.onNodeWithTag("notflix-row-$NOTFLIX_BECAUSE_KEY").assertDoesNotExist()
        compose.onNodeWithTag("notflix-row-A").assertExists()
    }

    @Test fun myListTitleOpensItsDetailFromItsOwnAddon() {
        val s = PreviewFixtureState()
        val row = requireNotNull(notflixPersonalRow(NOTFLIX_MY_LIST_KEY, "My list", listOf(item("mine", "https://mine.invalid"))))
        show(s, row, null)
        val tag = "notflix-card-${NOTFLIX_MY_LIST_KEY}|${row.catalog!!.stableItemKeys()[0]}"
        compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("mine" to "https://mine.invalid"), details) }
    }
}
