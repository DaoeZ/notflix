package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.data.repository.CalendarNuvioShow
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

/**
 * Proyección de un NextUp no emitido de `HomeUiState.upcomingItems`. No consulta la red: reutiliza
 * el mismo dato que la fila «En emisión» de Inicio.
 */
internal fun ContinueWatchingItem.toCalendarNuvioShow(): CalendarNuvioShow? {
    val next = this as? ContinueWatchingItem.NextUp ?: return null
    val info = next.info
    if (info.hasAired) return null
    val id = info.contentId.trim()
    if (id.isEmpty()) return null
    return CalendarNuvioShow(
        contentId = id,
        seriesName = info.name,
        poster = info.poster,
        season = info.season,
        episode = info.episode,
        episodeTitle = info.episodeTitle,
        released = info.released,
        releaseTimestamp = info.releaseTimestamp,
        contentType = info.contentType,
        ageRating = info.ageRating
    )
}
