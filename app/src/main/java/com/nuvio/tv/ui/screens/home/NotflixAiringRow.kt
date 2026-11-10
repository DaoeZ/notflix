package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.util.parseEpisodeReleaseInstant
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class AiringDayLabel { TODAY, TOMORROW, WEEKDAY }

/** Un NextUp no emitido dentro de la ventana local de 7 días. */
internal data class AiringEntry(
    val info: NextUpInfo,
    val localDate: LocalDate,
    val instant: Instant,
    val dayLabel: AiringDayLabel
)

/**
 * Próximos episodios de las series que el perfil ya sigue, entre hoy y hoy + 7 días
 * (hoy incluido, día 8 excluido) en la zona del dispositivo. Sin fecha no entra.
 * Orden ascendente, una entrada por serie, máximo [limit].
 */
internal fun airingThisWeek(
    upcoming: List<ContinueWatchingItem>,
    now: Instant,
    zone: ZoneId,
    limit: Int = 20
): List<AiringEntry> {
    val today = now.atZone(zone).toLocalDate()
    val endExclusive = today.plusDays(7)
    return upcoming.asSequence()
        .filterIsInstance<ContinueWatchingItem.NextUp>()
        .filter { !it.info.hasAired }
        .mapNotNull { next ->
            val info = next.info
            val instant = info.releaseTimestamp?.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
                ?: parseEpisodeReleaseInstant(info.released, zone)
                ?: return@mapNotNull null
            val localDate = instant.atZone(zone).toLocalDate()
            if (localDate.isBefore(today) || !localDate.isBefore(endExclusive)) return@mapNotNull null
            val label = when (localDate) {
                today -> AiringDayLabel.TODAY
                today.plusDays(1) -> AiringDayLabel.TOMORROW
                else -> AiringDayLabel.WEEKDAY
            }
            AiringEntry(info = info, localDate = localDate, instant = instant, dayLabel = label)
        }
        .sortedWith(compareBy<AiringEntry> { it.instant }.thenBy { it.info.contentId })
        .distinctBy { it.info.contentId }
        .take(limit)
        .toList()
}

/** Proyección mínima para que la tarjeta de «En emisión» abra la ficha de la serie. */
internal fun NextUpInfo.toAiringMetaPreview(): MetaPreview = MetaPreview(
    id = contentId,
    type = ContentType.fromString(contentType),
    rawType = contentType,
    name = name,
    poster = poster,
    posterShape = PosterShape.POSTER,
    background = backdrop,
    logo = logo,
    description = episodeDescription,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres,
    ageRating = ageRating,
    imdbId = contentId.takeIf { it.startsWith("tt") }
)
