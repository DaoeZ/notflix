package com.nuvio.tv.ui.screens.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** M46: Notflix renders «Mi lista» and its grid; Nuvio keeps the official screen (branch inert). */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NotflixLibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun entry(id: String, type: String = "movie") = LibraryEntry(
        id = id,
        type = type,
        name = "Title $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Synopsis $id",
        releaseInfo = "2019",
        imdbRating = 7.4f,
        genres = listOf("Drama"),
        addonBaseUrl = null
    )

    private fun state(watched: LibraryWatchedFilter = LibraryWatchedFilter.ALL) = LibraryUiState(
        isLoading = false,
        allItems = listOf(entry("1"), entry("2", "series")),
        visibleItems = listOf(entry("1"), entry("2", "series")),
        availableTypeTabs = listOf(
            LibraryTypeTab.All,
            LibraryTypeTab("movie", "Movies"),
            LibraryTypeTab("series", "Series")
        ),
        selectedTypeTab = LibraryTypeTab.All,
        availableSortOptions = LibrarySortOption.LocalOptions,
        selectedSortOption = LibrarySortOption.ADDED_DESC,
        selectedWatchedFilter = watched
    )

    private fun actions(onWatched: (LibraryWatchedFilter) -> Unit = {}) = NotflixLibraryActions(
        onSelectViewMode = {},
        onSelectType = {},
        onSelectList = {},
        onSelectGenre = {},
        onSelectYear = {},
        onSelectWatched = onWatched,
        onSelectSort = {},
        onClearFilters = {},
        onRefresh = {},
        onManageLists = {},
        onOpen = {},
        onLongPress = {},
        onPrefetch = {},
        onCloudProvider = {},
        onCloudType = {},
        onCloudQuery = {},
        onCloudRefresh = {}
    )

    private fun showNotflix(libraryState: LibraryUiState = state()) {
        composeRule.setContent {
            NotflixPresentationTheme(true) {
                CompositionLocalProvider(
                    LocalNotflixPresentation provides NotflixPresentation(
                        active = true,
                        navigation = null,
                        reduceHomeMotion = true
                    )
                ) {
                    // Same decision point as the production screen: the `active` read happens in
                    // this scope, so an appearance flip re-evaluates the branch here.
                    if (LocalNotflixPresentation.current.active) {
                        NotflixLibraryPage(
                            state = libraryState,
                            watchedMovieIds = emptySet(),
                            watchedSeriesIds = emptySet(),
                            viewModes = listOf(LibraryViewMode.Saved),
                            viewMode = LibraryViewMode.Saved,
                            actions = actions(),
                            onNavigateToHome = {},
                            resolvingCloudFileKey = null,
                            onCloudItemClick = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun notflixShowsMyListAndThePosterGrid() {
        showNotflix()

        composeRule.onNodeWithText(context.getString(R.string.notflix_my_list)).assertIsDisplayed()
        composeRule.onNodeWithTag("notflix-poster-grid").assertExists()
        composeRule.onNodeWithTag("notflix-poster-movie:1").assertExists()
        composeRule.onNodeWithTag("notflix-poster-series:2").assertExists()
        composeRule.onNodeWithTag("notflix-focus-header").assertDoesNotExist()
    }

    @Test
    fun activeFiltersShowTheCounterInTheFiltersButton() {
        showNotflix(state(watched = LibraryWatchedFilter.WATCHED))

        val label = context.getString(R.string.notflix_library_filters) + " (1)"
        composeRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun filtersFollowTheLastTypeAndTheListRowCanReturnUp() {
        var up = 0
        val types = state().copy(listTabs = listOf(
            LibraryListTab("a", "A", LibraryListTab.Type.PERSONAL),
            LibraryListTab("b", "B", LibraryListTab.Type.PERSONAL)
        ))
        composeRule.setContent {
            Presentation {
                NotflixSavedFiltersRow(types, "Filters", "List", remember { FocusRequester() },
                    remember { FocusRequester() }, {}, {}, {}, {}, { up++ })
            }
        }
        composeRule.onNodeWithTag("notflix-library-type-series").requestFocus()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("notflix-library-filters").assertIsFocused()
        val typeTop = composeRule.onNodeWithTag("notflix-library-type-series").fetchSemanticsNode().boundsInRoot.center.y
        val filterTop = composeRule.onNodeWithTag("notflix-library-filters").fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(typeTop, filterTop, 1f)
        composeRule.onNodeWithTag("notflix-library-filters").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("notflix-library-list").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("notflix-library-list").assertIsNotFocused()
        composeRule.runOnIdle { assertEquals(0, up) }
        composeRule.onNodeWithTag("notflix-library-filters").requestFocus()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("notflix-library-type-series").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.runOnIdle { assertEquals(1, up) }
    }

    @Test fun manyTypesCanScrollToFiltersWithoutAnEmptyListRow() {
        val types = state().copy(availableTypeTabs = (0..12).map { LibraryTypeTab("type$it", "Type $it") })
        composeRule.setContent {
            Presentation {
                Box(Modifier.width(480.dp)) {
                    NotflixSavedFiltersRow(types, "Filters", null, remember { FocusRequester() },
                        remember { FocusRequester() }, {}, {}, {}, {}, {})
                }
            }
        }
        composeRule.onNodeWithTag("notflix-library-type-type0").requestFocus()
        repeat(13) { index ->
            composeRule.onNodeWithTag("notflix-library-type-type$index")
                .performKeyInput { pressKey(Key.DirectionRight) }
        }
        composeRule.onNodeWithTag("notflix-library-filters").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("notflix-library-list").assertDoesNotExist()
    }

    @Test fun filtersSurviveRecreationAndStayAvailableWhenASelectionHasNoResults() {
        val restoration = StateRestorationTester(composeRule)
        val current = mutableStateOf(state())
        val callbacks = actions { watched ->
            current.value = state(watched).copy(visibleItems = if (watched == LibraryWatchedFilter.WATCHED) emptyList() else state().visibleItems)
        }
        restoration.setContent {
            Presentation {
                NotflixLibraryPage(current.value, emptySet(), emptySet(), listOf(LibraryViewMode.Saved),
                    LibraryViewMode.Saved, callbacks, {}, null, {})
            }
        }
        composeRule.onNodeWithTag("notflix-library-filters").requestFocus().performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("notflix-panel-choice-watched:watched").performClick()
        composeRule.onNodeWithTag("notflix-library-filters").assertIsDisplayed().assertIsFocused().performClick()
        composeRule.onNodeWithTag("notflix-panel-choice-watched:watched").performKeyInput { pressKey(Key.Back) }
        composeRule.onNodeWithTag("notflix-library-filters").assertIsFocused().performClick()
        composeRule.onNodeWithTag("notflix-panel-choice-watched:all").performClick()
        composeRule.onNodeWithTag("notflix-library-filters").assertIsFocused()
        composeRule.onNodeWithTag("notflix-poster-movie:1").assertExists()
    }

    @Composable private fun Presentation(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true)) {
            NotflixPresentationTheme(true, content = content)
        }
    }

    @Test
    fun nuvioAppearanceNeverRendersTheNotflixLibrary() {
        composeRule.setContent {
            NotflixPresentationTheme(false) {
                CompositionLocalProvider(
                    LocalNotflixPresentation provides NotflixPresentation(active = false)
                ) {
                    if (LocalNotflixPresentation.current.active) {
                        NotflixLibraryPage(
                            state = state(),
                            watchedMovieIds = emptySet(),
                            watchedSeriesIds = emptySet(),
                            viewModes = listOf(LibraryViewMode.Saved),
                            viewMode = LibraryViewMode.Saved,
                            actions = actions(),
                            onNavigateToHome = {},
                            resolvingCloudFileKey = null,
                            onCloudItemClick = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.notflix_my_list)).assertDoesNotExist()
        composeRule.onNodeWithTag("notflix-poster-grid").assertDoesNotExist()
    }
}
