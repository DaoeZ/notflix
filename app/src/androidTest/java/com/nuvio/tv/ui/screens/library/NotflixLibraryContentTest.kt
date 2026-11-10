package com.nuvio.tv.ui.screens.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M46: Notflix renders «Mi lista» and its grid; Nuvio keeps the official screen (branch inert). */
@RunWith(AndroidJUnit4::class)
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

    private fun actions() = NotflixLibraryActions(
        onSelectViewMode = {},
        onSelectType = {},
        onSelectList = {},
        onSelectGenre = {},
        onSelectYear = {},
        onSelectWatched = {},
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
        composeRule.onNodeWithTag("notflix-focus-header").assertExists()
    }

    @Test
    fun activeFiltersShowTheCounterInTheFiltersButton() {
        showNotflix(state(watched = LibraryWatchedFilter.WATCHED))

        val label = context.getString(R.string.notflix_library_filters) + " (1)"
        composeRule.onNodeWithText(label).assertIsDisplayed()
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
