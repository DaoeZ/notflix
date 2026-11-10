package com.nuvio.tv.ui.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.CollectionFolder
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
import com.nuvio.tv.ui.screens.home.HeroBackdropState
import com.nuvio.tv.ui.screens.home.homeItemStatusKey

/** Prefijo de los elementos de relleno que el ViewModel usa mientras carga un catálogo. */
private const val NOTFLIX_FOLDER_PLACEHOLDER_PREFIX = "__placeholder"

/** Una pestaña ya convertida: modelo de la página común y lo necesario para navegar. */
internal data class NotflixFolderTabData(
    val tab: NotflixCategoryTab,
    val itemsByKey: Map<String, MetaPreview>,
    val addonBaseUrl: String
)

/**
 * M49: convierte una pestaña de la carpeta en el modelo de la página común. No pide nada: usa el
 * `catalogRow` que el ViewModel ya cargó, filtra los rellenos de carga y aplica la regla de vistos
 * de M39 con el mismo `movieWatchedStatus` que la pantalla oficial.
 */
internal fun notflixFolderTabData(
    index: Int,
    tab: FolderTab,
    watchedStatus: Map<String, Boolean>
): NotflixFolderTabData {
    val rowItems = tab.catalogRow?.items.orEmpty()
        .filterNot { it.id.startsWith(NOTFLIX_FOLDER_PLACEHOLDER_PREFIX) }
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
    onNavigateToDetail: (String, String, String) -> Unit
) {
    val posterOptionsState by viewModel.posterOptions.state.collectAsStateWithLifecycle()
    val folder = uiState.folder

    when {
        uiState.isLoading -> NotflixCategorySkeleton()
        folder == null -> NotflixCategoryMessage(
            title = stringResource(R.string.folder_detail_not_found)
        )
        else -> NotflixFolderCategoryPage(
            viewModel = viewModel,
            uiState = uiState,
            folder = folder,
            onNavigateToDetail = onNavigateToDetail
        )
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
    onNavigateToDetail: (String, String, String) -> Unit
) {
    val tabsData = remember(uiState.tabs, uiState.movieWatchedStatus) {
        uiState.tabs.mapIndexed { index, tab ->
            notflixFolderTabData(index, tab, uiState.movieWatchedStatus)
        }
    }
    val tabs = remember(tabsData) { tabsData.map { it.tab } }
    val itemsByKey = remember(tabsData) {
        tabsData.fold(mutableMapOf<String, MetaPreview>()) { acc, data ->
            data.itemsByKey.forEach { (key, item) -> if (!acc.containsKey(key)) acc[key] = item }
            acc
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
        onRetry = viewModel::retryTab
    )
}
