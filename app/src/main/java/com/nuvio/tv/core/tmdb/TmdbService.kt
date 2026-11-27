package com.nuvio.tv.core.tmdb

import android.util.Log
import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbRecommendationResult
import com.nuvio.tv.data.remote.api.TmdbImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TmdbService"
private val IMDB_ID_REGEX = Regex("tt[0-9]+")
private const val MAX_ID_CACHE_ENTRIES = 1000
private const val MAX_TV_POSTER_CACHE_ENTRIES = 500

/**
 * Service to handle TMDB ID conversions and lookups.
 * Provides caching to avoid redundant API calls.
 */
@Singleton
class TmdbService @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val integrationConfig: NotflixIntegrationConfig
) {
    private val searchPosterCache = android.util.LruCache<String, SearchArtwork>(300)
    private val localizedPosterCache = android.util.LruCache<String, String>(300)
    private val posterRequests = Semaphore(3)

    internal suspend fun searchTitles(query: String, language: String): List<TmdbRecommendationResult> {
        if (!tmdbEnabled()) return emptyList()
        return tmdbApi.searchTitles(tmdbKey(), query, language).body()?.results.orEmpty()
    }

    internal data class SearchArtwork(val title: String?, val poster: String?, val rating: Float? = null)

    internal suspend fun searchArtwork(id: String, type: String, language: String): SearchArtwork? {
        val mediaType = normalizeMediaType(type)
        if (mediaType !in setOf("movie", "tv", "series") || !tmdbEnabled()) return null
        val key = "$mediaType:$id:$language"
        searchPosterCache.get(key)?.let { return it }
        val result = if (id.matches(IMDB_ID_REGEX)) {
            val response = tmdbApi.findByExternalId(id, tmdbKey(), language = language)
            if (!response.isSuccessful) return null
            val match = response.body()?.let { data ->
                (if (mediaType == "movie") data.movieResults else data.tvResults)?.firstOrNull()
            }
            SearchArtwork(
                match?.title ?: match?.name,
                match?.id?.let { localizedPoster(it, mediaType, language) },
                match?.voteAverage?.toFloat()?.takeIf { it > 0f }
            )
        } else {
            val tmdbId = id.takeIf { it.startsWith("tmdb:") }?.substringAfter(':')?.toIntOrNull() ?: return null
            val response = if (mediaType == "movie") tmdbApi.getMovieDetails(tmdbId, tmdbKey(), language)
                else tmdbApi.getTvDetails(tmdbId, tmdbKey(), language)
            if (!response.isSuccessful) return null
            val details = response.body()
            SearchArtwork(
                details?.title ?: details?.name,
                localizedPoster(tmdbId, mediaType, language),
                details?.voteAverage?.toFloat()?.takeIf { it > 0f }
            )
        }
        searchPosterCache.put(key, result)
        return result
    }

    internal suspend fun localizedPosterForItem(id: String, type: String, language: String): String? =
        posterRequests.withPermit {
            val mediaType = normalizeMediaType(type)
            if (mediaType !in setOf("movie", "tv", "series") || !tmdbEnabled()) return@withPermit null
            val tmdbId = id.removePrefix("tmdb:").takeIf { id.startsWith("tmdb:") }?.toIntOrNull()
                ?: id.takeIf { it.startsWith("tt") }?.let { imdbToTmdb(it, mediaType) }
                ?: return@withPermit null
            localizedPoster(tmdbId, mediaType, language)
        }

    private suspend fun localizedPoster(tmdbId: Int, mediaType: String, language: String): String? {
        val code = language.substringBefore('-').lowercase()
        val key = "$mediaType:$tmdbId:$code"
        localizedPosterCache.get(key)?.let { return it.ifEmpty { null } }
        val response = if (mediaType == "movie") tmdbApi.getMovieImages(tmdbId, tmdbKey(), "$code,null")
            else tmdbApi.getTvImages(tmdbId, tmdbKey(), "$code,null")
        if (!response.isSuccessful) return null
        val poster = selectNotflixPoster(response.body()?.posters.orEmpty(), code)
        localizedPosterCache.put(key, poster.orEmpty())
        return poster
    }
    private fun tmdbKey(): String = integrationConfig.tmdbApiKey()
    @Volatile private var missingKeyLogged = false
    private fun tmdbEnabled(): Boolean {
        if (integrationConfig.isTmdbEnabled()) return true
        if (!missingKeyLogged) {
            missingKeyLogged = true
            Log.i(TAG, "TMDB disabled: configure the API key in Settings > Integration > Notflix")
        }
        return false
    }
    // Cache: IMDB ID -> TMDB ID (keyed by "$imdbId:$mediaType")
    private val imdbToTmdbCache = ConcurrentHashMap<String, Int>()
    
    // Cache: TMDB ID -> IMDB ID (keyed by "$tmdbId:$mediaType")
    private val tmdbToImdbCache = ConcurrentHashMap<String, String>()

    // Cache: TMDB TV id -> poster URL (calendar posters, fetched lazily per visible row)
    private val tvPosterCache = ConcurrentHashMap<Int, String>()

    private val imdbToTmdbInFlight = ConcurrentHashMap<String, CompletableDeferred<Int?>>()
    private val tmdbToImdbInFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    
    // Mutex for thread-safe cache operations
    private val cacheMutex = Mutex()
    
    /**
     * Convert an IMDB ID to a TMDB ID.
     * 
     * @param imdbId The IMDB ID (e.g., "tt0133093")
     * @param mediaType The media type ("movie" or "series"/"tv")
     * @return The TMDB ID, or null if not found
     */
    suspend fun imdbToTmdb(imdbId: String, mediaType: String): Int? = withContext(Dispatchers.IO) {
        // Validate IMDB ID format
        if (!imdbId.startsWith("tt")) {
            Log.w(TAG, "Invalid IMDB ID format: $imdbId")
            return@withContext null
        }
        
        val normalizedType = normalizeMediaType(mediaType)
        val cacheKey = "$imdbId:$normalizedType"

        // Check cache first
        imdbToTmdbCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Cache hit: IMDB $imdbId ($normalizedType) -> TMDB $cached")
            return@withContext cached
        }
        if (!tmdbEnabled()) return@withContext null
        
        val requestKey = cacheKey
        var requestDeferred: CompletableDeferred<Int?>
        while (true) {
            imdbToTmdbCache[cacheKey]?.let { return@withContext it }
            requestDeferred = CompletableDeferred()
            val existing = imdbToTmdbInFlight.putIfAbsent(requestKey, requestDeferred)
            if (existing == null) break
            try {
                return@withContext existing.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }

        try {
            Log.d(TAG, "Looking up TMDB ID for IMDB: $imdbId (type: $mediaType)")
            
            val response = tmdbApi.findByExternalId(
                externalId = imdbId,
                apiKey = tmdbKey(),
                externalSource = "imdb_id"
            )
            
            if (!response.isSuccessful) {
                Log.e(TAG, "TMDB API error: ${response.code()} - ${response.message()}")
                requestDeferred.complete(null)
                return@withContext null
            }
            
            val body = response.body()
            if (body == null) {
                requestDeferred.complete(null)
                return@withContext null
            }
            
            // Determine which results to use based on media type
            val result = when (normalizedType) {
                "movie" -> body.movieResults?.firstOrNull()
                "tv", "series" -> body.tvResults?.firstOrNull()
                else -> body.movieResults?.firstOrNull() ?: body.tvResults?.firstOrNull()
            }
            
            result?.let { found ->
                Log.d(TAG, "Found TMDB ID: ${found.id} for IMDB: $imdbId")
                
                // Cache both directions with bounded size
                cacheMutex.withLock {
                    if (imdbToTmdbCache.size >= MAX_ID_CACHE_ENTRIES) imdbToTmdbCache.clear()
                    if (tmdbToImdbCache.size >= MAX_ID_CACHE_ENTRIES) tmdbToImdbCache.clear()
                    imdbToTmdbCache[cacheKey] = found.id
                    tmdbToImdbCache["${found.id}:$normalizedType"] = imdbId
                }

                requestDeferred.complete(found.id)
                 
                return@withContext found.id
            }
            
            Log.w(TAG, "No TMDB result found for IMDB: $imdbId")
            requestDeferred.complete(null)
            null
            
        } catch (e: CancellationException) {
            requestDeferred.cancel(e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up TMDB ID for $imdbId: ${e.message}", e)
            requestDeferred.complete(null)
            null
        } finally {
            imdbToTmdbInFlight.remove(requestKey, requestDeferred)
        }
    }
    
    /**
     * Convert a TMDB ID to an IMDB ID.
     * 
     * @param tmdbId The TMDB ID
     * @param mediaType The media type ("movie" or "series"/"tv")
     * @return The IMDB ID, or null if not found
     */
    suspend fun tmdbToImdb(tmdbId: Int, mediaType: String): String? = withContext(Dispatchers.IO) {
        val normalizedType = normalizeMediaType(mediaType)
        val cacheKey = "$tmdbId:$normalizedType"

        // Check cache first
        tmdbToImdbCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Cache hit: TMDB $tmdbId ($normalizedType) -> IMDB $cached")
            return@withContext cached
        }
        if (!tmdbEnabled()) return@withContext null
        
        val requestKey = cacheKey
        var requestDeferred: CompletableDeferred<String?>
        while (true) {
            tmdbToImdbCache[cacheKey]?.let { return@withContext it }
            requestDeferred = CompletableDeferred()
            val existing = tmdbToImdbInFlight.putIfAbsent(requestKey, requestDeferred)
            if (existing == null) break
            try {
                return@withContext existing.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }

        try {
            Log.d(TAG, "Looking up IMDB ID for TMDB: $tmdbId (type: $mediaType)")
            
            val response = when (normalizedType) {
                "movie" -> tmdbApi.getMovieExternalIds(tmdbId, tmdbKey())
                "tv", "series" -> tmdbApi.getTvExternalIds(tmdbId, tmdbKey())
                else -> tmdbApi.getMovieExternalIds(tmdbId, tmdbKey())
            }
            
            if (!response.isSuccessful) {
                Log.e(TAG, "TMDB API error: ${response.code()} - ${response.message()}")
                requestDeferred.complete(null)
                return@withContext null
            }
            
            val body = response.body()
            if (body == null) {
                requestDeferred.complete(null)
                return@withContext null
            }
            
            body.imdbId?.let { imdbId ->
                Log.d(TAG, "Found IMDB ID: $imdbId for TMDB: $tmdbId")
                
                // Cache both directions with bounded size
                cacheMutex.withLock {
                    if (tmdbToImdbCache.size >= MAX_ID_CACHE_ENTRIES) tmdbToImdbCache.clear()
                    if (imdbToTmdbCache.size >= MAX_ID_CACHE_ENTRIES) imdbToTmdbCache.clear()
                    tmdbToImdbCache[cacheKey] = imdbId
                    imdbToTmdbCache["$imdbId:$normalizedType"] = tmdbId
                }

                requestDeferred.complete(imdbId)
                 
                return@withContext imdbId
            }
            
            Log.w(TAG, "No IMDB ID found for TMDB: $tmdbId")
            requestDeferred.complete(null)
            null
            
        } catch (e: CancellationException) {
            requestDeferred.cancel(e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up IMDB ID for $tmdbId: ${e.message}", e)
            requestDeferred.complete(null)
            null
        } finally {
            tmdbToImdbInFlight.remove(requestKey, requestDeferred)
        }
    }
    
    /**
     * Get a TMDB ID from a video ID string.
     * Handles both IMDB IDs (tt...) and TMDB IDs.
     * 
     * @param videoId The video ID (can be IMDB or TMDB format)
     * @param mediaType The media type
     * @param fallbackImdbId Optional IMDB ID from meta response
     * @return The TMDB ID as a string, or null if conversion failed
     */
    suspend fun ensureTmdbId(videoId: String, mediaType: String, fallbackImdbId: String? = null): String? {
        // Check if it's already a TMDB ID (numeric or prefixed)
        val cleanId = videoId
            .removePrefix("tmdb:")
            .removePrefix("movie:")
            .removePrefix("series:")

        // Stremio-style series ids can look like: tt1234567:season:episode
        // Plugins/TMDB lookup need the base external id only.
        val idPart = cleanId
            .substringBefore(':')
            .substringBefore('/')
            .trim()
        
        // If it's an IMDB ID, convert it
        if (idPart.startsWith("tt")) {
            val tmdbId = imdbToTmdb(idPart, normalizeMediaType(mediaType))
            return tmdbId?.toString()
        }
        
        // If it looks like a numeric ID, assume it's already a TMDB ID
        if (idPart.isNotEmpty() && idPart.all { it.isDigit() }) {
            return idPart
        }

        // Fallback: use the IMDB ID supplied by the addon's meta response
        val normalizedFallback = fallbackImdbId
            ?.trim()
            ?.substringBefore(':')
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (normalizedFallback != null) {
            val tmdbId = imdbToTmdb(normalizedFallback, normalizeMediaType(mediaType))
            if (tmdbId != null) return tmdbId.toString()
        }
        
        // Unknown format
        Log.w(TAG, "Unknown video ID format: $videoId")
        return null
    }
    
    /**
     * Normalize media type to consistent format
     */
    private fun normalizeMediaType(mediaType: String): String {
        return when (mediaType.lowercase()) {
            "series", "tv", "show", "tvshow" -> "tv"
            "movie", "film" -> "movie"
            else -> mediaType.lowercase()
        }
    }
    
    /**
     * Clear all caches
     */
    fun clearCache() {
        imdbToTmdbCache.clear()
        tmdbToImdbCache.clear()
        imdbToTmdbInFlight.clear()
        tmdbToImdbInFlight.clear()
        top10Cache.clear()
        tvPosterCache.clear()
        tvPosterLocalizedCache.clear()
        localizedBriefCache.evictAll()
        searchPosterCache.evictAll()
        localizedPosterCache.evictAll()
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * Pre-populate cache with known mappings
     */
    fun preCacheMapping(imdbId: String, tmdbId: Int, mediaType: String = "movie") {
        val normalizedType = normalizeMediaType(mediaType)
        imdbToTmdbCache["$imdbId:$normalizedType"] = tmdbId
        tmdbToImdbCache["$tmdbId:$normalizedType"] = imdbId
    }

    /** Returns the cached TMDB ID for an IMDB ID without making any network call. */
    fun cachedTmdbId(imdbId: String): Int? =
        imdbToTmdbCache["$imdbId:movie"] ?: imdbToTmdbCache["$imdbId:tv"]

    private data class CachedTop10(
        val items: List<com.nuvio.tv.domain.model.MetaPreview>,
        val fetchedAtMs: Long
    )

    private val top10Cache = ConcurrentHashMap<String, CachedTop10>()
    private val tvPosterLocalizedCache = ConcurrentHashMap<String, String>()

    internal fun normalizeTmdbRequestLanguage(language: String): String =
        when (language.trim().replace('_', '-').lowercase()) {
            "es" -> "es-ES"
            "es-419" -> "es-MX"
            "pt-br" -> "pt-BR"
            "pt" -> "pt-PT"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "it" -> "it-IT"
            else -> language.trim().ifBlank { "en" }
        }

    /**
     * International multi-platform Top 10 Movies / Series / Kids for the user's region (60-min memory cache).
     * For Movies & Series, combines JustWatch country trending (streamable locally) + Trakt global trending
     * with platform diversity cap + 1-per-saga deduplication, hydrated via TMDB in the active language.
     * Falls back seamlessly to TMDB Discover/Trending (also 1-per-saga) if needed or for certified Kids.
     */
    internal suspend fun fetchTop10ByCountry(
        mediaType: String,
        countryCode: String,
        language: String,
        kidsOnly: Boolean = false
    ): List<com.nuvio.tv.domain.model.MetaPreview> = withContext(Dispatchers.IO) {
        if (!tmdbEnabled()) return@withContext emptyList()
        val normalizedType = normalizeMediaType(mediaType)
        val region = countryCode.trim().uppercase().takeIf { it.length == 2 } ?: "US"
        val effectiveLang = normalizeTmdbRequestLanguage(language)
        val cacheKey = "$normalizedType:$region:$effectiveLang:$kidsOnly"
        val now = System.currentTimeMillis()
        top10Cache[cacheKey]?.takeIf { now - it.fetchedAtMs <= 60L * 60L * 1000L }?.let {
            return@withContext it.items
        }
        try {
            val apiKey = tmdbKey()
            val apiType = if (normalizedType == "tv") "series" else "movie"
            val contentType = com.nuvio.tv.domain.model.ContentType.fromString(apiType)

            // Redesigned multi-platform Top 10 for standard Movies & Series (JustWatch + Trakt + Netflix Tudum)
            val rankedPreviews = if (!kidsOnly) {
                val rankedIds = NotflixTop10Ranker.fetchRankedTmdbIds(
                    mediaType = normalizedType,
                    region = region,
                    language = effectiveLang,
                    traktClientId = integrationConfig.traktClientId(),
                    resolveTmdbIdByTitle = { query ->
                        runCatching {
                            val wantedType = if (normalizedType == "tv") "tv" else "movie"
                            tmdbApi.searchTitles(apiKey, query, "en-US").body()?.results.orEmpty()
                                .firstOrNull { it.mediaType.equals(wantedType, ignoreCase = true) }
                                ?.id
                        }.getOrNull()
                    }
                )
                if (rankedIds.size >= 5) {
                    val permits = Semaphore(5)
                    coroutineScope {
                        rankedIds.map { tmdbId ->
                            async {
                                permits.withPermit {
                                    runCatching {
                                        val resp = if (normalizedType == "tv") {
                                            tmdbApi.getTvDetails(tmdbId, apiKey, effectiveLang)
                                        } else {
                                            tmdbApi.getMovieDetails(tmdbId, apiKey, effectiveLang)
                                        }
                                        val dto = resp.body() ?: return@runCatching null
                                        val posterPath = dto.posterPath?.takeIf { it.isNotBlank() } ?: return@runCatching null
                                        val title = dto.title?.takeIf { it.isNotBlank() }
                                            ?: dto.name?.takeIf { it.isNotBlank() }
                                            ?: return@runCatching null
                                        val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)
                                        com.nuvio.tv.domain.model.MetaPreview(
                                            id = "tmdb:$tmdbId",
                                            type = contentType,
                                            rawType = apiType,
                                            name = title,
                                            poster = "https://image.tmdb.org/t/p/w342$posterPath",
                                            posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
                                            background = dto.backdropPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w1280$it" },
                                            logo = null,
                                            description = dto.overview?.takeIf { it.isNotBlank() },
                                            releaseInfo = year,
                                            imdbRating = dto.voteAverage?.toFloat()?.takeIf { it > 0f },
                                            ratingSource = com.nuvio.tv.domain.model.RatingSource.TMDB,
                                            genres = dto.genres?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }.orEmpty(),
                                            ageRating = null
                                        )
                                    }.getOrNull()
                                }
                            }
                        }.mapNotNull { it.await() }
                    }.take(10)
                } else emptyList()
            } else emptyList()

            val previews = if (rankedPreviews.size >= 5) {
                rankedPreviews
            } else {
                val rawResults = when {
                    kidsOnly && normalizedType == "movie" -> {
                        val certCountry = if (region == "ES") "ES" else "US"
                        val cert = if (region == "ES") "A|Ai" else "G"
                        val primary = tmdbApi.discoverMovies(
                            apiKey = apiKey,
                            language = effectiveLang,
                            page = 1,
                            sortBy = "popularity.desc",
                            withGenres = "16|10751",
                            withoutGenres = "27,53,80,10752",
                            watchRegion = region,
                            voteCountGte = 30,
                            certificationCountry = certCountry,
                            certification = cert,
                            includeAdult = false
                        ).body()?.results.orEmpty()
                        if (primary.size >= 10) primary else {
                            val fallback = tmdbApi.discoverMovies(
                                apiKey = apiKey,
                                language = effectiveLang,
                                page = 1,
                                sortBy = "popularity.desc",
                                withGenres = "16|10751",
                                withoutGenres = "27,53,80,10752",
                                voteCountGte = 30,
                                certificationCountry = "US",
                                certification = "G",
                                includeAdult = false
                            ).body()?.results.orEmpty()
                            (primary + fallback).distinctBy { it.id }
                        }
                    }
                    kidsOnly && normalizedType == "tv" -> {
                        tmdbApi.discoverTv(
                            apiKey = apiKey,
                            language = effectiveLang,
                            page = 1,
                            sortBy = "popularity.desc",
                            withGenres = "10762",
                            withoutGenres = "80,18,10768",
                            watchRegion = region,
                            voteCountGte = 15
                        ).body()?.results.orEmpty()
                    }
                    normalizedType == "movie" -> {
                        val regional = tmdbApi.discoverMovies(
                            apiKey = apiKey,
                            language = effectiveLang,
                            page = 1,
                            sortBy = "popularity.desc",
                            watchRegion = region,
                            withWatchMonetizationTypes = "flatrate|free|ads|rent|buy",
                            voteCountGte = 40,
                            includeAdult = false
                        ).body()?.results.orEmpty()
                        if (regional.size >= 10) regional else {
                            tmdbApi.getTrending("movie", "day", apiKey, effectiveLang, 1).body()?.results.orEmpty()
                        }
                    }
                    else -> {
                        val regional = tmdbApi.discoverTv(
                            apiKey = apiKey,
                            language = effectiveLang,
                            page = 1,
                            sortBy = "popularity.desc",
                            watchRegion = region,
                            withWatchMonetizationTypes = "flatrate|free|ads|rent|buy",
                            voteCountGte = 20
                        ).body()?.results.orEmpty()
                        if (regional.size >= 10) regional else {
                            tmdbApi.getTrending("tv", "day", apiKey, effectiveLang, 1).body()?.results.orEmpty()
                        }
                    }
                }
                val seenSagas = mutableSetOf<String>()
                rawResults.asSequence()
                    .filter { !it.posterPath.isNullOrBlank() }
                    .distinctBy { it.id }
                    .filter { dto ->
                        val title = dto.title?.takeIf { it.isNotBlank() } ?: dto.name.orEmpty()
                        val saga = NotflixTop10Ranker.sagaKey(title).ifBlank { dto.id.toString() }
                        seenSagas.add(saga)
                    }
                    .take(10)
                    .map { dto ->
                        val title = dto.title?.takeIf { it.isNotBlank() } ?: dto.name.orEmpty()
                        val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)
                        com.nuvio.tv.domain.model.MetaPreview(
                            id = "tmdb:${dto.id}",
                            type = contentType,
                            rawType = apiType,
                            name = title,
                            poster = dto.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                            posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
                            background = dto.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                            logo = null,
                            description = dto.overview?.takeIf { it.isNotBlank() },
                            releaseInfo = year,
                            imdbRating = dto.voteAverage?.toFloat()?.takeIf { it > 0f },
                            ratingSource = com.nuvio.tv.domain.model.RatingSource.TMDB,
                            genres = emptyList(),
                            ageRating = if (kidsOnly && normalizedType == "movie") "TP" else null
                        )
                    }
                    .toList()
            }
            if (previews.isNotEmpty()) {
                if (top10Cache.size >= 24) top10Cache.clear()
                top10Cache[cacheKey] = CachedTop10(previews, now)
            }
            previews
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchTop10ByCountry failed ($normalizedType/$region): ${e.message}")
            emptyList()
        }
    }

    private data class LocalizedBrief(val title: String?, val overview: String?)
    private val localizedBriefCache = android.util.LruCache<String, LocalizedBrief>(300)

    /**
     * Localizes My List previews (whose Trakt overviews arrive in English) into the active app language
     * via cached lightweight TMDB lookups with bounded concurrency.
     */
    internal suspend fun localizeMyListPreviews(
        items: List<com.nuvio.tv.domain.model.MetaPreview>,
        language: String
    ): List<com.nuvio.tv.domain.model.MetaPreview> = withContext(Dispatchers.IO) {
        if (items.isEmpty() || !tmdbEnabled()) return@withContext items
        val apiKey = tmdbKey()
        val effectiveLang = normalizeTmdbRequestLanguage(language)
        val permits = Semaphore(4)
        coroutineScope {
            items.map { item ->
                async {
                    val mediaType = normalizeMediaType(item.apiType)
                    if (mediaType != "movie" && mediaType != "tv") return@async item
                    val cacheKey = "$mediaType:${item.id}:$effectiveLang"
                    val cached = localizedBriefCache.get(cacheKey)
                    val brief = if (cached != null) cached else permits.withPermit {
                        runCatching {
                            val tmdbId = ensureTmdbId(item.id, item.apiType, fallbackImdbId = item.imdbId)?.toIntOrNull()
                                ?: return@runCatching null
                            val resp = if (mediaType == "tv") {
                                tmdbApi.getTvDetails(tmdbId, apiKey, effectiveLang)
                            } else {
                                tmdbApi.getMovieDetails(tmdbId, apiKey, effectiveLang)
                            }
                            val dto = resp.body() ?: return@runCatching null
                            LocalizedBrief(
                                title = (dto.title ?: dto.name)?.takeIf { it.isNotBlank() },
                                overview = dto.overview?.takeIf { it.isNotBlank() }
                            ).also { localizedBriefCache.put(cacheKey, it) }
                        }.getOrNull()
                    }
                    if (brief == null) item else item.copy(
                        name = brief.title ?: item.name,
                        description = brief.overview ?: item.description
                    )
                }
            }.map { it.await() }
        }
    }

    fun apiKey(): String = tmdbKey()

    /**
     * Póster de una serie a partir de su id de TMDB. Se usa para el calendario, donde Trakt solo
     * trae ids. Cachea las URLs para no repetir la petición por cada fila visible.
     */
    suspend fun tvPosterUrl(tmdbId: Int, language: String = java.util.Locale.getDefault().toLanguageTag()): String? = withContext(Dispatchers.IO) {
        val effectiveLang = normalizeTmdbRequestLanguage(language)
        val cacheKey = "$tmdbId:$effectiveLang"
        tvPosterLocalizedCache[cacheKey]?.let { return@withContext it }
        if (!tmdbEnabled()) return@withContext null
        try {
            val response = tmdbApi.getTvDetails(tmdbId, tmdbKey(), effectiveLang)
            val path = response.body()?.posterPath ?: return@withContext null
            "https://image.tmdb.org/t/p/w500$path".also { url ->
                if (tvPosterLocalizedCache.size >= MAX_TV_POSTER_CACHE_ENTRIES) tvPosterLocalizedCache.clear()
                tvPosterLocalizedCache[cacheKey] = url
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches backdrop and poster URLs from TMDB for the given IMDB ID.
     * Returns null if the IMDB ID doesn't start with "tt" or if TMDB has no data.
     * Results are NOT cached here — callers should persist what they need.
     */
    suspend fun fetchImdbImages(imdbId: String, mediaType: String): TmdbImages? =
        withContext(Dispatchers.IO) {
            if (!imdbId.startsWith("tt")) return@withContext null
            if (!tmdbEnabled()) return@withContext null
            val tmdbId = imdbToTmdb(imdbId, mediaType) ?: return@withContext null
            try {
                val isMovie = normalizeMediaType(mediaType) == "movie"
                val response = if (isMovie)
                    tmdbApi.getMovieDetails(tmdbId, tmdbKey())
                else
                    tmdbApi.getTvDetails(tmdbId, tmdbKey())
                val body = response.body() ?: return@withContext null
                TmdbImages(
                    backdropUrl = body.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    posterUrl = body.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    runtimeMinutes = body.runtime
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
}

internal fun searchImageUrl(path: String?): String? =
    path?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }

internal fun selectNotflixPoster(posters: List<TmdbImage>, language: String): String? {
    val code = language.substringBefore('-')
    return posters.firstNotNullOfOrNull { image ->
        searchImageUrl(image.filePath).takeIf { image.iso6391.equals(code, ignoreCase = true) }
    } ?: posters.firstNotNullOfOrNull { image ->
        searchImageUrl(image.filePath).takeIf { image.iso6391.equals("en", ignoreCase = true) }
    }
}

data class TmdbImages(val backdropUrl: String?, val posterUrl: String?, val runtimeMinutes: Int? = null)
