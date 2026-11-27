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
    val likedAnchorKeys: Set<String>,
    val dislikedEntries: List<Pair<String, String>>,
    val watchedIds: Set<String>,
    val limit: AgeLevel?,
)

private data class ForYouLoadedData(
    val positive: List<Pair<NotflixBecause, Int>>,
    val dislikedRelatedKeys: Set<Pair<String, String>>,
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
        traktSettingsDataStore.moreLikeThisSource.onStart { emit(MoreLikeThisSourcePreference.TMDB) },
        com.nuvio.tv.data.local.NotflixTitleFeedbackStore.revision
    ) { progress, limit, tmdbKey, recSource, _ ->
        val likedEntries = com.nuvio.tv.data.local.NotflixTitleFeedbackStore.likedEntries(profile)
        val dislikedEntries = com.nuvio.tv.data.local.NotflixTitleFeedbackStore.dislikedEntries(profile)
        val dislikedIds = dislikedEntries.mapTo(mutableSetOf()) { it.second }
        val likedAnchors = likedEntries.take(4).map { (type, id) ->
            WatchProgress(
                contentId = id,
                contentType = type,
                name = "",
                poster = null,
                backdrop = null,
                logo = null,
                videoId = id,
                season = null,
                episode = null,
                episodeTitle = null,
                position = 0L,
                duration = 0L,
                lastWatched = Long.MAX_VALUE
            )
        }
        val watchedAnchors = notflixForYouAnchors(progress)
            .filterNot { it.contentId.trim().lowercase() in dislikedIds }
        val mergedAnchors = (likedAnchors + watchedAnchors)
            .distinctBy { it.contentId.trim().lowercase() }
            .take(8)
        val likedKeySet = likedAnchors.mapTo(mutableSetOf()) { it.contentId.trim().lowercase() }
        Triple(
            ForYouHistory(
                anchors = mergedAnchors,
                likedAnchorKeys = likedKeySet,
                dislikedEntries = dislikedEntries.take(3),
                watchedIds = progress.mapTo(mutableSetOf()) { it.contentId.trim().lowercase() },
                limit = limit
            ),
            tmdbKey,
            recSource
        )
    }
        .distinctUntilChanged { a, b ->
            a.first.anchors.map { it.contentId to it.contentType } == b.first.anchors.map { it.contentId to it.contentType } &&
                a.first.likedAnchorKeys == b.first.likedAnchorKeys &&
                a.first.dislikedEntries == b.first.dislikedEntries &&
                a.first.watchedIds == b.first.watchedIds && a.first.limit == b.first.limit &&
                a.second == b.second && a.third == b.third
        }
        .transformLatest { (history, _, _) ->
            emit(ForYouLoadedData(emptyList(), emptySet()))
            val limit = ageGate.currentLimitFromStore()
            val loaded = mutableListOf<Pair<NotflixBecause, Int>>()
            val dislikedRelated = mutableSetOf<Pair<String, String>>()
            if (history.dislikedEntries.isNotEmpty()) {
                coroutineScope {
                    history.dislikedEntries.map { (type, id) ->
                        async {
                            try {
                                withTimeoutOrNull(8_000L) { loadRelatedKeys(type, id) }.orEmpty()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptySet()
                            }
                        }
                    }.awaitAll().forEach { dislikedRelated += it }
                }
            }
            for (batch in history.anchors.chunked(2)) {
                if (profileManager.activeProfileId.value != profile) return@transformLatest
                val hidden = hiddenKeys.first()
                loaded += coroutineScope {
                    batch.filterNot { hiddenTitleKey(it.contentType, it.contentId) in hidden }
                        .map { anchor ->
                            async {
                                try {
                                    val result = withTimeoutOrNull(14_000L) { load(anchor, limit, history.watchedIds) }
                                    val weight = if (anchor.contentId.trim().lowercase() in history.likedAnchorKeys) 2 else 1
                                    result?.let { it to weight }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                }
                if (profileManager.activeProfileId.value == profile) {
                    emit(ForYouLoadedData(loaded.toList(), dislikedRelated.toSet()))
                }
            }
        }
        .combine(combine(hiddenKeys, com.nuvio.tv.data.local.NotflixTitleFeedbackStore.revision) { h, r -> h to r }) { data, (hidden, _) ->
            val dislikedIds = com.nuvio.tv.data.local.NotflixTitleFeedbackStore.dislikedIds(profile)
            val likedGenres = com.nuvio.tv.data.local.NotflixTitleFeedbackStore.likedGenres(profile)
            val dislikedGenres = com.nuvio.tv.data.local.NotflixTitleFeedbackStore.dislikedGenres(profile)
            val visible = data.positive.mapNotNull { (src, weight) ->
                src.withoutHidden(hidden)?.let { it to weight }
            }
            val items = mergeWeightedForYou(
                sources = visible.map { (src, weight) -> src.items to weight },
                limit = NOTFLIX_PERSONAL_ROW_LIMIT,
                dislikedIds = dislikedIds,
                dislikedRelatedKeys = data.dislikedRelatedKeys,
                likedGenres = likedGenres,
                dislikedGenres = dislikedGenres
            )
            visible.firstOrNull()?.first?.copy(items = items, profileId = profile)?.takeIf { items.isNotEmpty() }
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun loadRelatedKeys(type: String, id: String): Set<Pair<String, String>> {
        val meta: Meta = metaRepository.getCachedMeta(type, id) ?: withTimeoutOrNull(4_000L) {
            (metaRepository.getMetaFromAllAddons(type = type, id = id).first { it !is NetworkResult.Loading }
                as? NetworkResult.Success)?.data
        } ?: return emptySet()
        val contentType = resolvePostPlayContentType(type, meta.type) ?: return emptySet()
        val settings = tmdbSettingsDataStore.settings.first()
        if (!settings.enabled || !settings.useMoreLikeThis) return emptySet()
        val lookup = contentType.toApiString(type)
        val tmdbId = tmdbService.ensureTmdbId(meta.id, lookup, fallbackImdbId = meta.imdbId)
            ?: tmdbService.ensureTmdbId(id, lookup, fallbackImdbId = meta.imdbId)
            ?: return emptySet()
        return tmdbMetadataService.fetchMoreLikeThis(tmdbId = tmdbId, contentType = contentType, language = settings.language)
            .mapTo(mutableSetOf()) { it.apiType.trim().lowercase() to it.id.trim().lowercase() }
    }

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
internal fun mergeBalancedForYou(sources: List<List<MetaPreview>>, limit: Int): List<MetaPreview> =
    mergeWeightedForYou(sources = sources.map { it to 1 }, limit = limit)

/**
 * Merges recommendations across multiple weighted anchors:
 * - Disliked titles are excluded completely.
 * - "Like" anchors carry double weight (+4 vs +2) and liked genres add a small boost.
 * - Items similar to "Not interested" titles (related items or shared genres) are penalized and pushed down.
 */
internal fun mergeWeightedForYou(
    sources: List<Pair<List<MetaPreview>, Int>>,
    limit: Int,
    dislikedIds: Set<String> = emptySet(),
    dislikedRelatedKeys: Set<Pair<String, String>> = emptySet(),
    likedGenres: Map<String, Int> = emptyMap(),
    dislikedGenres: Map<String, Int> = emptyMap()
): List<MetaPreview> {
    if (sources.isEmpty() || limit <= 0) return emptyList()
    val deduped = sources.map { (list, weight) ->
        list.asSequence()
            .filterNot { it.id.trim().lowercase() in dislikedIds }
            .distinctBy { it.apiType.lowercase() to it.id.trim().lowercase() }
            .toList() to weight
    }
    val scores = mutableMapOf<Pair<String, String>, Int>()
    val firstByKey = linkedMapOf<Pair<String, String>, MetaPreview>()
    val firstOrder = mutableMapOf<Pair<String, String>, Int>()
    var orderCounter = 0
    for ((list, weight) in deduped) {
        for (item in list) {
            val key = item.apiType.lowercase() to item.id.trim().lowercase()
            scores[key] = (scores[key] ?: 0) + (weight * 2)
            if (key !in firstByKey) {
                firstByKey[key] = item
                firstOrder[key] = orderCounter++
            }
        }
    }
    for ((key, item) in firstByKey) {
        var adjustment = 0
        if (key in dislikedRelatedKeys) adjustment -= 4
        if (likedGenres.isNotEmpty() || dislikedGenres.isNotEmpty()) {
            var likeHits = 0
            var dislikeHits = 0
            item.genres.forEach { raw ->
                val g = raw.trim().lowercase()
                if (g in likedGenres) likeHits++
                // One "not interested" must not sink a whole genre (most series are Drama): it takes two, and more dislikes than likes.
        if ((dislikedGenres[g] ?: 0) >= 2 && (dislikedGenres[g] ?: 0) > (likedGenres[g] ?: 0)) dislikeHits++
            }
            adjustment += minOf(likeHits, 2)
            adjustment -= minOf(dislikeHits * 2, 4)
        }
        if (adjustment != 0) scores[key] = (scores[key] ?: 0) + adjustment
    }
    val result = mutableListOf<MetaPreview>()
    val emitted = mutableSetOf<Pair<String, String>>()
    // High-signal hits first (liked anchors or multi-anchor consensus)
    scores.entries
        .filter { it.value >= 4 }
        .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { firstOrder[it.key] ?: Int.MAX_VALUE })
        .forEach { (key, _) ->
            if (result.size >= limit) return result
            val item = firstByKey[key] ?: return@forEach
            if (emitted.add(key)) result += item
        }
    // Fair round-robin for neutral/positive remaining items (non-penalized first)
    val perAnchorLimit = if (deduped.size >= 3) 3 else Int.MAX_VALUE
    val perAnchorCount = IntArray(deduped.size)
    val maxLen = deduped.maxOfOrNull { it.first.size } ?: 0
    for (idx in 0 until maxLen) {
        for (srcIdx in deduped.indices) {
            if (result.size >= limit) return result
            if (perAnchorCount[srcIdx] >= perAnchorLimit) continue
            val item = deduped[srcIdx].first.getOrNull(idx) ?: continue
            val key = item.apiType.lowercase() to item.id.trim().lowercase()
            if ((scores[key] ?: 0) <= 0) continue
            if (emitted.add(key)) {
                result += item
                perAnchorCount[srcIdx]++
            }
        }
    }
    // Downweighted items only fill remaining slots at the very end, sorted by score descending
    scores.entries
        .filter { it.key !in emitted }
        .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { firstOrder[it.key] ?: Int.MAX_VALUE })
        .forEach { (key, _) ->
            if (result.size >= limit) return result
            val item = firstByKey[key] ?: return@forEach
            if (emitted.add(key)) result += item
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
