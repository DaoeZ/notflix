package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaPreviewDto
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.RatingSource

/**
 * M57: manifest id of mrcanelas's «The Movie Database Addon». Checked against the addon's own
 * public manifest (11-11-2026): the catalog items carry TMDB's vote_average in imdbRating.
 */
const val TMDB_ADDON_MANIFEST_ID = "tmdb-addon"

internal fun catalogRatingSource(addonId: String): RatingSource =
    if (addonId == TMDB_ADDON_MANIFEST_ID) RatingSource.TMDB else RatingSource.IMDB

fun MetaPreviewDto.toDomainOrNull(
    catalogType: String,
    sourceAddonBaseUrl: String? = null,
    ratingSource: RatingSource = RatingSource.IMDB
): MetaPreview? {
    val resolvedId = id?.takeIf { it.isNotBlank() } ?: return null
    val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
    val resolvedType = type?.takeIf { it.isNotBlank() } ?: catalogType
    return MetaPreview(
        id = resolvedId,
        type = ContentType.fromString(resolvedType),
        rawType = resolvedType,
        name = resolvedName,
        poster = poster,
        posterShape = PosterShape.fromString(posterShape),
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toFloatOrNull(),
        genres = genres ?: emptyList(),
        runtime = runtime,
        status = status?.trim()?.takeIf { it.isNotBlank() },
        released = released,
        country = country,
        imdbId = imdbId,
        slug = slug,
        landscapePoster = landscapePoster,
        rawPosterUrl = rawPosterUrl,
        director = coerceStringList(director),
        writer = coerceStringList(writer).ifEmpty { coerceStringList(writers) },
        links = links?.mapNotNull { it.toDomain() } ?: emptyList(),
        behaviorHints = mapBehaviorHints(behaviorHints),
        trailers = mapTrailers(trailers, trailerStreams),
        trailerYtIds = collectTrailerYtIds(trailers, trailerStreams),
        sourceAddonBaseUrl = sourceAddonBaseUrl,
        ratingSource = ratingSource
    )
}
