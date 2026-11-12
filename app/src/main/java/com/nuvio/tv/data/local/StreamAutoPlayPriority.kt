@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.nuvio.tv.data.local

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** M65: which axis is the outer order of the priority list. */
@Serializable
enum class StreamAutoPlayPriority {
    CALIDAD,
    IDIOMA
}

/**
 * One expanded row of the Priority list, used by the selector. A null/blank add-on means "any add-on".
 * [cachedOnly] keeps only sources ready on the debrid service (no download); [audioLanguage] keeps
 * only sources that match that ISO 639-1 language. Null means any language.
 */
@Serializable(with = StreamAutoPlayPriorityRuleSerializer::class)
data class StreamAutoPlayPriorityRule(
    val addonName: String? = null,
    val quality: StreamAutoPlayQuality = StreamAutoPlayQuality.UHD_2160,
    val cachedOnly: Boolean = false,
    val audioLanguage: String? = null
)

@Serializable
enum class StreamAutoPlayQuality {
    UHD_2160,
    FHD_1080,
    OTHER
}

/**
 * What the user edits: an ordered add-on list, an ordered quality list, the ordered audio languages
 * and two filters. The add-on is always the innermost loop. [priority] decides whether quality or
 * language is the outer loop. Empty add-ons mean "any add-on"; no languages means "any language".
 */
@Serializable(with = StreamAutoPlayPriorityConfigSerializer::class)
data class StreamAutoPlayPriorityConfig(
    val addons: List<String> = emptyList(),
    val qualities: List<StreamAutoPlayQuality> = emptyList(),
    val cachedOnly: Boolean = false,
    val audioLanguages: List<String> = emptyList(),
    val priority: StreamAutoPlayPriority = StreamAutoPlayPriority.CALIDAD
) {
    fun toRules(): List<StreamAutoPlayPriorityRule> {
        val addonRows: List<String?> = addons
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf(null) }
        val languageRows: List<String?> = normalizeAudioLanguageOrder(audioLanguages)
            .ifEmpty { listOf(null) }
        val qualityRows = qualities.distinct()
        return when (priority) {
            StreamAutoPlayPriority.CALIDAD -> qualityRows.flatMap { quality ->
                languageRows.flatMap { language ->
                    addonRows.map { addon -> StreamAutoPlayPriorityRule(addon, quality, cachedOnly, language) }
                }
            }
            StreamAutoPlayPriority.IDIOMA -> languageRows.flatMap { language ->
                qualityRows.flatMap { quality ->
                    addonRows.map { addon -> StreamAutoPlayPriorityRule(addon, quality, cachedOnly, language) }
                }
            }
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
                audioLanguages = normalizeAudioLanguageList(rules.mapNotNull { it.audioLanguage }),
                priority = StreamAutoPlayPriority.CALIDAD
            )
    }
}

/** M65: normalizes codes and removes duplicates without changing the user's order. */
internal fun normalizeAudioLanguageOrder(codes: List<String>): List<String> =
    codes.mapNotNull { normalizeAudioLanguageCode(it) }.distinct()

/** M65: the app language order is the table order; unknown codes stay at the end, sorted. */
internal fun normalizeAudioLanguageList(codes: List<String>): List<String> {
    val normalized = normalizeAudioLanguageOrder(codes)
    val knownCodes = AUDIO_LANGUAGES.map { it.code }
    return knownCodes.filter { it in normalized } + normalized.filter { it !in knownCodes }.sorted()
}

private fun normalizeAudioLanguageCode(code: String?): String? =
    code?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it == "iw") "he" else it }

/**
 * M65: the legacy schema had `spanishOnly`; M59 had a set of languages in `audioLanguages`. The
 * storage surrogate reads both, writes only the ordered list and the priority mode.
 */
@Serializable
private data class StreamAutoPlayPriorityRuleStorage(
    val addonName: String? = null,
    val quality: StreamAutoPlayQuality = StreamAutoPlayQuality.UHD_2160,
    val cachedOnly: Boolean = false,
    val audioLanguage: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val audioLanguages: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val spanishOnly: Boolean? = null
)

private object StreamAutoPlayPriorityRuleSerializer : KSerializer<StreamAutoPlayPriorityRule> {
    private val storage = StreamAutoPlayPriorityRuleStorage.serializer()
    override val descriptor: SerialDescriptor = storage.descriptor

    override fun deserialize(decoder: Decoder): StreamAutoPlayPriorityRule {
        val value = decoder.decodeSerializableValue(storage)
        val migrated = normalizeAudioLanguageCode(value.audioLanguage)
            ?: if (value.audioLanguages != null) {
                // An explicit empty set means "any"; only an absent set uses the legacy flag.
                normalizeAudioLanguageList(value.audioLanguages).firstOrNull()
            } else if (value.spanishOnly == true) "es" else null
        return StreamAutoPlayPriorityRule(
            addonName = value.addonName,
            quality = value.quality,
            cachedOnly = value.cachedOnly,
            audioLanguage = migrated
        )
    }

    override fun serialize(encoder: Encoder, value: StreamAutoPlayPriorityRule) {
        encoder.encodeSerializableValue(
            storage,
            StreamAutoPlayPriorityRuleStorage(
                addonName = value.addonName,
                quality = value.quality,
                cachedOnly = value.cachedOnly,
                audioLanguage = normalizeAudioLanguageCode(value.audioLanguage)
            )
        )
    }
}

@Serializable
private data class StreamAutoPlayPriorityConfigStorage(
    val addons: List<String> = emptyList(),
    val qualities: List<StreamAutoPlayQuality> = emptyList(),
    val cachedOnly: Boolean = false,
    val audioLanguages: List<String>? = null,
    val priority: StreamAutoPlayPriority? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val spanishOnly: Boolean? = null
)

private object StreamAutoPlayPriorityConfigSerializer : KSerializer<StreamAutoPlayPriorityConfig> {
    private val storage = StreamAutoPlayPriorityConfigStorage.serializer()
    override val descriptor: SerialDescriptor = storage.descriptor

    override fun deserialize(decoder: Decoder): StreamAutoPlayPriorityConfig {
        val value = decoder.decodeSerializableValue(storage)
        // M65: new JSON always carries priority; its absence means the old M59 set, whose order was
        // not meaningful and is normalized to the app language order.
        val languages = value.audioLanguages ?: if (value.spanishOnly == true) listOf("es") else emptyList()
        val migratedLanguages = if (value.priority != null) {
            normalizeAudioLanguageOrder(languages)
        } else {
            normalizeAudioLanguageList(languages)
        }
        return StreamAutoPlayPriorityConfig(
            addons = value.addons,
            qualities = value.qualities,
            cachedOnly = value.cachedOnly,
            audioLanguages = migratedLanguages,
            priority = value.priority ?: StreamAutoPlayPriority.CALIDAD
        )
    }

    override fun serialize(encoder: Encoder, value: StreamAutoPlayPriorityConfig) {
        encoder.encodeSerializableValue(
            storage,
            StreamAutoPlayPriorityConfigStorage(
                addons = value.addons,
                qualities = value.qualities,
                cachedOnly = value.cachedOnly,
                audioLanguages = normalizeAudioLanguageOrder(value.audioLanguages),
                priority = value.priority
            )
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
                streamAutoPlayPriorityJson.parseToJsonElement(value).jsonArray.flatMap { element ->
                    val row = streamAutoPlayPriorityJson
                        .decodeFromJsonElement<StreamAutoPlayPriorityRuleStorage>(element)
                    val languages: List<String?> = when {
                        // The new singleton (including explicit null) wins over legacy fields.
                        "audioLanguage" in element.jsonObject -> listOf(normalizeAudioLanguageCode(row.audioLanguage))
                        row.audioLanguages != null -> normalizeAudioLanguageList(row.audioLanguages)
                            .ifEmpty { listOf(null) }
                        else -> listOf(if (row.spanishOnly == true) "es" else null)
                    }
                    // M59 rules accepted any selected language. Expand all of them at the same
                    // rule position so the list-only DataStore fallback keeps those alternatives.
                    languages.map { language ->
                        StreamAutoPlayPriorityRule(row.addonName, row.quality, row.cachedOnly, language)
                    }
                }
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
