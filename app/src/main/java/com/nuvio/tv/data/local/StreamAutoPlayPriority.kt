package com.nuvio.tv.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One expanded row of the Priority list, used by the selector. A null/blank add-on means "any add-on".
 * [cachedOnly] keeps only sources ready on the debrid service (no download); [spanishOnly] keeps only
 * sources whose name or title says they are Spanish.
 */
@Serializable
data class StreamAutoPlayPriorityRule(
    val addonName: String? = null,
    val quality: StreamAutoPlayQuality = StreamAutoPlayQuality.UHD_2160,
    val cachedOnly: Boolean = false,
    val spanishOnly: Boolean = false
)

@Serializable
enum class StreamAutoPlayQuality {
    UHD_2160,
    FHD_1080,
    OTHER
}

/**
 * What the user edits: an ordered add-on list, an ordered quality list and two filters.
 * Quality is the outer order and add-on the inner one, so every add-on is tried at the first quality
 * before any add-on is tried at the next one. An empty add-on list means "any add-on".
 */
@Serializable
data class StreamAutoPlayPriorityConfig(
    val addons: List<String> = emptyList(),
    val qualities: List<StreamAutoPlayQuality> = emptyList(),
    val cachedOnly: Boolean = false,
    val spanishOnly: Boolean = false
) {
    fun toRules(): List<StreamAutoPlayPriorityRule> {
        val addonRows: List<String?> = addons
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf(null) }
        return qualities.distinct().flatMap { quality ->
            addonRows.map { addon -> StreamAutoPlayPriorityRule(addon, quality, cachedOnly, spanishOnly) }
        }
    }

    companion object {
        /** Quality order of a new list, from best to worst. */
        val DEFAULT_QUALITIES: List<StreamAutoPlayQuality> = listOf(
            StreamAutoPlayQuality.UHD_2160,
            StreamAutoPlayQuality.FHD_1080,
            StreamAutoPlayQuality.OTHER
        )

        /** Older lists were pairs of add-on and quality; their two lists and filters are kept. */
        fun fromRules(rules: List<StreamAutoPlayPriorityRule>): StreamAutoPlayPriorityConfig =
            StreamAutoPlayPriorityConfig(
                addons = rules.mapNotNull { it.addonName?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
                qualities = rules.map { it.quality }.distinct(),
                cachedOnly = rules.any { it.cachedOnly },
                spanishOnly = rules.any { it.spanishOnly }
            )
    }
}

private val streamAutoPlayPriorityJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun encodeStreamAutoPlayPriorityList(rules: List<StreamAutoPlayPriorityRule>): String =
    streamAutoPlayPriorityJson.encodeToString(rules)

internal fun decodeStreamAutoPlayPriorityList(raw: String?): List<StreamAutoPlayPriorityRule> =
    raw
        ?.takeIf { it.isNotBlank() }
        ?.let { value ->
            runCatching {
                streamAutoPlayPriorityJson.decodeFromString<List<StreamAutoPlayPriorityRule>>(value)
            }.getOrNull()
        }
        ?: emptyList()

internal fun encodeStreamAutoPlayPriorityConfig(config: StreamAutoPlayPriorityConfig): String =
    streamAutoPlayPriorityJson.encodeToString(config)

/** Null when nothing was saved yet or the stored value cannot be read. */
internal fun decodeStreamAutoPlayPriorityConfig(raw: String?): StreamAutoPlayPriorityConfig? =
    raw
        ?.takeIf { it.isNotBlank() }
        ?.let { value ->
            runCatching {
                streamAutoPlayPriorityJson.decodeFromString<StreamAutoPlayPriorityConfig>(value)
            }.getOrNull()
        }
