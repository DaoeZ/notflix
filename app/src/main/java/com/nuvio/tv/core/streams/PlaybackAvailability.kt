package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.ScraperInfo
import com.nuvio.tv.domain.model.Video

internal fun Addon.supportsStreamResource(type: String, videoId: String): Boolean =
    supportsResource("stream", type, videoId)

internal fun Addon.supportsResource(name: String, type: String, id: String): Boolean {
    val normalizedType = if (type.equals("show", ignoreCase = true) || type.equals("tv", ignoreCase = true)) "series" else type
    return resources.any { resource ->
        resource.name == name &&
            (resource.types.isEmpty() || resource.types.contains(type) || resource.types.contains(normalizedType)) &&
            run {
                val prefixes = resource.idPrefixes?.takeIf { it.isNotEmpty() }
                    ?: idPrefixes.takeIf { it.isNotEmpty() }
                prefixes == null || prefixes.any { id.startsWith(it) }
            }
    }
}

internal data class PlaybackAvailability(
    val addons: List<Addon> = emptyList(),
    val scrapers: List<ScraperInfo> = emptyList(),
    val isLoaded: Boolean = false,
    private val cachedMeta: (String, String) -> Meta? = { _, _ -> null },
    private val serverStreams: (String, String) -> Boolean = { _, _ -> false }
) {
    fun canStream(
        type: String,
        videoId: String,
        contentId: String = videoId,
        video: Video? = null,
        fallbackVideoId: String? = null
    ): Boolean = canStreamId(type, videoId, contentId, video) ||
        (!fallbackVideoId.isNullOrBlank() && fallbackVideoId != videoId &&
            canStreamId(type, fallbackVideoId, contentId, video))

    private fun canStreamId(
        type: String,
        videoId: String,
        contentId: String,
        video: Video?
    ): Boolean = video?.takeIf { it.id == videoId }?.streams?.isNotEmpty() == true ||
        cachedMeta(type, contentId)?.videos?.any { it.id == videoId && it.streams.isNotEmpty() } == true ||
        addons.any { it.enabled && it.supportsStreamResource(type, videoId) } ||
        scrapers.any { it.enabled && it.supportsType(type) } ||
        serverStreams(type, videoId)
}
