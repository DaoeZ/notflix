@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens

import com.nuvio.tv.domain.model.catalogRowLegacyKey
import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.nuvio.tv.ui.util.dpadRepeatThrottle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.LocalCardDepthStyle
import com.nuvio.tv.ui.components.nuvioCardDepth
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.ui.components.posteroptions.posterCardStatusKey
import com.nuvio.tv.ui.screens.home.HomeEvent
import com.nuvio.tv.ui.screens.home.HomeViewModel
import com.nuvio.tv.ui.screens.home.NotflixCardStatus
import com.nuvio.tv.ui.screens.home.allowsForAge
import com.nuvio.tv.ui.screens.home.notflixCardActionHost
import com.nuvio.tv.ui.screens.home.rememberNotflixPreviewSession
import com.nuvio.tv.ui.screens.home.toNotflixCardStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.ui.screens.search.SearchEvent
import com.nuvio.tv.ui.screens.search.SearchViewModel
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.domain.model.stableItemKeys
import com.nuvio.tv.domain.model.stableKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/** Stable focus key for a catalog poster (survives list reloads; not a grid index). */
private fun catalogItemFocusKey(item: MetaPreview): String = "${item.apiType}:${item.id}"

@Composable
fun CatalogSeeAllScreen(
    catalogId: String,
    addonId: String,
    type: String,
    searchViewModel: SearchViewModel? = null,
    serverCatalog: ServerCatalogViewModel? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    posterOptionsViewModel: com.nuvio.tv.ui.components.posteroptions.PosterOptionsViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToPlay: ((String, String, String, Boolean) -> Unit)? = null,
    onBackPress: () -> Unit
) {
    val posterOptionsController = searchViewModel?.posterOptions ?: posterOptionsViewModel.controller
    val uiState by viewModel.uiState.collectAsState()
    val fullCatalogRows by viewModel.fullCatalogRows.collectAsState()
    val computedHeightDp = (uiState.posterCardWidthDp * 1.5f).roundToInt()
    val posterCardStyle = PosterCardStyle(
        width = uiState.posterCardWidthDp.dp,
        height = computedHeightDp.dp,
        cornerRadius = uiState.posterCardCornerRadiusDp.dp,
        focusedBorderWidth = PosterCardDefaults.Style.focusedBorderWidth,
        focusedScale = PosterCardDefaults.Style.focusedScale
    )

    BackHandler { onBackPress() }

    val isSearchMode = searchViewModel != null
    val catalogKey = catalogRowLegacyKey(addonId, type, catalogId)

    // In search mode, get the catalog row from SearchViewModel's existing results.
    // Otherwise fall back to HomeViewModel's fullCatalogRows (home screen catalogs).
    val searchUiState = searchViewModel?.uiState?.collectAsState()
    val searchWatchedMovieIds = searchViewModel?.watchedMovieIds?.collectAsState()
    val searchWatchedSeriesIds = searchViewModel?.watchedSeriesIds?.collectAsState()
    val searchCatalogRow = searchUiState?.value?.catalogRows?.find {
        it.legacyKey() == catalogKey
    }
    val homeCatalogRow = fullCatalogRows.find {
        it.legacyKey() == catalogKey
    }
    val serverRow = serverCatalog?.row?.collectAsState()
    val serverFailed = serverCatalog?.failed?.collectAsState()
    val catalogRow = when {
        serverCatalog != null -> serverRow?.value
        isSearchMode -> searchCatalogRow
        else -> homeCatalogRow
    }
    val catalogItems = remember(catalogRow?.items, uiState.ageLimit, uiState.ageRatings, uiState.hiddenTitleKeys) {
        val rowItems = catalogRow?.items.orEmpty()
        val ageItems = if (uiState.ageLimit == null) rowItems else rowItems.filter { uiState.allowsForAge(it) }
        if (uiState.hiddenTitleKeys.isEmpty()) ageItems else ageItems.filterNot {
            com.nuvio.tv.data.local.hiddenTitleKey(it.apiType, it.id) in uiState.hiddenTitleKeys
        }
    }
    LaunchedEffect(catalogItems) { viewModel.ageGate.prefetch(catalogItems) }

    LaunchedEffect(catalogKey, isSearchMode, catalogRow != null) {
        if (!isSearchMode && serverCatalog == null && catalogRow == null) {
            viewModel.requestLazyCatalogLoad(catalogKey)
        }
    }

    // Rama Notflix de «Ver todo» (M49). Con apariencia Nuvio no se compone: la pantalla oficial
    // sigue igual. Los datos, la paginación y el menú de tarjeta son los mismos de siempre.
    if (LocalNotflixPresentation.current.active) {
        val loadMoreCatalog: () -> Unit = {
            val row = catalogRow
            if (row != null && row.hasMore && !row.isLoading) {
                when {
                    serverCatalog != null -> serverCatalog.loadMore()
                    isSearchMode -> searchViewModel?.onEvent(
                        SearchEvent.LoadMoreCatalog(row.catalogId, row.addonId, row.apiType)
                    )
                    else -> viewModel.onEvent(
                        HomeEvent.OnLoadMoreCatalog(row.catalogId, row.addonId, row.apiType)
                    )
                }
            }
        }
        val catalogFailed = catalogRow == null && serverFailed?.value == true
        // M63: misma tarjeta abierta que la carpeta; el pedido de tráiler es el de Inicio.
        val notflixPosterOptions by posterOptionsController.state.collectAsState()
        val notflixAutoplay by viewModel.effectiveAutoplayEnabled.collectAsStateWithLifecycle(initialValue = false)
        val notflixResolver = remember(viewModel) {
            com.nuvio.tv.ui.screens.home.OfficialNotflixPreviewResolver(
                com.nuvio.tv.ui.screens.home.HomeNotflixPreviewRequestHost(viewModel, requiresHomePresentation = false)
            )
        }
        val notflixPreview = rememberNotflixPreviewSession(
            resolver = notflixResolver,
            dialog = notflixPosterOptions.target != null || notflixPosterOptions.listPickerActive,
            routeActive = true, validContext = { true }, lockContext = true
        )
        // M67: mismas acciones y mismos escritores que Inicio. Mi lista y Visto van por el mismo
        // controlador del menú largo de esta pantalla; Ocultar, por el almacén de títulos ocultos.
        DisposableEffect(posterOptionsController) { onDispose { posterOptionsController.stopCardStatus() } }
        val latestNotflixCardStatus = posterOptionsController.cardStatus.collectAsState()
        val notflixCardActions = remember(posterOptionsController, notflixPosterOptions.librarySourceMode) {
            notflixCardActionHost(
                status = { item ->
                    latestNotflixCardStatus.value[posterCardStatusKey(item.apiType, item.id)]
                        ?.toNotflixCardStatus() ?: NotflixCardStatus()
                },
                manageLists = notflixPosterOptions.librarySourceMode != LibrarySourceMode.LOCAL,
                onExpanded = { item -> posterOptionsController.refreshCardStatus(item) },
                onLibrary = { item, base -> posterOptionsController.toggleLibraryFor(item, base) },
                onWatched = { item -> posterOptionsController.toggleWatchedFor(item) },
                onHide = { item -> viewModel.hideTitle(item) }
            )
        }
        Box(Modifier.fillMaxSize()) {
            NotflixCatalogSeeAllScreen(
                catalogKey = catalogKey,
                title = catalogRow?.catalogName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.catalog_see_all_title_fallback),
                collectionLabel = notflixCatalogCollectionLabel(
                    addonName = catalogRow?.addonName,
                    enabled = uiState.catalogAddonNameEnabled
                )?.let { stringResource(R.string.catalog_see_all_from, it) },
                items = catalogItems,
                isLoading = if (catalogRow == null) !catalogFailed else catalogRow.isLoading,
                hasMore = catalogRow?.hasMore == true,
                notFound = catalogFailed,
                onRetry = if (!isSearchMode && serverCatalog == null) {
                    { viewModel.requestLazyCatalogLoad(catalogKey) }
                } else {
                    null
                },
                onLoadMore = loadMoreCatalog,
                watched = { item ->
                    notflixCatalogItemWatched(
                        item = item,
                        searchMode = isSearchMode,
                        searchWatchedMovieIds = searchWatchedMovieIds?.value ?: emptySet(),
                        searchWatchedSeriesIds = searchWatchedSeriesIds?.value ?: emptySet(),
                        movieWatchedStatus = uiState.movieWatchedStatus
                    )
                },
                onOpen = { item ->
                    onNavigateToDetail(item.id, item.apiType, catalogRow?.addonBaseUrl.orEmpty())
                },
                onLongPress = { item ->
                    posterOptionsController.show(item, catalogRow?.addonBaseUrl.orEmpty())
                },
                onNavigateToDetail = onNavigateToDetail,
                posterController = posterOptionsController,
                previewSession = notflixPreview,
                cardActions = notflixCardActions,
                onCardPlay = onNavigateToPlay?.let { play ->
                    { item -> play(item.id, item.apiType, catalogRow?.addonBaseUrl.orEmpty(), !notflixAutoplay) }
                },
                canPlay = onNavigateToPlay != null
            )
            uiState.hiddenTitleUndo?.takeIf { it.profileId == viewModel.profileManager.activeProfileId.value }?.let { undo ->
                com.nuvio.tv.ui.screens.home.HiddenTitleUndoNotice(undo, viewModel::undoHideTitle,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = NuvioTheme.spacing.xl), autoFocus = false)
            }
        }
        return
    }

    val gridState = rememberLazyGridState()
    val restoreFocusRequester = remember { FocusRequester() }
    // Persist the focused catalog item by stable id (not grid index) so return-from-Details
    // can re-focus the same poster even if the row reloads or the first cell steals focus.
    var focusedItemKey by rememberSaveable(catalogKey) { mutableStateOf<String?>(null) }
    var shouldRestoreFocus by rememberSaveable(catalogKey) { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val focusedItemIndex = remember(catalogItems, focusedItemKey) {
        val items = catalogItems
        if (items.isEmpty()) return@remember 0
        val key = focusedItemKey
        if (key.isNullOrBlank()) return@remember 0
        items.indexOfFirst { catalogItemFocusKey(it) == key }.takeIf { it >= 0 } ?: 0
    }

    // Load more when scrolling near the bottom
    LaunchedEffect(gridState, catalogItems.size) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            lastVisible to total
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 10) {
                    val row = catalogRow
                    if (row != null && row.hasMore && !row.isLoading) {
                        if (serverCatalog != null) {
                            serverCatalog.loadMore()
                        } else if (isSearchMode) {
                            searchViewModel.onEvent(
                                SearchEvent.LoadMoreCatalog(row.catalogId, row.addonId, row.apiType)
                            )
                        } else {
                            viewModel.onEvent(
                                HomeEvent.OnLoadMoreCatalog(row.catalogId, row.addonId, row.apiType)
                            )
                        }
                    }
                }
            }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shouldRestoreFocus = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(shouldRestoreFocus, catalogItems.size, focusedItemKey) {
        if (!shouldRestoreFocus) return@LaunchedEffect
        val items = catalogItems
        if (items.isEmpty()) return@LaunchedEffect

        val targetIndex = focusedItemIndex.coerceIn(0, items.lastIndex)
        val isTargetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!isTargetVisible) {
            gridState.animateScrollToItem(targetIndex)
        }
        repeat(2) { withFrameNanos { } }
        try {
            restoreFocusRequester.requestFocus()
            shouldRestoreFocus = false
        } catch (_: IllegalStateException) {
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = NuvioTheme.spacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTheme.spacing.xxxl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = catalogRow?.catalogName ?: stringResource(R.string.catalog_see_all_title_fallback),
                style = MaterialTheme.typography.headlineLarge,
                color = NuvioTheme.colors.TextPrimary
            )
        }

        if (uiState.catalogAddonNameEnabled) {
            catalogRow?.addonName?.let { addonName ->
                Text(
                    modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl),
                    text = stringResource(R.string.catalog_see_all_from, addonName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))

        val hasItems = catalogItems.isNotEmpty()
        val isCatalogLoading = if (catalogRow == null) serverFailed?.value != true else catalogRow.isLoading

        if (hasItems) {
            val seeAllItemKeys = remember(catalogRow, catalogItems, uiState.ageLimit) {
                if (uiState.ageLimit == null && catalogRow != null) catalogRow.stableItemKeys()
                else catalogItems.map(::catalogItemFocusKey)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(
                        minSize = if (com.nuvio.tv.ui.components.LocalLandscapePosterMode.current) posterCardStyle.height else posterCardStyle.width
                    ),
                    modifier = Modifier.dpadRepeatThrottle(),
                    contentPadding = PaddingValues(
                        start = NuvioTheme.spacing.xxxl,
                        end = NuvioTheme.spacing.xl,
                        top = NuvioTheme.spacing.md,
                        bottom = NuvioTheme.spacing.xxl
                    ),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
                ) {
                    itemsIndexed(
                        items = catalogItems,
                        key = { index, _ -> seeAllItemKeys.getOrElse(index) { "${catalogRow?.stableKey() ?: catalogKey}_$index" } }
                    ) { index, item ->
                        val isWatched = if (isSearchMode) {
                            val isSeries = item.apiType.equals("series", ignoreCase = true) || item.apiType.equals("tv", ignoreCase = true)
                            if (isSeries) item.id in (searchWatchedSeriesIds?.value ?: emptySet())
                            else item.id in (searchWatchedMovieIds?.value ?: emptySet())
                        } else {
                            uiState.movieWatchedStatus[
                                com.nuvio.tv.ui.screens.home.homeItemStatusKey(item.id, item.apiType)
                            ] == true
                        }
                        val itemFocusKey = catalogItemFocusKey(item)
                        GridContentCard(
                            item = item,
                            posterCardStyle = posterCardStyle,
                            showLabel = uiState.posterLabelsEnabled,
                            isWatched = isWatched,
                            focusRequester = if (index == focusedItemIndex) restoreFocusRequester else null,
                            onFocused = {
                                // While restoring after Details/resume, ignore transient focus on the
                                // first/leftmost cell so it cannot overwrite the saved poster key.
                                if (shouldRestoreFocus &&
                                    focusedItemKey != null &&
                                    itemFocusKey != focusedItemKey
                                ) {
                                    return@GridContentCard
                                }
                                focusedItemKey = itemFocusKey
                            },
                            onClick = {
                                focusedItemKey = itemFocusKey
                                onNavigateToDetail(
                                    item.id,
                                    item.apiType,
                                    catalogRow?.addonBaseUrl.orEmpty()
                                )
                            },
                            onLongPress = {
                                focusedItemKey = itemFocusKey
                                posterOptionsController.show(item, catalogRow?.addonBaseUrl.orEmpty())
                            }
                        )
                    }

                    if (catalogRow?.isLoading == true) {
                        item(key = "loading_more") {
                            val cardShape = remember(posterCardStyle.cornerRadius) {
                                androidx.compose.foundation.shape.RoundedCornerShape(posterCardStyle.cornerRadius)
                            }
                            val cardDepthStyle = com.nuvio.tv.ui.components.LocalCardDepthStyle.current
                            val globalLandscape = com.nuvio.tv.ui.components.LocalLandscapePosterMode.current
                            Column(
                                modifier = Modifier
                                    .then(if (globalLandscape) Modifier.fillMaxWidth() else Modifier.width(posterCardStyle.width))
                            ) {
                                androidx.tv.material3.Card(
                                    onClick = {},
                                    modifier = Modifier
                                        .then(
                                            if (globalLandscape) Modifier.fillMaxWidth().aspectRatio(com.nuvio.tv.domain.model.PosterShape.LANDSCAPE.aspectRatio())
                                            else Modifier.width(posterCardStyle.width).height(posterCardStyle.height)
                                        )
                                        .then(Modifier.focusProperties { canFocus = false }),
                                    shape = androidx.tv.material3.CardDefaults.shape(shape = cardShape),
                                    colors = androidx.tv.material3.CardDefaults.colors(
                                        containerColor = NuvioTheme.colors.BackgroundCard,
                                        focusedContainerColor = NuvioTheme.colors.BackgroundCard
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(cardShape)
                                            .then(
                                                Modifier.nuvioCardDepth(
                                                    shape = cardShape,
                                                    surface = com.nuvio.tv.domain.model.CardDepthSurface.POSTERS,
                                                    style = cardDepthStyle
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator()
                                    }
                                }
                                Spacer(
                                    modifier = Modifier
                                        .then(if (globalLandscape) Modifier.fillMaxWidth() else Modifier.width(posterCardStyle.width))
                                        .padding(top = NuvioTheme.spacing.sm)
                                        .height(MaterialTheme.typography.titleMedium.lineHeight.value.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (isCatalogLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            EmptyScreenState(
                title = stringResource(R.string.catalog_see_all_empty_title),
                subtitle = stringResource(R.string.catalog_see_all_empty_subtitle),
                icon = Icons.Default.GridView
            )
        }

        val posterOptionsState by posterOptionsController.state.collectAsState()
        com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
            state = posterOptionsState,
            controller = posterOptionsController,
            onNavigateToDetail = { id, type2, addonBaseUrl ->
                onNavigateToDetail(id, type2, addonBaseUrl)
            }
        )
    }
}
