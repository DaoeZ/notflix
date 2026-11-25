package com.nuvio.tv.core.player

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.data.local.AUDIO_LANGUAGES
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlayPriorityRule
import com.nuvio.tv.data.local.StreamAutoPlayQuality
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.data.local.normalizeAudioLanguageCodes
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

object StreamAutoPlaySelector {
    fun orderAddonStreams(
        streams: List<AddonStreams>,
        installedOrder: List<String>,
        preferredNames: Set<String> = emptySet()
    ): List<AddonStreams> {
        if (streams.isEmpty()) return streams
        if (preferredNames.isNotEmpty()) {
            val (preferred, rest) = streams.partition { it.addonName in preferredNames }
            return preferred + orderAddonStreams(rest, installedOrder)
        }

        val addonRankByName = HashMap<String, Int>(installedOrder.size)
        installedOrder.forEachIndexed { index, addonName ->
            if (addonName !in addonRankByName) {
                addonRankByName[addonName] = index
            }
        }

        val (directDebridEntries, remainingEntries) = streams.partition {
            it.streams.any { stream -> stream.isDirectDebrid() }
        }
        if (installedOrder.isEmpty()) return directDebridEntries + remainingEntries
        val (addonEntries, pluginEntries) = remainingEntries.partition { it.addonName in addonRankByName }
        val orderedAddons = addonEntries.sortedBy { addonRankByName.getValue(it.addonName) }
        return directDebridEntries + orderedAddons + pluginEntries
    }

    private fun isPlayable(stream: Stream): Boolean {
        // External URL streams (e.g. error pages, web links) are not playable.
        if (stream.isExternal()) return false
        if (stream.serverTarget != null) return true
        // Streams with a direct URL are always playable regardless of debrid cache status.
        if (stream.getStreamUrl() != null) return true
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.UNKNOWN -> return false
            StreamDebridCacheState.CACHED,
            null -> Unit
        }
        return stream.isTorrent() ||
            stream.isDirectDebrid() ||
            // A YouTube id is resolved to a playable URL on the device when the stream is played.
            // Builds without in-app YouTube playback open the watch page instead, an external link.
            (AppFeaturePolicy.inAppTrailerPlaybackEnabled && stream.youTubeIdToResolve() != null)
    }



    fun selectAutoPlayStream(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        priorityList: List<StreamAutoPlayPriorityRule> = emptyList(),
        carryPriorityAudioLanguage: Boolean = false
    ): Stream? {
        if (streams.isEmpty()) return null

        val effectiveSource = if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY
        } else {
            source
        }

        // Binge group matching takes priority over mode and source filters — even in MANUAL mode,
        // a persisted binge group should auto-play without showing the picker.
        // Search ALL streams, not just candidateStreams, since binge group reuse should
        // not be limited by autoplay source/addon selection filters.
        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            val bingeGroupMatch = streams.firstOrNull { stream ->
                !stream.isExternal() &&
                    stream.behaviorHints?.bingeGroup == targetBingeGroup &&
                    isPlayable(stream)
            }
            if (bingeGroupMatch != null) return bingeGroupMatch
            // When bingeGroupOnly is set (MANUAL mode with only binge-group
            // preference enabled), don't fall back to a non-matching stream —
            // return null so the caller shows the stream picker instead.
            if (bingeGroupOnly) return null
        }

        if (bingeGroupOnly) return null

        val sourceScopedStreams = when (effectiveSource) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.serverTarget != null || it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        // In Priority mode the rules name the add-ons themselves, so the "allowed add-ons" filter would
        // silently drop them. The source filter above still applies.
        val priorityRulesDefineSources = mode == StreamAutoPlayMode.PRIORITY_LIST && priorityList.isNotEmpty()
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (stream.serverTarget != null || priorityRulesDefineSources) {
                true
            } else if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }
        if (candidateStreams.isEmpty()) return null

        if (mode == StreamAutoPlayMode.MANUAL) return null

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> null
            StreamAutoPlayMode.FIRST_STREAM -> candidateStreams.firstOrNull { isPlayable(it) }
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()
 
                // Try to compile the user regex
                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                if (userRegex == null) return null

                // Auto-extract exclusion patterns from negative lookaheads
                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    runCatching {
                        Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
                    }.getOrNull()
                } else null

                // 1. Build list of ALL regex‑matching streams
                val matchingStreams = candidateStreams.filter { stream ->
                    if (!isPlayable(stream)) return@filter false

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.title.orEmpty()).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(stream.getStreamUrl().orEmpty())
                        if (stream.isTorrent()) append(' ').append(stream.infoHash.orEmpty())
                    }

                    // Must match include pattern
                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    // Must NOT match exclusion pattern
                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }

                if (matchingStreams.isEmpty()) return null
                matchingStreams.firstOrNull { isPlayable(it) }
            }
            StreamAutoPlayMode.PRIORITY_LIST -> selectPriorityListStream(
                candidateStreams, priorityList, includePlaybackLanguage = carryPriorityAudioLanguage
            )

        }
    }

    /**
     * The first playable stream that satisfies each rule, walking the user's list in order.
     * A null/blank add-on means any add-on. No match returns null, which is the manual result.
     * M65: each stream is classified once (quality, cache and audio languages); the rules only read
     * that result, so dozens of language/quality rules do not re-parse the same titles.
     */
    internal fun selectPriorityListStream(
        streams: List<Stream>,
        rules: List<StreamAutoPlayPriorityRule>,
        includePlaybackLanguage: Boolean = false
    ): Stream? {
        if (streams.isEmpty() || rules.isEmpty()) return null
        val needCached = rules.any { it.cachedOnly }
        val onlyCached = needCached && rules.all { it.cachedOnly }
        val needAudio = rules.any { it.audioLanguage != null }
        val allowInferredEnglish = rules.none { it.audioLanguage != null && it.audioLanguage != "en" }
        val addonKeys = rules.mapNotNull { it.addonName?.takeIf(String::isNotBlank)?.let(::priorityAddonKey) }.toSet()
        val anyAddon = rules.any { it.addonName.isNullOrBlank() }
        val prepared = streams.asSequence().filter { stream ->
            (anyAddon || priorityAddonKey(stream.addonName) in addonKeys) &&
                isPlayable(stream)
        }.mapNotNull { stream ->
            val cached = needCached && isCachedForPriority(stream)
            if (onlyCached && !cached) return@mapNotNull null
            PreparedPriorityStream(
                stream = stream,
                addonKey = priorityAddonKey(stream.addonName),
                quality = streamPriorityQuality(stream),
                cached = cached,
                audio = if (needAudio) classifyPriorityAudio(stream)
                    else PriorityAudioClassification(emptySet(), englishFallback = false)
            )
        }.toList()
        for (rule in rules) {
            val addon = rule.addonName?.let(::priorityAddonKey).orEmpty()
            val match = prepared.firstOrNull { candidate ->
                (addon.isEmpty() || candidate.addonKey == addon) &&
                    candidate.quality == rule.quality &&
                    (!rule.cachedOnly || candidate.cached) &&
                    candidate.audio.matches(rule.audioLanguage, allowInferredEnglish)
            }
            if (match != null) {
                return if (includePlaybackLanguage && rule.audioLanguage in match.audio.languages) {
                    match.stream.copy(autoPlayAudioLanguage = rule.audioLanguage)
                } else match.stream
            }
        }
        return null
    }

    private data class PreparedPriorityStream(
        val stream: Stream,
        val addonKey: String,
        val quality: StreamAutoPlayQuality,
        val cached: Boolean,
        val audio: PriorityAudioClassification
    )

    // A partial search can only settle a rule once every earlier named source has
    // returned. Any-source rules have to wait for the full search or its deadline.
    internal fun settledPriorityRules(
        rules: List<StreamAutoPlayPriorityRule>,
        completedSources: Set<String>,
        installedAddonNames: List<String>
    ): List<StreamAutoPlayPriorityRule> {
        if (completedSources.isEmpty()) return emptyList()
        val installed = installedAddonNames.groupBy(::priorityAddonKey)
        return rules.takeWhile { rule ->
            val names = rule.addonName?.let { installed[priorityAddonKey(it)] }
            rule.audioLanguage != "en" && !names.isNullOrEmpty() && names.all { it in completedSources }
        }
    }

    private val ADDON_SEPARATOR = Regex("""[\s|]+""")

    private fun priorityAddonKey(name: String): String =
        name.trim().replace(ADDON_SEPARATOR, " ").trim().lowercase(java.util.Locale.ROOT)

    /**
     * Ready without a download: the debrid service already has it, or it is a direct link.
     * Torrents that would have to be downloaded to the service are not cached.
     */
    internal fun isCachedForPriority(stream: Stream): Boolean {
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CACHED -> return true
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.UNKNOWN -> return false
            null -> Unit
        }
        // Debrid add-ons return http links for downloads too ([RD download], ⬇, ⏳); trust their own
        // label before the "direct link means ready" rule (promoter 09-11).
        val label = listOfNotNull(stream.name, stream.title).joinToString(" ")
        if (NOT_CACHED_LABEL.containsMatchIn(label)) return false
        return stream.isDirectDebrid() || (!stream.isTorrent() && stream.getStreamUrl() != null)
    }

    private val NOT_CACHED_LABEL = Regex("""(?i)(\bdownload\b|\buncached\b|\bnot\s*cached\b|⬇|⏳)""")

    /**
     * Heuristic: the add-on names the audio language in the name, title or description. Empty
     * [codes] accepts any stream. The selector uses the shared [AUDIO_LANGUAGES] table. If English is selected, a stream with no audio-language
     * mark at all counts as English, except when it says MULTI/Dual without naming a language.
     */
    internal fun matchesAudioLanguages(stream: Stream, codes: Set<String>): Boolean {
        if (codes.isEmpty()) return true
        val classification = classifyPriorityAudio(stream)
        return normalizeAudioLanguageCodes(codes).any { classification.matches(it) }
    }

    /** M65: one classification per stream, reused by every rule. */
    internal data class PriorityAudioClassification(
        val languages: Set<String>,
        val englishFallback: Boolean
    ) {
        fun matches(code: String?, allowInferredEnglish: Boolean = true): Boolean {
            if (code == null) return true
            val normalized = if (code == "iw") "he" else code
            return normalized in languages || (allowInferredEnglish && normalized == "en" && englishFallback)
        }
    }

    internal fun classifyPriorityAudio(stream: Stream): PriorityAudioClassification {
        val raw = withoutStreamSites(
            listOfNotNull(stream.name, stream.title, stream.description, stream.priorityFilename()).joinToString("\n")
        )
        val text = withoutSubtitleLabels(raw)
        val shortCodes = shortStreamAudioCodes(
            text, withoutSubtitleLabels(withoutStreamSites(stream.description.orEmpty()))
        )
        val explicit = AUDIO_LANGUAGES.asSequence()
            .filter { language ->
                when (language.code) {
                    "es" -> if (VOSE_PATTERN.containsMatchIn(raw)) SPANISH_DUB_PATTERN.containsMatchIn(text)
                        else language.matches(text, raw, language.code in shortCodes)
                    "pl" -> {
                        val polishAudio = LEKTOR_AS_FILM_TITLE.replace(text, " ")
                        language.code in shortCodes || language.matches(polishAudio, polishAudio) ||
                            POLISH_AUDIO_MARK.containsMatchIn(polishAudio)
                    }
                    else -> language.matches(text, raw, language.code in shortCodes)
                }
            }
            .map { it.code }
            .toSet()
        return PriorityAudioClassification(
            languages = explicit,
            englishFallback = explicit.isEmpty() && !MULTI_DUAL_PATTERN.containsMatchIn(text)
        )
    }

    private fun Stream.priorityFilename(): String? =
        behaviorHints?.filename?.takeIf { it.isNotBlank() }
            ?: clientResolve?.filename?.takeIf { it.isNotBlank() }
            ?: clientResolve?.stream?.raw?.filename

    private val MULTI_DUAL_PATTERN =
        Regex("""(?i)(?<!\p{L})(multi|dual|ᴍᴜʟᴛɪ|ᴅᴜᴀʟ)(?!\p{L})""")

    private val LEKTOR_AS_FILM_TITLE = Regex("""(?im)^\s*lektor(?=[ ._(-]+(?:19|20)[0-9]{2}\b)""")
    private val POLISH_AUDIO_MARK = Regex(
        """(?iu)(?<![\p{L}\p{N}])(?:pl|pldub(?:bing)?|lektor(?:ski|pl)?|dubb(?:ing)?pl)(?![\p{L}\p{N}])"""
    )

    internal fun isSpanishForPriority(stream: Stream): Boolean =
        "es" in classifyPriorityAudio(stream).languages

    private val VOSE_PATTERN = Regex("""(?i)(?<!\p{L})(vose|v\.o\.s\.e\.?)(?!\p{L})""")
    private val SPANISH_DUB_PATTERN = Regex("""(?i)(?<!\p{L})(castellano|espa[nñ]ol|latino|spanish)(?!\p{L})""")

    /** 2160p/4K/UHD, then 1080p/FHD/Full HD; anything else (or unstated) is OTHER. */
    internal fun streamPriorityQuality(stream: Stream): StreamAutoPlayQuality {
        fun quality(text: String): StreamAutoPlayQuality? = when {
            UHD_QUALITY_PATTERN.containsMatchIn(text) -> StreamAutoPlayQuality.UHD_2160
            FHD_QUALITY_PATTERN.containsMatchIn(text) -> StreamAutoPlayQuality.FHD_1080
            else -> null
        }
        return quality("${stream.name.orEmpty()} ${stream.title.orEmpty()}")
            ?: quality(listOfNotNull(
                stream.quality, stream.clientResolve?.stream?.raw?.parsed?.resolution,
                stream.priorityFilename(), stream.description
            ).joinToString(" "))
            ?: StreamAutoPlayQuality.OTHER
    }

    private val UHD_QUALITY_PATTERN = Regex("""(?<![\p{L}\p{N}])(2160[pᴘ]|4k|uhd|ultra\s*hd)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val FHD_QUALITY_PATTERN = Regex("""(?<![\p{L}\p{N}])(1080[pᴘ]|fhd|fullhd|full\s*hd)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
}
