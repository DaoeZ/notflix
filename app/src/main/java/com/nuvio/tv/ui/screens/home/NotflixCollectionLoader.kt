package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.profile.isKidsAgeLimit
import com.nuvio.tv.domain.model.CollectionCatalogRequest
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.ui.appearance.NotflixKidsHomePreset
import com.nuvio.tv.ui.appearance.NotflixKidsHomeTexts
import kotlinx.coroutines.flow.update

/** The first returning composition must not treat an inactive Home snapshot as retirement. */
internal fun notflixHomeEntryState(state: HomeUiState): HomeUiState =
    if (!state.notflixPresentationActive) state.copy(notflixCatalog = state.notflixCatalog.copy(configured = false)) else state

internal fun HomeViewModel.configureNotflixCollectionRows() {
    val limit = ageGate.currentLimit()
    val scope = if (_uiState.value.notflixPresentationActive && notflixCollectionsProfile == profileManager.activeProfileId.value)
        NotflixCollectionScope(
            profileManager.activeProfileId.value,
            catalogLoadGeneration,
            notflixCollectionVisit,
            _uiState.value.hideUnreleasedContent,
            limit,
            _uiState.value.hiddenTitleKeys
        ) else null
    val definitions = when {
        scope == null -> emptyList()
        // M66: a KIDS profile gets its own four Cinemeta rows instead of the account's rows.
        isKidsAgeLimit(limit) -> notflixCollectionSpecsFor(
            NotflixKidsHomePreset.create(notflixKidsHomeTexts()), addonsCache, emptySet())
            .takeIf { tmdbAvailability.isEnabled() }.orEmpty()
        else -> notflixCollectionSpecs(collectionsCache, addonsCache, disabledHomeCatalogKeys)
    }
    notflixCollectionRows.configure(scope, definitions)
}

/** App-language row names, resolved like the rest of the Notflix rows already are. */
internal fun HomeViewModel.notflixKidsHomeTexts(): NotflixKidsHomeTexts =
    com.nuvio.tv.ui.appearance.notflixKidsHomeTexts(appContext)

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

/** Key changes bypass the catalog refresh TTL on return from the KIDS integration entry. */
internal fun HomeViewModel.refreshNotflixKidsTmdbAvailability() {
    if (_uiState.value.notflixPresentationActive && isKidsAgeLimit(ageGate.currentLimit())) {
        val available = tmdbAvailability.isEnabled()
        if (_uiState.value.ageTmdbKeyAvailable != available) {
            _uiState.update { it.copy(ageTmdbKeyAvailable = available) }
            notflixCollectionRows.clear()
            scheduleUpdateCatalogRows()
        }
    }
}
