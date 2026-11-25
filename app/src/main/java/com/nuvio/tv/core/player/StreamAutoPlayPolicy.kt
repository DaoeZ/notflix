package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode

object StreamAutoPlayPolicy {
    // All add-ons can finish early. Priority mode never uses an instant timeout on a partial batch.
    internal fun priorityTimeoutMs(timeoutSeconds: Int): Long =
        if (timeoutSeconds == com.nuvio.tv.data.local.PlayerSettings.STREAM_AUTOPLAY_TIMEOUT_UNLIMITED) {
            60_000L
        } else {
            timeoutSeconds.coerceAtLeast(12) * 1_000L
        }

    fun isEffectivelyEnabled(playerSettings: PlayerSettings): Boolean {
        if (playerSettings.streamReuseLastLinkEnabled) return true
        if (playerSettings.streamAutoPlayReuseBingeGroup &&
            playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode) return true

        return when (playerSettings.streamAutoPlayMode) {
            StreamAutoPlayMode.MANUAL -> false
            StreamAutoPlayMode.FIRST_STREAM -> true
            StreamAutoPlayMode.REGEX_MATCH -> isRegexSelectionConfigured(playerSettings.streamAutoPlayRegex)
            StreamAutoPlayMode.PRIORITY_LIST -> playerSettings.streamAutoPlayPriorityList.isNotEmpty()
        }
    }

    fun isRegexSelectionConfigured(regexPattern: String): Boolean {
        val pattern = regexPattern.trim()
        if (pattern.isEmpty() || !pattern.any { it.isLetterOrDigit() }) return false
        return runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
    }
}
