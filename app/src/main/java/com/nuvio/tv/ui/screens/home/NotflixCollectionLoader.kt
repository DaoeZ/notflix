package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.domain.model.CollectionCatalogRequest
import com.nuvio.tv.domain.model.enabledAddons

/** The first returning composition must not treat an inactive Home snapshot as retirement. */
internal fun notflixHomeEntryState(state: HomeUiState): HomeUiState =
    if (!state.notflixPresentationActive) state.copy(notflixCatalog = state.notflixCatalog.copy(configured = false)) else state

internal fun HomeViewModel.configureNotflixCollectionRows() {
    val scope = if (_uiState.value.notflixPresentationActive && notflixCollectionsProfile == profileManager.activeProfileId.value)
        NotflixCollectionScope(
            profileManager.activeProfileId.value,
            catalogLoadGeneration,
            notflixCollectionVisit,
            _uiState.value.hideUnreleasedContent,
            ageGate.currentLimit(),
            _uiState.value.hiddenTitleKeys
        ) else null
    notflixCollectionRows.configure(scope, if (scope != null) notflixCollectionSpecs(collectionsCache, addonsCache, disabledHomeCatalogKeys) else emptyList())
}

internal fun HomeViewModel.collectionRequestStillCurrent(scope: NotflixCollectionScope): Boolean =
    _uiState.value.notflixPresentationActive && profileManager.activeProfileId.value == scope.profile &&
        catalogLoadGeneration == scope.generation && notflixCollectionVisit == scope.visit &&
        _uiState.value.hideUnreleasedContent == scope.hideUnreleased && notflixCollectionsProfile == scope.profile &&
        ageGate.currentLimit() == scope.ageLimit && _uiState.value.hiddenTitleKeys == scope.hiddenKeys

internal fun HomeViewModel.loadNotflixCollectionRequest(request: CollectionCatalogRequest) =
    catalogRepository.getCatalog(
        addonBaseUrl = request.addon.baseUrl, addonId = request.addon.id, addonName = request.addon.displayName,
        catalogId = request.catalogId, catalogName = request.catalog?.name ?: request.catalogId, type = request.type,
        skip = 0, skipStep = request.skipStep, extraArgs = request.extraArgs, supportsSkip = request.supportsSkip,
        posterScreen = CustomPosterScreen.COLLECTIONS
    )
