package com.nuvio.tv.data.remote.dto.trakt

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Un estreno de «calendars/my/shows» de Trakt. `first_aired` es un instante ISO 8601 en UTC.
 */
@JsonClass(generateAdapter = true)
data class TraktCalendarShowDto(
    @Json(name = "first_aired") val firstAired: String? = null,
    @Json(name = "episode") val episode: TraktCalendarEpisodeDto? = null,
    @Json(name = "show") val show: TraktCalendarShowInfoDto? = null
)

@JsonClass(generateAdapter = true)
data class TraktCalendarEpisodeDto(
    @Json(name = "season") val season: Int? = null,
    @Json(name = "number") val number: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "ids") val ids: TraktIdsDto? = null
)

@JsonClass(generateAdapter = true)
data class TraktCalendarShowInfoDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "ids") val ids: TraktIdsDto? = null
)
