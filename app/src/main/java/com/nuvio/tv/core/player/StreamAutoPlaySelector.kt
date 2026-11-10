package com.nuvio.tv.core.player

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlayPriorityRule
import com.nuvio.tv.data.local.StreamAutoPlayQuality
import com.nuvio.tv.data.local.StreamAutoPlaySource
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
        priorityList: List<StreamAutoPlayPriorityRule> = emptyList()
    ): Stream? {
        if (streams.isEmpty()) return null

        val effectiveSource = if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY
        } else {
            source
        }

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
                    Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
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
            StreamAutoPlayMode.PRIORITY_LIST -> selectPriorityListStream(candidateStreams, priorityList)

        }
    }

    /**
     * The first playable stream that satisfies each rule, walking the user's list in order.
     * A null/blank add-on means any add-on. No match returns null, which is the manual result.
     */
    internal fun selectPriorityListStream(
        streams: List<Stream>,
        rules: List<StreamAutoPlayPriorityRule>
    ): Stream? {
        if (streams.isEmpty() || rules.isEmpty()) return null
        for (rule in rules) {
            val addon = rule.addonName?.trim().orEmpty()
            val match = streams.firstOrNull { stream ->
                isPlayable(stream) &&
                    (addon.isEmpty() || stream.addonName == addon) &&
                    streamPriorityQuality(stream) == rule.quality &&
                    (!rule.cachedOnly || isCachedForPriority(stream)) &&
                    (!rule.spanishOnly || isSpanishForPriority(stream))
            }
            if (match != null) return match
        }
        return null
    }

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
     * Heuristic: the add-on names the language in the name, title or description. Streams that say
     * nothing about language are not treated as Spanish.
     */
    internal fun isSpanishForPriority(stream: Stream): Boolean {
        val raw = listOfNotNull(stream.name, stream.title, stream.description).joinToString("\n")
        // Spanish subtitles are not Spanish audio (promoter 09-11): whatever follows a subtitle marker
        // on its line is ignored, and a VOSE release counts only when it also names a Spanish dub.
        val text = SUBTITLE_SEGMENT.replace(raw, " ")
        if (VOSE_PATTERN.containsMatchIn(raw)) return SPANISH_DUB_PATTERN.containsMatchIn(text)
        return SPANISH_WORD_PATTERN.containsMatchIn(text) || SPANISH_FLAGS.any { text.contains(it) }
    }

    private val VOSE_PATTERN = Regex("""(?i)(?<!\p{L})(vose|v\.o\.s\.e\.?)(?!\p{L})""")
    private val SUBTITLE_SEGMENT =
        Regex("""(?i)((?<!\p{L})(subs?|subtitles?|subt[ií]tulos?|subtitulad[oa]s?|multi[\s.-]?subs?)(?!\p{L})|💬|📜|🔤)[^\n|]*""")

    private val SPANISH_DUB_PATTERN = Regex("""(?i)(?<!\p{L})(castellano|espa[nñ]ol|latino|spanish)(?!\p{L})""")
    private val SPANISH_WORD_PATTERN =
        Regex("""(?i)(?<!\p{L})(es|esp|spa|spanish|castellano|espa[nñ]ol|latino|lat)(?!\p{L})""")
    private val SPANISH_FLAGS = listOf("🇪🇸", "🇲🇽", "🇦🇷", "🇨🇱", "🇨🇴", "🇵🇪", "🇻🇪", "🇺🇾")

    /** 2160p/4K/UHD, then 1080p/FHD/Full HD; anything else (or unstated) is OTHER. */
    internal fun streamPriorityQuality(stream: Stream): StreamAutoPlayQuality {
        val text = "${stream.name.orEmpty()} ${stream.title.orEmpty()}"
        return when {
            UHD_QUALITY_PATTERN.containsMatchIn(text) -> StreamAutoPlayQuality.UHD_2160
            FHD_QUALITY_PATTERN.containsMatchIn(text) -> StreamAutoPlayQuality.FHD_1080
            else -> StreamAutoPlayQuality.OTHER
        }
    }

    private val UHD_QUALITY_PATTERN = Regex("""\b(2160p|4k|uhd|ultra\s*hd)\b""", RegexOption.IGNORE_CASE)
    private val FHD_QUALITY_PATTERN = Regex("""\b(1080p|fhd|fullhd|full\s*hd)\b""", RegexOption.IGNORE_CASE)
}
