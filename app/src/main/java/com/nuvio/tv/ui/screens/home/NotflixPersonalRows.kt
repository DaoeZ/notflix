package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.ActiveProfileAgeGate
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.ageLevelOf
import com.nuvio.tv.core.profile.displayRating
import com.nuvio.tv.core.profile.isAllowed
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.util.isUnreleased
import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.MoreLikeThisSourcePreference
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.data.repository.TraktRelatedService
import com.nuvio.tv.data.simkl.SimklAuthRepository
import com.nuvio.tv.data.simkl.SimklRelatedService
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.ui.screens.player.isPostPlayCandidateWatched
import com.nuvio.tv.ui.screens.player.resolvePostPlayContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

internal const val NOTFLIX_PERSONAL_ROW_LIMIT = 20
internal const val NOTFLIX_MY_LIST_KEY = "notflix-my-list"
internal const val NOTFLIX_BECAUSE_KEY = "notflix-because"

/** «Because you watched X»: the anchor title name and official related titles, already filtered. */
data class NotflixBecause(val anchorTitle: String, val anchorKey: String, val items: List<MetaPreview>)

/** Official library, newest first, as Home tiles. Each keeps its own addon so detail opens from the right source.
 *  [maxItems] allows the caller to fetch extra candidates before applying the age filter. */
internal fun notflixMyList(
    entries: List<LibraryEntry>,
    maxItems: Int = NOTFLIX_PERSONAL_ROW_LIMIT
): List<MetaPreview> = entries
    .sortedByDescending { it.listedAt }
    .distinctBy { it.type.lowercase() to it.id }
    .take(maxItems)
    .map { e ->
        MetaPreview(id = e.id, type = ContentType.fromString(e.type), rawType = e.type, name = e.name, poster = e.poster,
            posterShape = e.posterShape, background = e.background, logo = e.logo, description = e.description,
            releaseInfo = e.releaseInfo, imdbRating = e.imdbRating, genres = e.genres, imdbId = e.imdbId,
            landscapePoster = e.landscapePoster, rawPosterUrl = e.rawPosterUrl, sourceAddonBaseUrl = e.addonBaseUrl)
    }

/** Last watched movie or series of the profile; anything else is not a recommendation anchor. */
internal fun notflixAnchor(progress: List<WatchProgress>): WatchProgress? = progress
    .filter { it.contentId.isNotBlank() && resolvePostPlayContentType(it.contentType) != null }
    .maxByOrNull { it.lastWatched }

/** Same filters as the official post-play recommendations: not the anchor, not watched, not unreleased if hidden, unique. */
internal fun notflixBecauseCandidates(
    candidates: List<MetaPreview>, anchorIds: Set<String>, watchedMovieIds: Set<String>, watchedSeriesIds: Set<String>,
    hideUnreleased: Boolean, today: LocalDate, maxItems: Int = NOTFLIX_PERSONAL_ROW_LIMIT
): List<MetaPreview> = candidates.asSequence()
    .filterNot { it.id.trim().lowercase() in anchorIds }
    .filterNot { isPostPlayCandidateWatched(it, watchedMovieIds, watchedSeriesIds) }
    .filterNot { hideUnreleased && it.isUnreleased(today) }
    .distinctBy { it.apiType.trim().lowercase() to it.id.trim().lowercase() }
    .take(maxItems)
    .toList()

/** Rows inserted by Home: My list after Continue watching, Because after the first catalogue row. */
internal fun notflixPersonalRow(key: String, title: String, items: List<MetaPreview>): NotflixCatalogRow? =
    items.takeIf { it.isNotEmpty() }?.let {
        NotflixCatalogRow(key, key, title, NotflixRowPhase.READY, CatalogRow(addonId = "notflix", addonName = "Notflix",
            addonBaseUrl = "", catalogId = key, catalogName = title, type = it.first().type, items = it, hasMore = false))
    }

/**
 * Personal rows from official data only. Recommendation source follows the official post-play rule:
 * Simkl or Trakt when connected and chosen, otherwise TMDB «More like this» when enabled; nothing invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotflixPersonalRowsViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val metaRepository: MetaRepository,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val traktRelatedService: TraktRelatedService,
    private val traktAuthDataStore: TraktAuthDataStore,
    private val traktSettingsDataStore: TraktSettingsDataStore,
    private val simklRelatedService: SimklRelatedService,
    private val simklAuthRepository: SimklAuthRepository,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val watchedSeriesStateHolder: WatchedSeriesStateHolder,
    private val ageGate: ActiveProfileAgeGate,
    private val profileManager: com.nuvio.tv.core.profile.ProfileManager,
    private val hiddenTitlesRepository: com.nuvio.tv.domain.repository.HiddenTitlesRepository,
) : ViewModel() {
    private val hiddenKeys: kotlinx.coroutines.flow.Flow<Set<String>> = profileManager.activeProfileId
        .flatMapLatest { hiddenTitlesRepository.observe(it) }

    val myList: StateFlow<List<MetaPreview>> = combine(
        libraryRepository.libraryItems,
        ageGate.snapshot,
        hiddenKeys
    ) { entries, snapshot, hidden -> Triple(entries, snapshot, hidden) }
        .mapLatest { (entries, snapshot, hidden) ->
            val previews = notflixMyList(entries, maxItems = NOTFLIX_PERSONAL_ROW_LIMIT * 4)
            val visible = if (hidden.isEmpty()) previews else previews.filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }
            val allowed = if (snapshot.limit == null) visible else {
                ageGate.filter(visible).map { item ->
                    item.copy(ageRating = ageGate.knownRating(item)?.displayRating() ?: item.ageRating)
                }
            }
            allowed.take(NOTFLIX_PERSONAL_ROW_LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val becauseYouWatched: StateFlow<NotflixBecause?> = combine(
        watchProgressRepository.allProgress,
        ageGate.limit,
        hiddenKeys
    ) { progress, limit, hidden -> Triple(progress, limit, hidden) }
        .map { (progress, limit, hidden) -> notflixAnchor(progress)?.let { Triple(it, limit, hidden) } }
        .distinctUntilChanged { a, b ->
            a?.first?.contentId == b?.first?.contentId &&
                a?.first?.contentType == b?.first?.contentType &&
                a?.second == b?.second &&
                a?.third == b?.third
        }
        .mapLatest { anchorAndLimit ->
            anchorAndLimit?.let { (anchor, limit, hidden) ->
                runCatching { load(anchor, limit, hidden) }.getOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun load(anchor: WatchProgress, limit: AgeLevel?, hidden: Set<String>): NotflixBecause? {
        val type = anchor.contentType
        if (hiddenTitleKey(type, anchor.contentId) in hidden) return null
        val meta: Meta = metaRepository.getCachedMeta(type, anchor.contentId) ?: withTimeoutOrNull(8_000L) {
            (metaRepository.getMetaFromAllAddons(type = type, id = anchor.contentId).first { it !is NetworkResult.Loading }
                as? NetworkResult.Success)?.data
        } ?: return null
        if (limit != null) {
            val anchorRating = ageLevelOf(meta.ageRating) ?: ageGate.ratingFor(type, anchor.contentId, meta.ageRating)
            if (!isAllowed(limit, anchorRating)) return null
        }
        val contentType = resolvePostPlayContentType(type, meta.type) ?: return null
        val candidates = withTimeoutOrNull(10_000L) {
            val source = traktSettingsDataStore.moreLikeThisSource.first()
            when {
                source == MoreLikeThisSourcePreference.SIMKL && simklAuthRepository.state.value.isAuthenticated ->
                    simklRelatedService.getRelated(meta = meta, fallbackItemId = anchor.contentId, fallbackItemType = type)
                source == MoreLikeThisSourcePreference.TRAKT && traktAuthDataStore.isAuthenticated.first() ->
                    traktRelatedService.getRelated(meta = meta, fallbackItemId = anchor.contentId, fallbackItemType = type)
                else -> {
                    val settings = tmdbSettingsDataStore.settings.first()
                    if (!settings.enabled || !settings.useMoreLikeThis) return@withTimeoutOrNull emptyList()
                    val lookup = contentType.toApiString(type)
                    val tmdbId = tmdbService.ensureTmdbId(meta.id, lookup, fallbackImdbId = meta.imdbId)
                        ?: tmdbService.ensureTmdbId(anchor.contentId, lookup, fallbackImdbId = meta.imdbId)
                        ?: return@withTimeoutOrNull emptyList()
                    tmdbMetadataService.fetchMoreLikeThis(tmdbId = tmdbId, contentType = contentType, language = settings.language)
                }
            }
        }.orEmpty()
        val watched = combine(watchProgressRepository.observeWatchedMovieIds(), watchedSeriesStateHolder.fullyWatchedSeriesIds) { m, s -> m to s }.first()
        val base = notflixBecauseCandidates(candidates, setOf(meta.id.trim().lowercase(), anchor.contentId.trim().lowercase()),
            watched.first, watched.second, layoutPreferenceDataStore.hideUnreleasedContent.first(), LocalDate.now(),
            maxItems = NOTFLIX_PERSONAL_ROW_LIMIT * 3)
        val visible = if (hidden.isEmpty()) base else base.filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }
        val items = if (limit == null) visible.take(NOTFLIX_PERSONAL_ROW_LIMIT) else {
            ageGate.filter(visible).map { item ->
                item.copy(ageRating = ageGate.knownRating(item)?.displayRating() ?: item.ageRating)
            }.take(NOTFLIX_PERSONAL_ROW_LIMIT)
        }
        return items.takeIf { it.isNotEmpty() }?.let { NotflixBecause(anchor.name.ifBlank { meta.name }, "${type}:${anchor.contentId}", it) }
    }
}
