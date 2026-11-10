package com.nuvio.tv.data.repository

import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.util.parseEpisodeReleaseInstant
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktCalendarShowDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** Ventana del calendario: hoy incluido, hoy + 30 excluido (igual que la fila «En emisión»). */
const val CALENDAR_WINDOW_DAYS = 30

enum class CalendarSource { TRAKT, NUVIO }

/**
 * Un estreno del calendario, ya normalizado desde Trakt o desde las series de Nuvio.
 * [contentId] es el id con el que se abre la ficha (IMDb si existe; si no, TMDB o el id de Nuvio).
 */
data class CalendarEntry(
    val contentId: String,
    val seriesName: String,
    val poster: String?,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val airInstant: Instant,
    val hasTime: Boolean,
    val contentType: String = "series",
    val tmdbId: Int? = null,
    val ageRating: String? = null
)

/** Un día local con al menos un estreno, ordenado por hora. */
data class CalendarDay(
    val date: LocalDate,
    val entries: List<CalendarEntry>
)

data class CalendarResult(
    val source: CalendarSource,
    val entries: List<CalendarEntry>,
    /** Trakt estaba conectado pero falló; se está mostrando el origen Nuvio. */
    val traktFailed: Boolean = false
)

/**
 * Proyección mínima de un NextUp no emitido de `HomeUiState.upcomingItems`. La presentación la
 * construye sin red; aquí solo se normalizan fechas y se elige la fuente.
 */
data class CalendarNuvioShow(
    val contentId: String,
    val seriesName: String,
    val poster: String?,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val released: String?,
    val releaseTimestamp: Long?,
    val contentType: String = "series",
    val ageRating: String? = null
)

/**
 * Decide el origen del calendario y normaliza sus entradas.
 *
 * Decisión del promotor: Trakt si hay cuenta conectada; si no hay cuenta o Trakt falla, se usan
 * los próximos episodios que ya calcula Nuvio (`upcomingItems`). Sin token no se llama a Trakt.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val traktApi: TraktApi,
    private val traktAuthService: TraktAuthService,
    private val tmdbService: TmdbService
) {
    suspend fun upcoming(
        days: Int = CALENDAR_WINDOW_DAYS,
        nuvioUpcoming: List<CalendarNuvioShow> = emptyList(),
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): CalendarResult {
        val nuvioEntries = nuvioUpcoming.mapNotNull { it.toCalendarEntry(zone) }
        return when (val outcome = loadTrakt(days = days, now = now, zone = zone, nuvioUpcoming = nuvioUpcoming)) {
            is TraktOutcome.Loaded -> CalendarResult(CalendarSource.TRAKT, outcome.entries)
            TraktOutcome.Failed -> CalendarResult(CalendarSource.NUVIO, nuvioEntries, traktFailed = true)
            TraktOutcome.Unavailable -> CalendarResult(CalendarSource.NUVIO, nuvioEntries)
        }
    }

    /** Póster de Trakt por su id de TMDB; Nuvio ya trae el suyo. */
    suspend fun posterFor(entry: CalendarEntry): String? {
        entry.poster?.takeIf { it.isNotBlank() }?.let { return it }
        val tmdbId = entry.tmdbId ?: return null
        return tmdbService.tvPosterUrl(tmdbId)
    }

    private suspend fun loadTrakt(
        days: Int,
        now: Instant,
        zone: ZoneId,
        nuvioUpcoming: List<CalendarNuvioShow>
    ): TraktOutcome {
        if (!traktAuthService.hasRequiredCredentials()) return TraktOutcome.Unavailable
        val authenticated = runCatching { traktAuthService.getCurrentAuthState().isAuthenticated }
            .getOrDefault(false)
        if (!authenticated) return TraktOutcome.Unavailable

        val startDate = now.atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return try {
            val response = traktAuthService.executeAuthorizedRequest { authorization ->
                traktApi.getMyShowsCalendar(
                    authorization = authorization,
                    startDate = startDate,
                    days = days.coerceAtLeast(1)
                )
            }
            if (response == null || !response.isSuccessful) return TraktOutcome.Failed
            val body = response.body() ?: return TraktOutcome.Failed
            val nuvioById = nuvioUpcoming.associateBy { it.contentId }
            TraktOutcome.Loaded(body.mapNotNull { it.toCalendarEntry(nuvioById) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TraktOutcome.Failed
        }
    }

    private sealed interface TraktOutcome {
        data object Unavailable : TraktOutcome
        data object Failed : TraktOutcome
        data class Loaded(val entries: List<CalendarEntry>) : TraktOutcome
    }
}

/**
 * Agrupa por día en la zona del dispositivo. Sin fecha no hay entrada (se descarta antes de
 * llegar aquí); fuera de [today, today + [days]) tampoco. Orden por hora y, a igual hora, por serie.
 */
fun groupByLocalDay(
    entries: List<CalendarEntry>,
    zone: ZoneId,
    today: LocalDate,
    days: Int = CALENDAR_WINDOW_DAYS
): List<CalendarDay> {
    val endExclusive = today.plusDays(days.toLong())
    return entries
        .mapNotNull { entry ->
            val date = entry.airInstant.atZone(zone).toLocalDate()
            when {
                date.isBefore(today) -> null
                !date.isBefore(endExclusive) -> null
                else -> date to entry
            }
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (date, dayEntries) ->
            CalendarDay(
                date = date,
                entries = dayEntries.sortedWith(
                    compareBy<CalendarEntry> { it.airInstant }.thenBy { it.seriesName.lowercase() }
                )
            )
        }
}

/**
 * Convierte un próximo episodio de Nuvio. Una fecha sin hora se conserva como fecha local (no se
 * desplaza al día anterior al oeste de UTC); un instante real se respeta tal cual.
 */
internal fun CalendarNuvioShow.toCalendarEntry(zone: ZoneId): CalendarEntry? {
    val id = contentId.trim().takeIf { it.isNotEmpty() } ?: return null
    val raw = released?.trim().orEmpty()
    // Una fecha sin hora manda sobre el timestamp: el pipeline la convierte en medianoche UTC y
    // priorizarla desplazaría el día al oeste de UTC.
    val dateOnly = raw.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val timestamp = releaseTimestamp?.takeIf { it > 0L }
    val instant = dateOnly?.atStartOfDay(zone)?.toInstant()
        ?: timestamp?.let(Instant::ofEpochMilli)
        ?: parseEpisodeReleaseInstant(raw, zone)
        ?: return null
    return CalendarEntry(
        contentId = id,
        seriesName = seriesName.trim().ifEmpty { id },
        poster = poster,
        season = season,
        episode = episode,
        episodeTitle = episodeTitle?.trim()?.takeIf { it.isNotEmpty() },
        airInstant = instant,
        hasTime = dateOnly == null && (timestamp != null || raw.contains('T')),
        contentType = contentType.ifBlank { "series" },
        ageRating = ageRating
    )
}

/**
 * Convierte un estreno de Trakt. El póster se toma de la serie de Nuvio si coincide el id; si no,
 * queda el id de TMDB para cargarlo de forma perezosa al pintar la fila.
 */
internal fun TraktCalendarShowDto.toCalendarEntry(
    nuvioById: Map<String, CalendarNuvioShow>
): CalendarEntry? {
    val aired = firstAired?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val instant = runCatching { Instant.parse(aired) }.getOrNull()
        ?: parseEpisodeReleaseInstant(aired, ZoneOffset.UTC)
        ?: return null
    val ids = show?.ids
    val imdb = ids?.imdb?.trim()?.takeIf { it.isNotEmpty() }
    val tmdb = ids?.tmdb
    val contentId = imdb ?: tmdb?.toString() ?: ids?.trakt?.toString() ?: return null
    val match = imdb?.let { nuvioById[it] } ?: nuvioById[contentId]
    val name = show?.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: match?.seriesName
        ?: ids?.slug?.trim()?.takeIf { it.isNotEmpty() }
        ?: contentId
    return CalendarEntry(
        contentId = contentId,
        seriesName = name,
        poster = match?.poster,
        season = episode?.season,
        episode = episode?.number,
        episodeTitle = episode?.title?.trim()?.takeIf { it.isNotEmpty() },
        airInstant = instant,
        hasTime = aired.contains('T'),
        contentType = "series",
        tmdbId = tmdb,
        ageRating = match?.ageRating
    )
}
