package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.data.remote.api.ImdbTapframeApi
import com.nuvio.tv.data.remote.api.SeriesGraphApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImdbEpisodeRatingsRepository @Inject constructor(
    private val imdbTapframeApi: ImdbTapframeApi,
    private val seriesGraphApi: SeriesGraphApi,
    private val tmdbMetadataService: TmdbMetadataService
) {
    private data class CacheEntry(
        val ratings: Map<Pair<Int, Int>, Double>,
        val expiresAtMs: Long
    )

    private val tag = "ImdbEpisodeRatingsRepo"
    private val cacheTtlMs = 30L * 60L * 1000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Map<Pair<Int, Int>, Double>>>()
    private val inFlightMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        expectedEpisodes: Set<Pair<Int, Int>> = emptySet()
    ): Map<Pair<Int, Int>, Double> {
        val normalizedImdbId = imdbId
            ?.trim()
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
            ?.substringBefore(':')
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return emptyMap()

        val seasonsKey = expectedEpisodes.map { it.first }.distinct().sorted().joinToString(",")
        val cacheKey = "imdb:${normalizedImdbId.orEmpty()}|tmdb:${normalizedTmdbId ?: 0}|s:$seasonsKey"

        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (cached.expiresAtMs > now) return cached.ratings
            cache.remove(cacheKey)
        }

        val deferred = inFlightMutex.withLock {
            inFlight[cacheKey] ?: scope.async {
                try {
                    fetchEpisodeRatings(
                        imdbId = normalizedImdbId,
                        tmdbId = normalizedTmdbId,
                        expectedEpisodes = expectedEpisodes
                    ).also { result ->
                        cache[cacheKey] = CacheEntry(
                            ratings = result,
                            expiresAtMs = System.currentTimeMillis() + cacheTtlMs
                        )
                    }
                } finally {
                    inFlightMutex.withLock {
                        inFlight.remove(cacheKey)
                    }
                }
            }.also { created ->
                inFlight[cacheKey] = created
            }
        }

        return deferred.await()
    }

    /**
     * Per-episode fallback order: 1) IMDb (Tapframe), 2) SeriesGraph, 3) TMDB.
     * Earlier sources always win; later sources only fill episodes still missing a rating.
     */
    private suspend fun fetchEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        expectedEpisodes: Set<Pair<Int, Int>>
    ): Map<Pair<Int, Int>, Double> {
        val merged = linkedMapOf<Pair<Int, Int>, Double>()

        if (!imdbId.isNullOrBlank()) {
            merged.putAll(fetchFromImdbTapframe(imdbId))
        }

        val needsMoreAfterImdb = merged.isEmpty() || expectedEpisodes.any { it !in merged }
        if (tmdbId != null && tmdbId > 0 && needsMoreAfterImdb) {
            fetchFromSeriesGraph(tmdbId).forEach { (key, value) ->
                if (key !in merged) merged[key] = value
            }
        }

        val missingSeasons = if (expectedEpisodes.isNotEmpty()) {
            expectedEpisodes.asSequence()
                .filter { it !in merged }
                .map { it.first }
                .filter { it > 0 }
                .distinct()
                .sorted()
                .toList()
        } else {
            emptyList()
        }
        if (tmdbId != null && tmdbId > 0 && missingSeasons.isNotEmpty()) {
            fetchFromTmdb(tmdbId, missingSeasons).forEach { (key, value) ->
                if (key !in merged) merged[key] = value
            }
        }

        return merged
    }

    private suspend fun fetchFromTmdb(
        tmdbId: Int,
        seasonNumbers: List<Int>
    ): Map<Pair<Int, Int>, Double> {
        return try {
            tmdbMetadataService.fetchEpisodeRatings(tmdbId, seasonNumbers)
        } catch (e: Exception) {
            Log.w(tag, "Error fetching TMDB episode ratings for tmdbId=$tmdbId", e)
            emptyMap()
        }
    }

    private suspend fun fetchFromImdbTapframe(imdbId: String): Map<Pair<Int, Int>, Double> {
        return try {
            val response = imdbTapframeApi.getSeasonRatings(imdbId)
            if (!response.isSuccessful) {
                Log.w(tag, "Failed primary season ratings for imdbId=$imdbId (${response.code()})")
                return emptyMap()
            }
            toRatingsMap(response.body().orEmpty())
        } catch (e: Exception) {
            Log.w(tag, "Error fetching primary season ratings for imdbId=$imdbId", e)
            emptyMap()
        }
    }

    private suspend fun fetchFromSeriesGraph(tmdbId: Int): Map<Pair<Int, Int>, Double> {
        return try {
            val response = seriesGraphApi.getSeasonRatings(tmdbId)
            if (!response.isSuccessful) {
                Log.w(tag, "Failed fallback season ratings for tmdbId=$tmdbId (${response.code()})")
                return emptyMap()
            }
            toRatingsMap(response.body().orEmpty())
        } catch (e: Exception) {
            Log.w(tag, "Error fetching fallback season ratings for tmdbId=$tmdbId", e)
            emptyMap()
        }
    }

    private fun toRatingsMap(payload: List<com.nuvio.tv.data.remote.api.SeriesGraphSeasonRatingsDto>): Map<Pair<Int, Int>, Double> {
        return buildMap {
            payload.forEach { season ->
                season.episodes.orEmpty().forEach { episode ->
                    val seasonNumber = episode.seasonNumber ?: return@forEach
                    val episodeNumber = episode.episodeNumber ?: return@forEach
                    val voteAverage = episode.voteAverage?.takeIf { it > 0.0 } ?: return@forEach
                    put(seasonNumber to episodeNumber, voteAverage)
                }
            }
        }
    }
}
