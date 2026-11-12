package com.nuvio.tv.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.cloud.CloudLibraryItem
import com.nuvio.tv.core.cloud.CloudLibraryItemType
import com.nuvio.tv.core.cloud.CloudLibraryPlaybackInfo
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.localizedTitle
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.notflix.NotflixChip
import com.nuvio.tv.ui.appearance.notflix.NotflixFlatButton
import com.nuvio.tv.ui.appearance.notflix.NotflixFocusHeader
import com.nuvio.tv.ui.appearance.notflix.NotflixHeaderItem
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoice
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoiceRow
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoices
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelGroupTitle
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterGrid
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterMetrics
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterSpec
import com.nuvio.tv.ui.appearance.notflix.NotflixPrimaryButton
import com.nuvio.tv.ui.appearance.notflix.NotflixSidePanel
import com.nuvio.tv.ui.appearance.notflix.NotflixSkeletonBlock
import com.nuvio.tv.ui.appearance.notflix.notflixBleedRight
import com.nuvio.tv.ui.appearance.notflix.notflixHeaderKey
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.home.HeroBackdropState
import com.nuvio.tv.ui.screens.stream.PlayerChoiceDialog
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import kotlinx.coroutines.launch
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/** Every action the Notflix library page may trigger. All of them are owned by the ViewModel. */
@Stable
internal class NotflixLibraryActions(
    val onSelectViewMode: (LibraryViewMode) -> Unit,
    val onSelectType: (LibraryTypeTab) -> Unit,
    val onSelectList: (String) -> Unit,
    val onSelectGenre: (String?) -> Unit,
    val onSelectYear: (String?) -> Unit,
    val onSelectWatched: (LibraryWatchedFilter) -> Unit,
    val onSelectSort: (LibrarySortOption) -> Unit,
    val onClearFilters: () -> Unit,
    val onRefresh: () -> Unit,
    val onManageLists: () -> Unit,
    val onOpen: (LibraryEntry) -> Unit,
    val onLongPress: (LibraryEntry) -> Unit,
    val onPrefetch: (LibraryEntry) -> Unit,
    val onCloudProvider: (String?) -> Unit,
    val onCloudType: (CloudLibraryItemType?) -> Unit,
    val onCloudQuery: (String) -> Unit,
    val onCloudRefresh: () -> Unit
)

/** «Filtros (n)»: genre, year and watched count as three independent filters. */
internal fun notflixActiveFilterCount(
    genre: String?,
    year: String?,
    watched: LibraryWatchedFilter
): Int = listOfNotNull(
    genre?.takeIf { it.isNotBlank() },
    year?.takeIf { it.isNotBlank() },
    watched.takeIf { it != LibraryWatchedFilter.ALL }?.key
).size

/** Pure label so the counter can be proved without Compose. */
internal fun notflixFiltersLabel(base: String, count: Int): String =
    if (count > 0) "$base ($count)" else base

private enum class NotflixLibraryPanel { Sort, Filters, List, CloudProvider, CloudType }

internal fun libraryEntryKey(entry: LibraryEntry): String = "${entry.type}:${entry.id}"

@Composable
internal fun NotflixLibraryPage(
    state: LibraryUiState,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    viewModes: List<LibraryViewMode>,
    viewMode: LibraryViewMode,
    actions: NotflixLibraryActions,
    onNavigateToHome: () -> Unit,
    resolvingCloudFileKey: String?,
    onCloudItemClick: (CloudLibraryItem) -> Unit
) {
    val navigation = LocalNotflixPresentation.current.navigation
    val scope = rememberCoroutineScope()
    val saved = viewMode == LibraryViewMode.Saved
    val specs = remember(state.visibleItems, watchedMovieIds, watchedSeriesIds, saved) {
        if (!saved) {
            emptyList()
        } else {
            state.visibleItems.map { entry ->
                val isSeries = entry.type.equals("series", ignoreCase = true) ||
                    entry.type.equals("tv", ignoreCase = true)
                entry.toNotflixPosterSpec(
                    watched = if (isSeries) entry.id in watchedSeriesIds else entry.id in watchedMovieIds
                )
            }
        }
    }
    val openingLabel = stringResource(R.string.cloud_library_opening)
    val cloudSpecs = remember(state.visibleCloudItems, saved, resolvingCloudFileKey, openingLabel) {
        if (saved) {
            emptyList()
        } else {
            state.visibleCloudItems.map { item ->
                val resolving = resolvingCloudFileKey?.startsWith(item.stableKey) == true
                NotflixPosterSpec(
                    key = item.stableKey,
                    title = item.name,
                    progress = item.progressFraction,
                    badge = if (resolving) openingLabel else null
                )
            }
        }
    }
    val gridSpecs = if (saved) specs else cloudSpecs
    val entriesByKey = remember(state.visibleItems) {
        state.visibleItems.associateBy { libraryEntryKey(it) }
    }
    val cloudByKey = remember(state.visibleCloudItems) {
        state.visibleCloudItems.associateBy { it.stableKey }
    }

    var focusedKey by remember(viewMode) { mutableStateOf<String?>(null) }
    var panel by rememberSaveable { mutableStateOf<NotflixLibraryPanel?>(null) }
    val sortRequester = remember { FocusRequester() }
    val filtersRequester = remember { FocusRequester() }
    val listRequester = remember { FocusRequester() }
    val providerRequester = remember { FocusRequester() }
    val typeRequester = remember { FocusRequester() }
    val emptyActionRequester = remember { FocusRequester() }

    fun closePanel() {
        val opened = panel
        panel = null
        val target = opened?.let {
            when (it) {
                NotflixLibraryPanel.Sort -> sortRequester
                NotflixLibraryPanel.Filters -> filtersRequester
                NotflixLibraryPanel.List -> listRequester
                NotflixLibraryPanel.CloudProvider -> providerRequester
                NotflixLibraryPanel.CloudType -> typeRequester
            }
        }
        if (target != null) scope.launch {
            repeat(2) { withFrameNanos { } }
            runCatching { target.requestFocus() }
        }
    }

    val headerItem: NotflixHeaderItem? = if (saved) {
        val key = notflixHeaderKey(focusedKey, specs.map { it.key })
        key?.let { entriesByKey[it] }?.let { entry ->
            NotflixHeaderItem(
                key = libraryEntryKey(entry),
                title = entry.name,
                backdrop = entry.background,
                poster = entry.poster,
                logo = entry.logo,
                metadata = buildList {
                    entry.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
                    entry.genres.take(2).forEach { add(localizedGenreLabel(it)) }
                },
                overview = entry.description
            )
        }
    } else {
        val key = notflixHeaderKey(focusedKey, cloudSpecs.map { it.key })
        key?.let { cloudByKey[it] }?.let { item ->
            NotflixHeaderItem(
                key = item.stableKey,
                title = item.name,
                metadata = listOfNotNull(
                    item.providerName.takeIf { it.isNotBlank() },
                    item.type.localizedLabel()
                )
            )
        }
    }

    val filtersCount = notflixActiveFilterCount(
        genre = state.selectedGenre,
        year = state.selectedYear,
        watched = state.selectedWatchedFilter
    )
    val filtersLabel = notflixFiltersLabel(
        base = stringResource(R.string.notflix_library_filters),
        count = filtersCount
    )
    val selectedListTitle = state.listTabs.firstOrNull { it.key == state.selectedListKey }?.localizedTitle()
    val trackingNotAuthenticated = state.sourceMode != LibrarySourceMode.LOCAL && !state.isTrackingAuthenticated
    val noSavedItems = saved && state.visibleItems.isEmpty() && !state.isLoading
    val cloudRefreshingEmpty = !saved && state.visibleCloudItems.isEmpty() && state.cloudLibrary.isRefreshing
    val noCloudItems = !saved && state.visibleCloudItems.isEmpty() && !state.cloudLibrary.isRefreshing

    Box(Modifier.fillMaxSize().background(Spec.Background)) {
        when {
            (state.isLoading && saved) || cloudRefreshingEmpty -> NotflixLibrarySkeleton()

            saved && state.errorMessage != null && state.visibleItems.isEmpty() -> NotflixLibraryMessageState(
                title = null,
                message = state.errorMessage,
                actionLabel = stringResource(R.string.notflix_retry),
                actionRequester = null,
                onAction = actions.onRefresh
            )

            trackingNotAuthenticated && noSavedItems -> NotflixLibraryMessageState(
                title = stringResource(
                    when (state.sourceMode) {
                        LibrarySourceMode.SIMKL -> R.string.library_empty_simkl_not_auth_title
                        else -> R.string.library_empty_trakt_not_auth_title
                    }
                ),
                message = stringResource(
                    when (state.sourceMode) {
                        LibrarySourceMode.SIMKL -> R.string.library_empty_simkl_not_auth_subtitle
                        else -> R.string.library_empty_trakt_not_auth_subtitle
                    }
                ),
                actionLabel = stringResource(R.string.notflix_retry),
                actionRequester = null,
                onAction = actions.onRefresh
            )

            saved && noSavedItems && filtersCount > 0 && state.allItems.isNotEmpty() -> NotflixLibraryMessageState(
                title = stringResource(R.string.notflix_library_no_filter_match),
                message = null,
                actionLabel = stringResource(R.string.notflix_library_clear_filters),
                actionRequester = emptyActionRequester,
                onAction = actions.onClearFilters
            )

            saved && noSavedItems && filtersCount == 0 -> NotflixLibraryEmptyState(
                actionRequester = emptyActionRequester,
                onExplore = onNavigateToHome
            )

            !saved && !state.cloudLibrary.isEnabled -> NotflixLibraryMessageState(
                title = stringResource(R.string.cloud_library_disabled_title),
                message = stringResource(R.string.cloud_library_disabled_message),
                actionLabel = null,
                actionRequester = null,
                onAction = {}
            )

            !saved && !state.cloudLibrary.hasConnectedProvider && !state.cloudLibrary.isRefreshing ->
                NotflixLibraryMessageState(
                    title = stringResource(R.string.cloud_library_connect_title),
                    message = stringResource(R.string.cloud_library_connect_message),
                    actionLabel = null,
                    actionRequester = null,
                    onAction = {}
                )

            noCloudItems -> NotflixLibraryMessageState(
                title = stringResource(R.string.cloud_library_empty_title),
                message = stringResource(R.string.cloud_library_empty_message),
                actionLabel = null,
                actionRequester = null,
                onAction = {}
            )

            else -> NotflixPosterGrid(
                items = gridSpecs,
                onClick = { spec ->
                    if (saved) {
                        entriesByKey[spec.key]?.let(actions.onOpen)
                    } else {
                        cloudByKey[spec.key]?.let(onCloudItemClick)
                    }
                },
                state = rememberLazyGridState(),
                saveKey = "notflix-library-${viewMode.name}",
                requestInitialFocus = gridSpecs.isNotEmpty(),
                onFocused = { spec ->
                    focusedKey = spec.key
                    if (saved) entriesByKey[spec.key]?.let(actions.onPrefetch)
                },
                onLongClick = if (saved) {
                    { spec -> entriesByKey[spec.key]?.let(actions.onLongPress) }
                } else {
                    null
                },
                header = {
                    // Promoter (08-11): no focused-title header on list pages; title and grid only.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NotflixLibraryTitleRow(
                            count = if (saved) state.visibleItems.size else state.visibleCloudItems.size,
                            sortLabel = state.availableSortOptions
                                .firstOrNull { it.key == state.selectedSortOption.key }
                                ?.let { stringResource(it.labelResId) },
                            sortRequester = sortRequester,
                            onOpenSort = { panel = NotflixLibraryPanel.Sort }
                        )
                    }
                    if (viewModes.size > 1) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NotflixLibraryModeRow(
                                viewModes = viewModes,
                                viewMode = viewMode,
                                onSelect = actions.onSelectViewMode
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (saved) {
                            NotflixSavedFiltersRow(
                                state = state,
                                filtersLabel = filtersLabel,
                                listLabel = selectedListTitle?.let {
                                    stringResource(R.string.notflix_library_list_selected, it)
                                },
                                filtersRequester = filtersRequester,
                                listRequester = listRequester,
                                onSelectType = actions.onSelectType,
                                onOpenList = { panel = NotflixLibraryPanel.List },
                                onOpenFilters = { panel = NotflixLibraryPanel.Filters },
                                onManageLists = actions.onManageLists,
                                onUpToTopBar = { navigation?.focusTop() }
                            )
                        } else {
                            NotflixCloudFiltersRow(
                                state = state,
                                providerRequester = providerRequester,
                                typeRequester = typeRequester,
                                onOpenProvider = { panel = NotflixLibraryPanel.CloudProvider },
                                onOpenType = { panel = NotflixLibraryPanel.CloudType },
                                onQueryChange = actions.onCloudQuery,
                                onCloudRefresh = actions.onCloudRefresh,
                                onUpToTopBar = { navigation?.focusTop() }
                            )
                        }
                    }
                }
            )
        }

        state.transientMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Box(Modifier.fillMaxSize().padding(Spec.GUTTER.dp), contentAlignment = Alignment.BottomStart) {
                Text(
                    text = message,
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(Spec.Surface.copy(alpha = .95f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("notflix-library-message")
                )
            }
        }

        // Panels are overlays: the grid is never remounted, so the poster keeps its focus state.
        when (panel) {
            NotflixLibraryPanel.Sort -> NotflixSidePanel(
                title = stringResource(R.string.library_filter_sort),
                onDismiss = { closePanel() }
            ) {
                val choices = state.availableSortOptions.map { option ->
                    NotflixPanelChoice(
                        key = option.key,
                        label = stringResource(option.labelResId),
                        selected = option.key == state.selectedSortOption.key
                    )
                }
                NotflixPanelChoices(choices = choices, onSelect = { choice ->
                    state.availableSortOptions.firstOrNull { it.key == choice.key }?.let(actions.onSelectSort)
                    closePanel()
                })
            }

            NotflixLibraryPanel.List -> NotflixSidePanel(
                title = stringResource(R.string.library_filter_list),
                onDismiss = { closePanel() }
            ) {
                val choices = state.listTabs.map { tab ->
                    NotflixPanelChoice(
                        key = tab.key,
                        label = tab.localizedTitle(),
                        selected = tab.key == state.selectedListKey
                    )
                }
                NotflixPanelChoices(choices = choices, onSelect = { choice ->
                    actions.onSelectList(choice.key)
                    closePanel()
                })
            }

            NotflixLibraryPanel.Filters -> NotflixSidePanel(
                title = stringResource(R.string.notflix_library_filters),
                onDismiss = { closePanel() }
            ) {
                NotflixFiltersPanel(
                    state = state,
                    onSelectGenre = { actions.onSelectGenre(it); closePanel() },
                    onSelectYear = { actions.onSelectYear(it); closePanel() },
                    onSelectWatched = { actions.onSelectWatched(it); closePanel() }
                )
            }

            NotflixLibraryPanel.CloudProvider -> NotflixSidePanel(
                title = stringResource(R.string.cloud_library_select_provider),
                onDismiss = { closePanel() }
            ) {
                val choices = listOf(
                    NotflixPanelChoice(
                        key = "__all__",
                        label = stringResource(R.string.cloud_library_provider_all),
                        selected = state.selectedCloudProviderId == null
                    )
                ) + state.availableCloudProviders.map { option ->
                    NotflixPanelChoice(
                        key = option.key,
                        label = "${option.label} (${option.count})",
                        selected = option.key == state.selectedCloudProviderId
                    )
                }
                NotflixPanelChoices(choices = choices, onSelect = { choice ->
                    actions.onCloudProvider(choice.key.takeIf { it != "__all__" })
                    closePanel()
                })
            }

            NotflixLibraryPanel.CloudType -> NotflixSidePanel(
                title = stringResource(R.string.cloud_library_select_type),
                onDismiss = { closePanel() }
            ) {
                val choices = listOf(
                    NotflixPanelChoice(
                        key = "__all__",
                        label = stringResource(R.string.cloud_library_type_all),
                        selected = state.selectedCloudType == null
                    )
                ) + state.availableCloudTypes.map { option ->
                    NotflixPanelChoice(
                        key = option.key,
                        label = "${option.label} (${option.count})",
                        selected = option.key == state.selectedCloudType?.name
                    )
                }
                NotflixPanelChoices(choices = choices, onSelect = { choice ->
                    val type = choice.key.takeIf { it != "__all__" }
                        ?.let { key -> CloudLibraryItemType.entries.firstOrNull { it.name == key } }
                    actions.onCloudType(type)
                    closePanel()
                })
            }

            null -> Unit
        }
    }
}

@Composable
private fun NotflixLibraryTitleRow(
    count: Int,
    sortLabel: String?,
    sortRequester: FocusRequester,
    onOpenSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.notflix_my_list),
            color = Spec.Text,
            fontFamily = Spec.BrandFont,
            fontSize = 40.sp,
            lineHeight = 44.sp
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = pluralStringResource(R.plurals.notflix_library_title_count, count, count),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.weight(1f))
        if (sortLabel != null) {
            NotflixFlatButton(
                label = sortLabel,
                onClick = onOpenSort,
                modifier = Modifier.focusRequester(sortRequester),
                testTag = "notflix-library-sort"
            )
        }
    }
}

@Composable
private fun NotflixLibraryModeRow(
    viewModes: List<LibraryViewMode>,
    viewMode: LibraryViewMode,
    onSelect: (LibraryViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        viewModes.forEach { mode ->
            NotflixChip(
                label = stringResource(
                    when (mode) {
                        LibraryViewMode.Saved -> R.string.library_source_saved
                        LibraryViewMode.Cloud -> R.string.library_source_cloud
                        LibraryViewMode.Servers -> R.string.library_source_servers
                    }
                ),
                selected = mode == viewMode,
                onClick = { onSelect(mode) },
                testTag = "notflix-library-mode-${mode.name}"
            )
        }
    }
}

@Composable
internal fun NotflixSavedFiltersRow(
    state: LibraryUiState,
    filtersLabel: String,
    listLabel: String?,
    filtersRequester: FocusRequester,
    listRequester: FocusRequester,
    onSelectType: (LibraryTypeTab) -> Unit,
    onOpenList: () -> Unit,
    onOpenFilters: () -> Unit,
    onManageLists: () -> Unit,
    onUpToTopBar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUpToTopBar()
                    true
                } else {
                    false
                }
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.availableTypeTabs.forEach { tab ->
                    NotflixChip(
                        label = if (tab.key == LibraryTypeTab.ALL_KEY) {
                            stringResource(R.string.library_type_all)
                        } else {
                            localizedContentType(tab.key)
                        },
                        selected = tab.key == state.selectedTypeTab?.key,
                        onClick = { onSelectType(tab) },
                        testTag = "notflix-library-type-${tab.key}"
                    )
                }
                // M60 PARTE 2 (promotor 11-11): Filters lives next to the type chips, right after the
                // last one and before the spacer.
                NotflixFlatButton(
                    label = filtersLabel,
                    onClick = onOpenFilters,
                    modifier = Modifier.focusRequester(filtersRequester),
                    testTag = "notflix-library-filters"
                )
            }
            if (state.listManagement != null && state.isTrackingAuthenticated) {
                NotflixFlatButton(
                    label = stringResource(R.string.library_manage_lists),
                    onClick = onManageLists,
                    trailing = "",
                    testTag = "notflix-library-manage"
                )
            }
        }
        // The list button stays on its own row; do not draw an empty row when there is none.
        if (state.listTabs.size > 1 && listLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NotflixFlatButton(
                    label = listLabel,
                    onClick = onOpenList,
                    modifier = Modifier.focusRequester(listRequester),
                    testTag = "notflix-library-list"
                )
            }
        }
    }
}

@Composable
private fun NotflixCloudFiltersRow(
    state: LibraryUiState,
    providerRequester: FocusRequester,
    typeRequester: FocusRequester,
    onOpenProvider: () -> Unit,
    onOpenType: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCloudRefresh: () -> Unit,
    onUpToTopBar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 10.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUpToTopBar()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            NotflixFlatButton(
                label = state.availableCloudProviders
                    .firstOrNull { it.key == state.selectedCloudProviderId }?.label
                    ?: stringResource(R.string.cloud_library_provider_all),
                onClick = onOpenProvider,
                modifier = Modifier.focusRequester(providerRequester),
                testTag = "notflix-library-cloud-provider"
            )
            NotflixFlatButton(
                label = state.selectedCloudType
                    ?.let { type -> state.availableCloudTypes.firstOrNull { it.key == type.name }?.label }
                    ?: stringResource(R.string.cloud_library_type_all),
                onClick = onOpenType,
                modifier = Modifier.focusRequester(typeRequester),
                testTag = "notflix-library-cloud-type"
            )
            Spacer(Modifier.weight(1f))
            NotflixFlatButton(
                label = stringResource(R.string.cloud_library_refresh),
                onClick = onCloudRefresh,
                trailing = "",
                testTag = "notflix-library-cloud-refresh"
            )
        }
        NotflixSearchField(
            value = state.cloudSearchQuery,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.cloud_library_search_label)
        )
    }
}

@Composable
private fun NotflixSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val fieldRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldRequester.requestFocus() }
            keyboard?.show()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Spec.Surface.copy(alpha = if (focused) .9f else .55f), RoundedCornerShape(3.dp))
            .border(
                width = 2.dp,
                color = if (focused) Spec.Text else Color.Transparent,
                shape = RoundedCornerShape(3.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (!it.hasFocus && editing) editing = false
            }
            .clickable(role = Role.Button) {
                editing = true
                keyboard?.show()
            }
            .testTag("notflix-library-cloud-search")
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .focusRequester(fieldRequester)
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (!it.isFocused && editing) editing = false }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            editing = false
                            keyboard?.hide()
                            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                            true
                        }
                        Key.DirectionDown -> {
                            editing = false
                            keyboard?.hide()
                            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                            true
                        }
                        else -> false
                    }
                },
            readOnly = !editing,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                editing = false
                keyboard?.hide()
            }),
            textStyle = TextStyle(
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                textDirection = TextDirection.Content
            ),
            cursorBrush = SolidColor(if (editing) Spec.Accent else Color.Transparent),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Spec.Secondary.copy(alpha = .7f),
                        fontFamily = Spec.BodyFont,
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun NotflixFiltersPanel(
    state: LibraryUiState,
    onSelectGenre: (String?) -> Unit,
    onSelectYear: (String?) -> Unit,
    onSelectWatched: (LibraryWatchedFilter) -> Unit
) {
    val allLabel = stringResource(R.string.library_type_all)
    val genreChoices = listOf(NotflixPanelChoice("__all_genre", allLabel, state.selectedGenre == null)) +
        state.availableGenres.map {
            NotflixPanelChoice(
                key = "genre:${it.key}",
                label = "${localizedGenreLabel(it.label)} (${it.count})",
                selected = it.key == state.selectedGenre
            )
        }
    val yearChoices = listOf(NotflixPanelChoice("__all_year", allLabel, state.selectedYear == null)) +
        state.availableYears.map {
            NotflixPanelChoice(
                key = "year:${it.key}",
                label = "${it.label} (${it.count})",
                selected = it.key == state.selectedYear
            )
        }
    val watchedChoices = LibraryWatchedFilter.entries.map {
        NotflixPanelChoice(
            key = "watched:${it.key}",
            label = stringResource(it.labelResId),
            selected = it == state.selectedWatchedFilter
        )
    }
    val rows = buildList {
        if (genreChoices.size > 1) {
            add(NotflixFilterRow.Group("notflix-panel-genre", stringResource(R.string.library_filter_genre)))
            genreChoices.forEach { add(NotflixFilterRow.Choice(it)) }
        }
        if (yearChoices.size > 1) {
            add(NotflixFilterRow.Group("notflix-panel-year", stringResource(R.string.library_filter_year)))
            yearChoices.forEach { add(NotflixFilterRow.Choice(it)) }
        }
        add(NotflixFilterRow.Group("notflix-panel-watched", stringResource(R.string.library_filter_watched)))
        watchedChoices.forEach { add(NotflixFilterRow.Choice(it)) }
    }
    val keys = remember(rows) { rows.map { it.key } }
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    LaunchedEffect(keys) {
        val target = rows.firstOrNull { it.selected }?.key
            ?: rows.firstOrNull { it is NotflixFilterRow.Choice }?.key
        target?.let { key -> requesters[key]?.requestFocusAfterFrames(frames = 2) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is NotflixFilterRow.Group -> NotflixPanelGroupTitle(row.label)
                is NotflixFilterRow.Choice -> NotflixPanelChoiceRow(
                    choice = row.choice,
                    modifier = Modifier.focusRequester(
                        requesters[row.key] ?: remember(row.key) { FocusRequester() }
                    ),
                    onClick = {
                        when {
                            row.choice.key.startsWith("genre:") ->
                                onSelectGenre(row.choice.key.removePrefix("genre:"))
                            row.choice.key == "__all_genre" -> onSelectGenre(null)
                            row.choice.key.startsWith("year:") ->
                                onSelectYear(row.choice.key.removePrefix("year:"))
                            row.choice.key == "__all_year" -> onSelectYear(null)
                            row.choice.key.startsWith("watched:") -> {
                                val key = row.choice.key.removePrefix("watched:")
                                LibraryWatchedFilter.entries.firstOrNull { it.key == key }
                                    ?.let(onSelectWatched)
                            }
                        }
                    }
                )
            }
        }
    }
}

private sealed interface NotflixFilterRow {
    val key: String
    val selected: Boolean

    data class Group(override val key: String, val label: String) : NotflixFilterRow {
        override val selected: Boolean get() = false
    }

    data class Choice(val choice: NotflixPanelChoice) : NotflixFilterRow {
        override val key: String get() = choice.key
        override val selected: Boolean get() = choice.selected
    }
}

@Composable
private fun NotflixLibrarySkeleton() {
    Column(Modifier.fillMaxSize().testTag("notflix-library-skeleton")) {
        NotflixSkeletonBlock(Modifier.fillMaxWidth().height(230.dp), radius = 0.dp)
        NotflixSkeletonBlock(
            Modifier
                .padding(start = Spec.GUTTER.dp, top = 18.dp)
                .width(180.dp)
                .height(30.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)
        ) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)) {
                    repeat(NotflixPosterMetrics.COLUMNS) {
                        NotflixSkeletonBlock(
                            Modifier
                                .width(NotflixPosterMetrics.Width)
                                .height(NotflixPosterMetrics.Height)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotflixLibraryEmptyState(
    actionRequester: FocusRequester,
    onExplore: () -> Unit
) {
    LaunchedEffect(Unit) { actionRequester.requestFocusAfterFrames(frames = 2) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spec.GUTTER.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.notflix_mark),
            contentDescription = null,
            alpha = .25f,
            modifier = Modifier.size(84.dp)
        )
        Text(
            text = stringResource(R.string.notflix_library_empty_title),
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.notflix_library_empty_message),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        NotflixPrimaryButton(
            label = stringResource(R.string.notflix_library_empty_action),
            onClick = onExplore,
            modifier = Modifier
                .padding(top = 18.dp)
                .focusRequester(actionRequester),
            testTag = "notflix-library-empty-action"
        )
    }
}

@Composable
private fun NotflixLibraryMessageState(
    title: String?,
    message: String?,
    actionLabel: String?,
    actionRequester: FocusRequester?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spec.GUTTER.dp),
        verticalArrangement = Arrangement.Center
    ) {
        title?.let {
            Text(
                text = it,
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        message?.let {
            Text(
                text = it,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actionLabel != null) {
            NotflixPrimaryButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .then(if (actionRequester != null) Modifier.focusRequester(actionRequester) else Modifier),
                testTag = "notflix-library-state-action"
            )
        }
    }
}

/**
 * Notflix branch of the Library screen. It owns no data: every action is the official
 * [LibraryViewModel] one and the official screen keeps its own untoched path.
 */
@Composable
internal fun NotflixLibraryScreen(
    viewModel: LibraryViewModel,
    viewMode: LibraryViewMode,
    viewModes: List<LibraryViewMode>,
    onSelectViewMode: (LibraryViewMode) -> Unit,
    routeCloudPlayback: (CloudLibraryPlaybackInfo) -> Unit,
    onInternalCloudPlayback: (CloudLibraryPlaybackInfo) -> Unit,
    onExternalCloudPlayback: (CloudLibraryPlaybackInfo) -> Unit,
    onNavigateToDetail: (String, String, String?) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val watchedMovieIds by viewModel.watchedMovieIds.collectAsState()
    val watchedSeriesIds by viewModel.watchedSeriesIds.collectAsState()
    val posterOptionsState by viewModel.posterOptions.state.collectAsState()
    var showDeleteConfirm by remember(uiState.showManageDialog) { mutableStateOf(false) }
    var activeCloudItem by remember { mutableStateOf<CloudLibraryItem?>(null) }
    var pendingCloudPlayback by remember { mutableStateOf<CloudLibraryPlaybackInfo?>(null) }
    var showCloudPlayerChoice by remember { mutableStateOf(false) }

    LaunchedEffect(
        viewMode,
        uiState.cloudLibrary.isEnabled,
        uiState.cloudLibrary.isLoaded,
        uiState.cloudLibrarySettingsVersion
    ) {
        if (viewMode == LibraryViewMode.Cloud) viewModel.ensureCloudLibraryLoaded()
    }

    val actions = NotflixLibraryActions(
        onSelectViewMode = onSelectViewMode,
        onSelectType = viewModel::onSelectTypeTab,
        onSelectList = viewModel::onSelectListTab,
        onSelectGenre = viewModel::onSelectGenre,
        onSelectYear = viewModel::onSelectYear,
        onSelectWatched = viewModel::onSelectWatchedFilter,
        onSelectSort = viewModel::onSelectSortOption,
        onClearFilters = {
            viewModel.onSelectGenre(null)
            viewModel.onSelectYear(null)
            viewModel.onSelectWatchedFilter(LibraryWatchedFilter.ALL)
        },
        onRefresh = viewModel::onRefresh,
        onManageLists = viewModel::onOpenManageLists,
        onOpen = { entry ->
            HeroBackdropState.update(viewModel.getCachedBackdrop(entry.id, entry.type))
            onNavigateToDetail(entry.id, entry.type, entry.addonBaseUrl)
        },
        onLongPress = { entry ->
            viewModel.posterOptions.show(
                entry.toMetaPreview().copy(posterShape = PosterShape.POSTER),
                entry.addonBaseUrl
            )
        },
        onPrefetch = { entry -> viewModel.prefetchMetaOnFocus(entry.id, entry.type) },
        onCloudProvider = viewModel::onSelectCloudProvider,
        onCloudType = viewModel::onSelectCloudType,
        onCloudQuery = viewModel::onCloudSearchQueryChange,
        onCloudRefresh = viewModel::refreshCloudLibrary
    )

    NotflixLibraryPage(
        state = uiState,
        watchedMovieIds = watchedMovieIds,
        watchedSeriesIds = watchedSeriesIds,
        viewModes = viewModes,
        viewMode = viewMode,
        actions = actions,
        onNavigateToHome = onNavigateToHome,
        resolvingCloudFileKey = uiState.resolvingCloudFileKey,
        onCloudItemClick = { item ->
            val playableFiles = item.playableFiles
            when (playableFiles.size) {
                0 -> viewModel.onCloudItemHasNoPlayableFiles()
                1 -> viewModel.resolveCloudPlayback(item, playableFiles.first(), routeCloudPlayback)
                else -> activeCloudItem = item
            }
        }
    )

    if (uiState.showManageDialog && uiState.listManagement != null) {
        ManageListsDialog(
            capabilities = requireNotNull(uiState.listManagement),
            tabs = uiState.listTabs,
            selectedKey = uiState.manageSelectedListKey,
            errorMessage = uiState.errorMessage,
            pending = uiState.pendingOperation,
            onSelect = viewModel::onSelectManageList,
            onCreate = viewModel::onStartCreateList,
            onEdit = viewModel::onStartEditList,
            onMoveUp = viewModel::onMoveSelectedListUp,
            onMoveDown = viewModel::onMoveSelectedListDown,
            onDelete = { showDeleteConfirm = true },
            onDismiss = viewModel::onCloseManageLists,
            notflix = true
        )
    }

    if (showDeleteConfirm && uiState.showManageDialog) {
        ConfirmDeleteDialog(
            pending = uiState.pendingOperation,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.onDeleteSelectedList()
            },
            onCancel = { showDeleteConfirm = false },
            notflix = true
        )
    }

    val listEditor = uiState.listEditorState
    if (listEditor != null && uiState.showManageDialog && uiState.listManagement != null) {
        ListEditorDialog(
            capabilities = requireNotNull(uiState.listManagement),
            state = listEditor,
            pending = uiState.pendingOperation,
            onNameChanged = viewModel::onUpdateEditorName,
            onDescriptionChanged = viewModel::onUpdateEditorDescription,
            onPrivacyChanged = viewModel::onUpdateEditorPrivacy,
            onSave = viewModel::onSubmitEditor,
            onCancel = viewModel::onCancelEditor,
            notflix = true
        )
    }

    activeCloudItem?.let { item ->
        CloudFilePickerDialog(
            item = item,
            resolvingFileKey = uiState.resolvingCloudFileKey,
            onPlay = { file ->
                viewModel.resolveCloudPlayback(item, file) { info ->
                    activeCloudItem = null
                    routeCloudPlayback(info)
                }
            },
            onDismiss = { activeCloudItem = null }
        )
    }

    if (showCloudPlayerChoice && pendingCloudPlayback != null) {
        PlayerChoiceDialog(
            onInternalSelected = {
                showCloudPlayerChoice = false
                pendingCloudPlayback?.let(onInternalCloudPlayback)
                pendingCloudPlayback = null
            },
            onExternalSelected = {
                showCloudPlayerChoice = false
                pendingCloudPlayback?.let(onExternalCloudPlayback)
                pendingCloudPlayback = null
            },
            onDismiss = {
                showCloudPlayerChoice = false
                pendingCloudPlayback = null
            }
        )
    }

    com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
        state = posterOptionsState,
        controller = viewModel.posterOptions,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            onNavigateToDetail(id, type, addonBaseUrl.takeIf { it.isNotBlank() })
        }
    )
}
