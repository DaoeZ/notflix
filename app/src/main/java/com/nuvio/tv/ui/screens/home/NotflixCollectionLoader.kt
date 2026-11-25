package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.profile.isKidsAgeLimit
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.CollectionCatalogRequest
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.ui.appearance.NotflixKidsHomePreset
import com.nuvio.tv.ui.appearance.NotflixKidsHomeTexts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
        // M66: a KIDS profile gets its own four rows instead of the account's rows.
        // M68: from TMDB Discover, with the Cinemeta row of M66 as the fallback of each row.
        isKidsAgeLimit(limit) -> notflixCollectionSpecsFor(
            NotflixKidsHomePreset.createDiscover(notflixKidsHomeTexts()), addonsCache, emptySet(),
            fallbackSource = { NotflixKidsHomePreset.fallbackSource(it) })
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

/**
 * M68: a KIDS row comes from TMDB Discover. If TMDB fails, the same folder's Cinemeta row is used,
 * so the kids home never degrades into an empty row; the age gate is applied by the caller either way.
 */
internal fun HomeViewModel.loadNotflixTmdbRow(
    source: TmdbCollectionSource,
    fallback: CollectionCatalogRequest?
): Flow<NetworkResult<CatalogRow>> = flow {
    val resolver = tmdbCollectionSourceResolver
    if (resolver == null) {
        val replacement = fallback?.let { loadNotflixCollectionRequest(it) }
        if (replacement != null) emitAll(replacement)
        else emit(NetworkResult.Error(appContext.getString(R.string.tmdb_error_load_source)))
        return@flow
    }
    emitAll(
        notflixRowWithFallback(resolver.resolve(source, page = 1)) {
            fallback?.let { loadNotflixCollectionRequest(it) }
        }
    )
}

/** Uses the fallback flow once when the primary flow fails; otherwise passes every emission through. */
internal fun notflixRowWithFallback(
    primary: Flow<NetworkResult<CatalogRow>>,
    fallback: suspend () -> Flow<NetworkResult<CatalogRow>>?
): Flow<NetworkResult<CatalogRow>> = flow {
    var failure: NetworkResult.Error? = null
    primary.catch { error ->
        if (error is CancellationException) throw error
        emit(NetworkResult.Error(error.message.orEmpty()))
    }.takeWhile { result ->
        if (result is NetworkResult.Error) { failure = result; false } else true
    }.collect { emit(it) }
    failure?.let { error ->
        val replacement = fallback()
        if (replacement != null) emitAll(replacement) else emit(error)
    }
}

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
