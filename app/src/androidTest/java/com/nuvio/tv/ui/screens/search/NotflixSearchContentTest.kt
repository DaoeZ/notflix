package com.nuvio.tv.ui.screens.search

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** M31: the Notflix search layer is state + callbacks only, so every case runs with synthetic data. */
@OptIn(ExperimentalTestApi::class)
class NotflixSearchContentTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var back: OnBackPressedDispatcher
    private val queries = mutableListOf<String>()

    private fun item(id: String) = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        name = "Title $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = 7.4f,
        genres = emptyList()
    )

    private fun row(key: String, vararg items: MetaPreview) = CatalogRow(
        addonId = "addon",
        addonName = "Addon",
        addonBaseUrl = "https://addon.invalid",
        catalogId = key,
        catalogName = key,
        type = ContentType.MOVIE,
        items = items.toList()
    )

    private fun show(
        query: MutableState<String>,
        rows: List<CatalogRow> = emptyList(),
        submittedQuery: String = query.value,
        isSearching: Boolean = false,
        recentSearches: List<String> = emptyList()
    ) {
        compose.setContent {
            back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            NotflixPresentationTheme(true) {
                NotflixSearchContent(
                    query = query.value,
                    submittedQuery = submittedQuery,
                    recentSearches = recentSearches,
                    discoverResults = emptyList(),
                    catalogRows = rows,
                    isSearching = isSearching,
                    error = null,
                    isVoiceSearchAvailable = false,
                    isVoiceListening = false,
                    onQueryChange = {
                        queries += it
                        query.value = it
                    },
                    onRecentSearchClick = {
                        queries += it
                        query.value = it
                    },
                    onVoiceSearch = {},
                    onRetry = {},
                    onNavigateToDetail = { _, _, _ -> }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
                it.config.contains(SemanticsProperties.Focused) &&
                    it.config[SemanticsProperties.Focused]
            }
        }
        compose.onNodeWithTag(tag).assertIsFocused()
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(tag).assertIsFocused()
    }

    private fun pressCenter(tag: String) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test
    fun onScreenKeyboardEmitsTheTypedQueryAndBackspaceRemovesTheLastCharacter() {
        val query = mutableStateOf("")
        show(query)
        awaitFocus("notflix-key-A")

        focus("notflix-key-M")
        pressCenter("notflix-key-M")
        compose.waitForIdle()
        assertEquals("M", query.value)

        focus("notflix-key-A")
        pressCenter("notflix-key-A")
        compose.waitForIdle()
        assertEquals("MA", query.value)

        focus("notflix-key-backspace")
        pressCenter("notflix-key-backspace")
        compose.waitForIdle()

        assertEquals(listOf("M", "MA", "M"), queries)
        assertEquals("M", query.value)
    }

    @Test
    fun rightFromTheLastKeyboardColumnEntersTheGridAndLeftReturnsToThatSameKey() {
        val query = mutableStateOf("MA")
        show(query, rows = listOf(row("catalog", item("one"), item("two"))))
        awaitFocus("notflix-key-A")
        focus("notflix-key-9")

        compose.onNodeWithTag("notflix-key-9").performKeyInput { pressKey(Key.DirectionRight) }
        awaitFocus("notflix-search-result-one")

        compose.onNodeWithTag("notflix-search-result-one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        awaitFocus("notflix-key-9")
    }

    @Test
    fun backFromTheGridReturnsToTheKeyboard() {
        val query = mutableStateOf("MA")
        show(query, rows = listOf(row("catalog", item("one"))))
        awaitFocus("notflix-key-A")
        focus("notflix-key-9")
        compose.onNodeWithTag("notflix-key-9").performKeyInput { pressKey(Key.DirectionRight) }
        awaitFocus("notflix-search-result-one")

        compose.runOnIdle { back.onBackPressed() }

        awaitFocus("notflix-key-9")
    }

    @Test
    fun backFromTheGridRestoresTheFocusedSpaceKey() {
        val query = mutableStateOf("MA")
        show(query, rows = listOf(row("catalog", item("one"))))
        awaitFocus("notflix-key-A")
        focus("notflix-key-space")
        focus("notflix-search-result-one")
        compose.waitForIdle()

        compose.runOnIdle { back.onBackPressed() }

        awaitFocus("notflix-key-space")
    }

    @Test
    fun backFromTheGridRestoresTheBackspaceKeyThatOpenedIt() {
        val query = mutableStateOf("MA")
        show(query, rows = listOf(row("catalog", item("one"))))
        awaitFocus("notflix-key-A")
        focus("notflix-key-backspace")
        compose.onNodeWithTag("notflix-key-backspace").performKeyInput { pressKey(Key.DirectionRight) }
        awaitFocus("notflix-search-result-one")

        compose.runOnIdle { back.onBackPressed() }

        awaitFocus("notflix-key-backspace")
    }

    @Test
    fun duplicateResultsAcrossCatalogsRenderOnlyOnce() {
        val query = mutableStateOf("MA")
        show(
            query,
            rows = listOf(
                row("a", item("dup"), item("only-a")),
                row("b", item("only-b"), item("dup"))
            )
        )
        awaitFocus("notflix-key-A")

        compose.onAllNodesWithTag("notflix-search-result-dup").assertCountEquals(1)
        compose.onNodeWithTag("notflix-search-result-only-a").assertExists()
        compose.onNodeWithTag("notflix-search-result-only-b").assertExists()
    }
}
