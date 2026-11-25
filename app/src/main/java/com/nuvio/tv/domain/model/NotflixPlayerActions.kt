package com.nuvio.tv.domain.model

/** Stable presentation IDs only. Playback availability and callbacks remain owned by the player. */
enum class NotflixPlayerAction(val id: String) {
    AUDIO_SUBTITLES("audio_subtitles"), EPISODES("episodes"), NEXT_EPISODE("next_episode"), SPEED("speed"),
    NIGHT_MODE("night_mode"), SLEEP_TIMER("sleep_timer"),
    SOURCES("sources"), ASPECT("aspect"), STREAM_INFO("stream_info"), CHAPTERS("chapters"),
    SKIP_SEGMENT("skip_segment"), SHARPEN("sharpen"), PARTY("party"), ENGINE("engine"),
    EXTERNAL("external"), REPORT("report"), MORE("more")
}

object NotflixPlayerActions {
    /** Five configurable actions plus the fixed More button. Actual translated widths may fit fewer. */
    const val MAX_SELECTED = 5
    val configurable = NotflixPlayerAction.entries.filterNot { it == NotflixPlayerAction.MORE }
    val defaults = listOf(NotflixPlayerAction.AUDIO_SUBTITLES, NotflixPlayerAction.EPISODES,
        NotflixPlayerAction.NEXT_EPISODE, NotflixPlayerAction.SPEED).map { it.id }

    fun normalize(ids: List<String>): List<String> = ids.distinct()
        .filter { id -> configurable.any { it.id == id } }.take(MAX_SELECTED)

    fun decode(raw: String?): List<String> {
        if (raw == null) return defaults
        if (raw.isBlank()) return emptyList()
        return normalize(raw.split(',')).ifEmpty { defaults }
    }

    fun toggle(ids: List<String>, id: String): List<String> {
        val current = normalize(ids)
        return if (id in current) current - id else normalize(current + id)
    }

    fun move(ids: List<String>, id: String, delta: Int): List<String> {
        val current = normalize(ids).toMutableList()
        val from = current.indexOf(id)
        val to = from + delta.coerceIn(-1, 1)
        if (from >= 0 && to in current.indices) {
            val item = current.removeAt(from); current.add(to, item)
        }
        return current
    }

    data class Layout(val row: List<NotflixPlayerAction>, val more: List<NotflixPlayerAction>)

    /** A configured action that cannot fit stays reachable in More. No title is ellipsized. */
    fun layout(available: List<NotflixPlayerAction>, selected: List<String>, width: Float,
        measuredWidths: Map<NotflixPlayerAction, Float>, spacing: Float): Layout {
        val candidates = normalize(selected).mapNotNull { id -> available.firstOrNull { it.id == id } }
        var used = measuredWidths[NotflixPlayerAction.MORE] ?: 0f
        val row = candidates.takeWhile { action ->
            val next = used + spacing + (measuredWidths[action] ?: Float.POSITIVE_INFINITY)
            (next <= width).also { fits -> if (fits) used = next }
        }
        return Layout(row + NotflixPlayerAction.MORE, available.distinct()
            .filterNot { it == NotflixPlayerAction.MORE || it in row })
    }
}
