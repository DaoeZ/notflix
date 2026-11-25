package com.nuvio.tv.core.tmdb

import com.nuvio.tv.core.profile.AgeRatingLookup
import com.nuvio.tv.core.profile.TmdbAgeRatingSource
import com.nuvio.tv.core.profile.TmdbAvailability
import com.nuvio.tv.core.profile.ageLevelOf
import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import java.util.Locale
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consulta la clasificación en TMDB reutilizando la selección por regiones de
 * TmdbMetadataService (idioma del dispositivo, luego US y GB).
 */
@Singleton
class TmdbAgeRatingSourceImpl @Inject constructor(
    private val tmdbService: TmdbService,
    private val tmdbApi: TmdbApi,
    private val integrationConfig: NotflixIntegrationConfig
) : TmdbAgeRatingSource {
    override suspend fun lookup(apiType: String, id: String, fallbackImdbId: String?): AgeRatingLookup {
        if (!integrationConfig.isTmdbEnabled()) return AgeRatingLookup(null, cacheable = false)
        val numericId = runCatching {
            tmdbService.ensureTmdbId(id, apiType, fallbackImdbId = fallbackImdbId)
        }.getOrElse { if (it is CancellationException) throw it else null }?.toIntOrNull() ?: return AgeRatingLookup(null, cacheable = false)

        val language = Locale.getDefault().toLanguageTag()
        return runCatching {
            val raw = if (isSeriesType(apiType)) {
                val response = tmdbApi.getTvContentRatings(numericId, integrationConfig.tmdbApiKey())
                val body = response.body()
                if (!response.isSuccessful || body == null) return@runCatching AgeRatingLookup(null, cacheable = false)
                val ratings = body.results.orEmpty()
                selectTvAgeRating(ratings, language)
            } else {
                val response = tmdbApi.getMovieReleaseDates(numericId, integrationConfig.tmdbApiKey())
                val body = response.body()
                if (!response.isSuccessful || body == null) return@runCatching AgeRatingLookup(null, cacheable = false)
                val releases = body.results.orEmpty()
                selectMovieAgeRating(releases, language)
            }
            AgeRatingLookup(ageLevelOf(raw), cacheable = true)
        }.getOrElse {
            if (it is CancellationException) throw it
            AgeRatingLookup(null, cacheable = false)
        }
    }

    private fun isSeriesType(type: String): Boolean =
        type.equals("series", ignoreCase = true) ||
            type.equals("tv", ignoreCase = true) ||
            type.equals("anime", ignoreCase = true)
}

@Singleton
class IntegrationTmdbAvailability @Inject constructor(
    private val integrationConfig: NotflixIntegrationConfig
) : TmdbAvailability {
    override fun isEnabled(): Boolean = integrationConfig.isTmdbEnabled()
}
