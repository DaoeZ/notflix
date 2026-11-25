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
import kotlinx.coroutines.flow.onStart
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.transformLatest

internal const val NOTFLIX_PERSONAL_ROW_LIMIT = 20
internal const val NOTFLIX_MY_LIST_KEY = "notflix-my-list"
internal const val NOTFLIX_BECAUSE_KEY = "notflix-because"
internal const val NOTFLIX_TOP10_MOVIES_KEY = "notflix-top10-movies"
internal const val NOTFLIX_TOP10_SERIES_KEY = "notflix-top10-series"
internal const val NOTFLIX_TOP10_KIDS_KEY = "notflix-top10-kids"

/** «Because you watched X»: the anchor title name and official related titles, already filtered. */
data class NotflixBecause(val anchorTitle: String, val anchorKey: String, val items: List<MetaPreview>, val profileId: Int? = null)

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

internal fun notflixForYouAnchors(progress: List<WatchProgress>): List<WatchProgress> {
    val movies = progress.asSequence()
        .filter { it.contentId.isNotBlank() && resolvePostPlayContentType(it.contentType) == ContentType.MOVIE }
        .sortedByDescending { it.lastWatched }
        .distinctBy { it.contentId.trim().lowercase() }
        .toList()
    if (movies.size <= 6) return movies
    return (0..5).map { movies[it * movies.lastIndex / 5] }
}

private data class ForYouHistory(
    val anchors: List<WatchProgress>,
    val watchedIds: Set<String>,
    val limit: AgeLevel?,
)

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
    val activeProfileId = profileManager.activeProfileId
    private val hiddenKeys: kotlinx.coroutines.flow.Flow<Set<String>> = profileManager.activeProfileId
        .flatMapLatest { hiddenTitlesRepository.observe(it) }

    private val top10Inputs = combine(
        tmdbSettingsDataStore.settings,
        ageGate.limit,
        hiddenKeys,
        NotflixCountryPreference.selectedCountry
    ) { settings, limit, hidden, countryOverride -> Pair(Triple(settings, limit, hidden), countryOverride) }

    val top10Movies: StateFlow<List<MetaPreview>> = top10Inputs
        .transformLatest { (triple, countryOverride) ->
            val (settings, limit, hidden) = triple
            if (com.nuvio.tv.core.profile.isKidsAgeLimit(limit)) {
                emit(emptyList())
                return@transformLatest
            }
            emit(emptyList())
            val locale = java.util.Locale.getDefault()
            val country = NotflixCountryPreference.resolveCountryCode(locale, countryOverride)
            val lang = settings.language.takeIf { it.isNotBlank() } ?: locale.toLanguageTag()
            val fetched = tmdbService.fetchTop10ByCountry("movie", country, lang, kidsOnly = false)
            val visible = if (hidden.isEmpty()) fetched else fetched.filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }
            val allowed = if (limit == null) visible else ageGate.filterForLimit(visible, limit)
            emit(allowed.take(10))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val top10Series: StateFlow<List<MetaPreview>> = top10Inputs
        .transformLatest { (triple, countryOverride) ->
            val (settings, limit, hidden) = triple
            if (com.nuvio.tv.core.profile.isKidsAgeLimit(limit)) {
                emit(emptyList())
                return@transformLatest
            }
            emit(emptyList())
            val locale = java.util.Locale.getDefault()
            val country = NotflixCountryPreference.resolveCountryCode(locale, countryOverride)
            val lang = settings.language.takeIf { it.isNotBlank() } ?: locale.toLanguageTag()
            val fetched = tmdbService.fetchTop10ByCountry("tv", country, lang, kidsOnly = false)
            val visible = if (hidden.isEmpty()) fetched else fetched.filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }
            val allowed = if (limit == null) visible else ageGate.filterForLimit(visible, limit)
            emit(allowed.take(10))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val top10Kids: StateFlow<List<MetaPreview>> = top10Inputs
        .transformLatest { (triple, countryOverride) ->
            val (settings, limit, hidden) = triple
            if (!com.nuvio.tv.core.profile.isKidsAgeLimit(limit)) {
                emit(emptyList())
                return@transformLatest
            }
            emit(emptyList())
            val locale = java.util.Locale.getDefault()
            val country = NotflixCountryPreference.resolveCountryCode(locale, countryOverride)
            val lang = settings.language.takeIf { it.isNotBlank() } ?: locale.toLanguageTag()
            val fetched = tmdbService.fetchTop10ByCountry("movie", country, lang, kidsOnly = true)
            val visible = if (hidden.isEmpty()) fetched else fetched.filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }
            emit(visible.take(10))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myList: StateFlow<List<MetaPreview>> = combine(
        libraryRepository.libraryItems,
        ageGate.snapshot,
        hiddenKeys,
        tmdbSettingsDataStore.settings
    ) { entries, snapshot, hidden, settings -> Pair(Triple(entries, snapshot, hidden), settings) }
        .mapLatest { (triple, settings) ->
            val (entries, snapshot, hidden) = triple
            val previews = notflixMyList(entries, maxItems = NOTFLIX_PERSONAL_ROW_LIMIT * 4)
            val visible = if (hidden.isEmpty()) previews else previews.filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }
            val allowed = if (snapshot.limit == null) visible else {
                ageGate.filter(visible).map { item ->
                    item.copy(ageRating = ageGate.knownRating(item)?.displayRating() ?: item.ageRating)
                }
            }
            val page = allowed.take(NOTFLIX_PERSONAL_ROW_LIMIT)
            val lang = settings.language.takeIf { it.isNotBlank() } ?: java.util.Locale.getDefault().toLanguageTag()
            runCatching { tmdbService.localizeMyListPreviews(page, lang) }.getOrDefault(page)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val becauseYouWatched: StateFlow<NotflixBecause?> = profileManager.activeProfileId.flatMapLatest { profile -> combine(
        watchProgressRepository.allProgress,
        ageGate.limit,
        tmdbSettingsDataStore.settings.map { Triple(it.enabled, it.useMoreLikeThis, it.language) }
            .onStart {
                val cur = runCatching { tmdbSettingsDataStore.settings.value }.getOrNull()
                emit(if (cur != null) Triple(cur.enabled, cur.useMoreLikeThis, cur.language) else Triple(true, true, ""))
            },
        traktSettingsDataStore.moreLikeThisSource.onStart { emit(MoreLikeThisSourcePreference.TMDB) }
    ) { progress, limit, tmdbKey, recSource ->
        Triple(
            ForYouHistory(notflixForYouAnchors(progress), progress.mapTo(mutableSetOf()) { it.contentId.trim().lowercase() }, limit),
            tmdbKey,
            recSource
        )
    }
        .distinctUntilChanged { a, b ->
            a.first.anchors.map { it.contentId to it.contentType } == b.first.anchors.map { it.contentId to it.contentType } &&
                a.first.watchedIds == b.first.watchedIds && a.first.limit == b.first.limit &&
                a.second == b.second && a.third == b.third
        }
        .transformLatest { (history, _, _) ->
            emit(emptyList<NotflixBecause>())
            val limit = ageGate.currentLimitFromStore()
            val loaded = mutableListOf<NotflixBecause>()
            for (batch in history.anchors.chunked(2)) {
                if (profileManager.activeProfileId.value != profile) return@transformLatest
                val hidden = hiddenKeys.first()
                loaded += coroutineScope {
                    batch.filterNot { hiddenTitleKey(it.contentType, it.contentId) in hidden }
                        .map { anchor ->
                            async {
                                try {
                                    withTimeoutOrNull(14_000L) { load(anchor, limit, history.watchedIds) }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                }
                if (profileManager.activeProfileId.value == profile) emit(loaded.toList())
            }
        }
        .combine(hiddenKeys) { sources, hidden ->
            val visible = sources.mapNotNull { it.withoutHidden(hidden) }
            val items = mergeBalancedForYou(visible.map { it.items }, NOTFLIX_PERSONAL_ROW_LIMIT)
            visible.firstOrNull()?.copy(items = items, profileId = profile)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun load(anchor: WatchProgress, limit: AgeLevel?, historyIds: Set<String>): NotflixBecause? {
        val type = anchor.contentType
        val meta: Meta = metaRepository.getCachedMeta(type, anchor.contentId) ?: withTimeoutOrNull(5_000L) {
            (metaRepository.getMetaFromAllAddons(type = type, id = anchor.contentId).first { it !is NetworkResult.Loading }
                as? NetworkResult.Success)?.data
        } ?: return null
        if (limit != null) {
            val anchorRating = ageLevelOf(meta.ageRating) ?: ageGate.ratingFor(type, anchor.contentId, meta.ageRating)
            if (!isAllowed(limit, anchorRating)) return null
        }
        val contentType = resolvePostPlayContentType(type, meta.type) ?: return null
        val candidates = withTimeoutOrNull(7_000L) {
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
        val base = notflixBecauseCandidates(candidates, historyIds + meta.id.trim().lowercase(),
            watched.first, watched.second, layoutPreferenceDataStore.hideUnreleasedContent.first(), LocalDate.now(),
            maxItems = NOTFLIX_PERSONAL_ROW_LIMIT * 3)
        // Keep a spare pool so hiding titles later still leaves a full row.
        val items = if (limit == null) base else {
            ageGate.filterForLimit(base, limit).map { item ->
                item.copy(ageRating = ageGate.knownRating(item)?.displayRating() ?: item.ageRating)
            }
        }
        return items.takeIf { it.isNotEmpty() }?.let {
            NotflixBecause(anchor.name.ifBlank { meta.name }, hiddenTitleKey(type, anchor.contentId), it)
        }
    }
}

/** Drops hidden titles from a loaded row; a hidden anchor drops the whole row. */
internal fun NotflixBecause.withoutHidden(hidden: Set<String>): NotflixBecause? {
    if (anchorKey in hidden) return null
    val visible = items.asSequence().filterNot { hiddenTitleKey(it.apiType, it.id) in hidden }.take(NOTFLIX_PERSONAL_ROW_LIMIT).toList()
    return if (visible.isEmpty()) null else copy(items = visible)
}

/**
 * Merges recommendations across multiple anchors: consensus hits (>= 2 anchors) first,
 * followed by fair round-robin interleaving capped at 3 single-anchor items per source
 * so no single recently watched movie floods "For You".
 */
internal fun mergeBalancedForYou(sources: List<List<MetaPreview>>, limit: Int): List<MetaPreview> {
    if (sources.isEmpty()) return emptyList()
    val deduped = sources.map { list -> list.distinctBy { it.apiType.lowercase() to it.id.trim().lowercase() } }
    val counts = mutableMapOf<Pair<String, String>, Int>()
    val firstByKey = linkedMapOf<Pair<String, String>, MetaPreview>()
    for (list in deduped) {
        for (item in list) {
            val key = item.apiType.lowercase() to item.id.trim().lowercase()
            counts[key] = (counts[key] ?: 0) + 1
            if (key !in firstByKey) firstByKey[key] = item
        }
    }
    val result = mutableListOf<MetaPreview>()
    val emitted = mutableSetOf<Pair<String, String>>()
    counts.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.forEach { (key, _) ->
        if (result.size >= limit) return result
        val item = firstByKey[key] ?: return@forEach
        if (emitted.add(key)) result += item
    }
    val perAnchorLimit = if (deduped.size >= 3) 3 else Int.MAX_VALUE
    val perAnchorCount = IntArray(deduped.size)
    val maxLen = deduped.maxOfOrNull { it.size } ?: 0
    for (idx in 0 until maxLen) {
        for (srcIdx in deduped.indices) {
            if (result.size >= limit) return result
            if (perAnchorCount[srcIdx] >= perAnchorLimit) continue
            val item = deduped[srcIdx].getOrNull(idx) ?: continue
            val key = item.apiType.lowercase() to item.id.trim().lowercase()
            if (emitted.add(key)) {
                result += item
                perAnchorCount[srcIdx]++
            }
        }
    }
    return result
}

/**
 * Resolves the 2-letter ISO country code from the OS system configuration first,
 * because selecting an in-app language (e.g. "es") strips the region from [java.util.Locale.getDefault].
 */
internal fun notflixSystemCountryCode(fallbackLocale: java.util.Locale = java.util.Locale.getDefault()): String {
    val systemCountry = runCatching {
        val locales = android.content.res.Resources.getSystem()?.configuration?.locales ?: return@runCatching null
        (0 until locales.size()).map { locales[it].country }.firstOrNull { it.length == 2 }
    }.getOrNull()
    return (systemCountry ?: fallbackLocale.country).trim().uppercase().takeIf { it.length == 2 } ?: "US"
}
