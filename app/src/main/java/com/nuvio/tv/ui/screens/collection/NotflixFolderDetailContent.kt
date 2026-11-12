package com.nuvio.tv.ui.screens.collection

import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.nuvio.tv.ui.screens.home.HiddenTitleUndoNotice
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryMessage
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryPage
import com.nuvio.tv.ui.appearance.notflix.NotflixCategorySkeleton
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryTab
import com.nuvio.tv.ui.appearance.notflix.NotflixHeaderItem
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryPosterKeys
import com.nuvio.tv.ui.appearance.notflix.toNotflixCategoryHeader
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost
import com.nuvio.tv.ui.components.posteroptions.posterCardStatusKey
import com.nuvio.tv.ui.screens.home.FolderNotflixPreviewRequestHost
import com.nuvio.tv.ui.screens.home.HeroBackdropState
import com.nuvio.tv.ui.screens.home.NotflixCardActionsHost
import com.nuvio.tv.ui.screens.home.NotflixCardStatus
import com.nuvio.tv.ui.screens.home.OfficialNotflixPreviewResolver
import com.nuvio.tv.ui.screens.home.homeItemStatusKey
import com.nuvio.tv.ui.screens.home.notflixCardActionHost
import com.nuvio.tv.ui.screens.home.rememberNotflixPreviewSession
import com.nuvio.tv.ui.screens.home.toNotflixCardStatus

/** Prefijo de los elementos de relleno que el ViewModel usa mientras carga un catálogo. */
private const val NOTFLIX_FOLDER_PLACEHOLDER_PREFIX = "__placeholder"

/** Una pestaña ya convertida: modelo de la página común y lo necesario para navegar. */
internal data class NotflixFolderTabData(
    val tab: NotflixCategoryTab,
    val itemsByKey: Map<String, MetaPreview>,
    val addonBaseUrl: String
)

/** Keep each tab's original metadata and add-on URL, even when another tab repeats the id. */
internal fun notflixFolderSelectedTab(tabs: List<NotflixFolderTabData>, selectedIndex: Int): NotflixFolderTabData? =
    tabs.getOrNull(com.nuvio.tv.ui.appearance.notflix.notflixCategoryTabIndex(selectedIndex, tabs.size))

/**
 * M49: convierte una pestaña de la carpeta en el modelo de la página común. No pide nada: usa el
 * `catalogRow` que el ViewModel ya cargó, filtra los rellenos de carga y aplica la regla de vistos
 * de M39 con el mismo `movieWatchedStatus` que la pantalla oficial.
 */
internal fun notflixFolderTabData(
    index: Int,
    tab: FolderTab,
    watchedStatus: Map<String, Boolean>,
    hiddenKeys: Set<String> = emptySet()
): NotflixFolderTabData {
    val rowItems = tab.catalogRow?.items.orEmpty()
        .filterNot { it.id.startsWith(NOTFLIX_FOLDER_PLACEHOLDER_PREFIX) }
        // M67: los títulos ocultos del perfil desaparecen de la rejilla, como en Inicio.
        .filterNot { hiddenTitleKey(it.apiType, it.id) in hiddenKeys }
    val keys = notflixCategoryPosterKeys(rowItems)
    val specs = rowItems.mapIndexed { itemIndex, item ->
        item.toNotflixPosterSpec(
            watched = watchedStatus[homeItemStatusKey(item.id, item.apiType)] == true
        ).copy(key = keys[itemIndex])
    }
    return NotflixFolderTabData(
        tab = NotflixCategoryTab(
            key = "tab-$index-${tab.label}",
            label = tab.label,
            items = specs,
            isLoading = tab.isLoading || tab.catalogRow?.isLoading == true,
            error = tab.error,
            hasMore = tab.catalogRow?.hasMore == true
        ),
        itemsByKey = rowItems.mapIndexed { itemIndex, item -> keys[itemIndex] to item }.toMap(),
        addonBaseUrl = tab.catalogRow?.addonBaseUrl.orEmpty()
    )
}

/**
 * Rama Notflix de una carpeta de colección: página de categoría con cabecera del enfocado, título
 * grande, chips de catálogos y cuadrícula de seis columnas. Con apariencia Nuvio no se compone:
 * `FolderDetailScreen` sigue dibujando su ruta oficial sin cambios.
 */
@Composable
internal fun NotflixFolderDetailScreen(
    viewModel: FolderDetailViewModel,
    uiState: FolderDetailUiState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToPlay: ((String, String, String) -> Unit)? = null
) {
    val posterOptionsState by viewModel.posterOptions.state.collectAsStateWithLifecycle()
    val folder = uiState.folder
    // M63: misma sesión/coordinador que Inicio; el resolvedor usa el pedido oficial de la carpeta.
    val trailerPreview = rememberNotflixPreviewSession(
        resolver = remember(viewModel) {
            OfficialNotflixPreviewResolver(FolderNotflixPreviewRequestHost(viewModel))
        },
        // M67: el selector de listas también es un diálogo; el tráiler no corre debajo.
        dialog = posterOptionsState.target != null || posterOptionsState.listPickerActive,
        routeActive = true,
        validContext = { true },
        lockContext = true
    )
    // M67: mismas acciones y mismos escritores oficiales que Inicio. Mi lista y Visto van por el
    // controlador del menú largo de esta pantalla; Ocultar, por el almacén de títulos ocultos.
    DisposableEffect(viewModel) { onDispose { viewModel.posterOptions.stopCardStatus() } }
    val latestCardStatus = viewModel.posterOptions.cardStatus.collectAsStateWithLifecycle()
    val cardActions = remember(viewModel, posterOptionsState.librarySourceMode) {
        notflixCardActionHost(
            status = { item ->
                latestCardStatus.value[posterCardStatusKey(item.apiType, item.id)]?.toNotflixCardStatus()
                    ?: NotflixCardStatus()
            },
            manageLists = posterOptionsState.librarySourceMode != LibrarySourceMode.LOCAL,
            onExpanded = { item ->
                viewModel.onItemFocused(item, allowTrailerRetry = false)
                viewModel.refreshCardStatus(item)
            },
            onLibrary = viewModel::toggleCardLibrary,
            onWatched = viewModel::toggleCardWatched,
            onHide = viewModel::hideTitle
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> NotflixCategorySkeleton()
            folder == null -> NotflixCategoryMessage(
                title = stringResource(R.string.folder_detail_not_found)
            )
            else -> NotflixFolderCategoryPage(
                viewModel = viewModel,
                uiState = uiState,
                folder = folder,
                cardActions = cardActions,
                previewSession = trailerPreview,
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToPlay = onNavigateToPlay
            )
        }

        uiState.hiddenTitleUndo?.let { undo ->
            HiddenTitleUndoNotice(undo, viewModel::undoHideTitle,
                Modifier.align(Alignment.BottomCenter).padding(bottom = NuvioTheme.spacing.xl), autoFocus = false)
        }
    }

    PosterOptionsHost(
        state = posterOptionsState,
        controller = viewModel.posterOptions,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            onNavigateToDetail(id, type, addonBaseUrl)
        }
    )
}

@Composable
private fun NotflixFolderCategoryPage(
    viewModel: FolderDetailViewModel,
    uiState: FolderDetailUiState,
    folder: CollectionFolder,
    cardActions: NotflixCardActionsHost,
    previewSession: com.nuvio.tv.ui.screens.home.NotflixPreviewSession,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToPlay: ((String, String, String) -> Unit)?
) {
    val tabsData = remember(uiState.tabs, uiState.movieWatchedStatus, uiState.hiddenTitleKeys) {
        uiState.tabs.mapIndexed { index, tab ->
            notflixFolderTabData(index, tab, uiState.movieWatchedStatus, uiState.hiddenTitleKeys)
        }
    }
    val tabs = remember(tabsData) { tabsData.map { it.tab } }
    val selectedData = notflixFolderSelectedTab(tabsData, uiState.selectedTabIndex)
    val catalogItemsByKey = selectedData?.itemsByKey.orEmpty()
    // M63: el panel de la tarjeta abierta usa la meta enriquecida que la carpeta ya carga.
    val enriched by viewModel.enrichedPreviews.collectAsStateWithLifecycle()
    val itemsByKey = remember(catalogItemsByKey, enriched) {
        catalogItemsByKey.mapValues { (_, item) ->
            enriched[item.id]?.takeIf { it.apiType == item.apiType }
                ?.copy(poster = item.poster ?: enriched[item.id]?.poster) ?: item
        }
    }

    // Hasta que el usuario mueva el foco por la cuadrícula, la cabecera enseña el arte de la carpeta.
    val folderHeader = remember(folder) {
        NotflixHeaderItem(
            key = "folder:${folder.id}",
            title = folder.title,
            backdrop = folder.heroBackdropUrl ?: folder.coverImageUrl,
            poster = folder.coverImageUrl,
            logo = folder.titleLogoUrl
        )
    }
    val collectionLabel = uiState.collectionTitle
        .takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.notflix_category_collection, it) }

    NotflixCategoryPage(
        title = folder.title,
        collectionLabel = collectionLabel,
        tabs = tabs,
        selectedTabIndex = uiState.selectedTabIndex,
        saveScope = "folder-${folder.id}",
        onSelectTab = viewModel::selectTab,
        headerFor = { spec -> itemsByKey[spec.key]?.toNotflixCategoryHeader() },
        fallbackHeader = folderHeader,
        onPosterClick = { tabIndex, spec ->
            val data = tabsData.getOrNull(tabIndex)
            val item = data?.itemsByKey?.get(spec.key)
            if (data != null && item != null) {
                HeroBackdropState.update(item.backdropUrl)
                onNavigateToDetail(item.id, item.apiType, data.addonBaseUrl)
            }
        },
        onPosterLongClick = { tabIndex, spec ->
            val data = tabsData.getOrNull(tabIndex)
            val item = data?.itemsByKey?.get(spec.key)
            if (data != null && item != null) {
                viewModel.posterOptions.show(item, data.addonBaseUrl)
            }
        },
        onLoadMore = viewModel::loadMoreItems,
        onRetry = viewModel::retryTab,
        itemsByKey = itemsByKey,
        cardActions = cardActions,
        previewSession = previewSession,
        onCardPlay = onNavigateToPlay?.let { play ->
            { meta ->
                HeroBackdropState.update(meta.backdropUrl)
                play(meta.id, meta.apiType, selectedData?.addonBaseUrl.orEmpty())
            }
        },
        cardBase = { selectedData?.addonBaseUrl.orEmpty() },
        canPlay = onNavigateToPlay != null
    )
}

