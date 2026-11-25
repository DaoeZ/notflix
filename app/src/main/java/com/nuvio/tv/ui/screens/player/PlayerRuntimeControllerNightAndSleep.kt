package com.nuvio.tv.ui.screens.player

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NIGHT_MODE_PREFS = "notflix_night_mode_prefs"
private const val NIGHT_MODE_KEY = "night_mode_enabled"

internal fun PlayerRuntimeController.loadNightModePreference() {
    val enabled = runCatching {
        context.getSharedPreferences(NIGHT_MODE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(NIGHT_MODE_KEY, false)
    }.getOrDefault(false)
    nightModeAudioProcessor.setNightModeEnabled(enabled)
    _uiState.update { it.copy(nightModeEnabled = enabled) }
}

internal fun PlayerRuntimeController.toggleNightMode() {
    val newEnabled = !_uiState.value.nightModeEnabled
    nightModeAudioProcessor.setNightModeEnabled(newEnabled)
    runCatching {
        context.getSharedPreferences(NIGHT_MODE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(NIGHT_MODE_KEY, newEnabled)
            .apply()
    }
    applyNightModeToActiveEngine(newEnabled)
    _uiState.update { it.copy(nightModeEnabled = newEnabled) }
}

internal fun PlayerRuntimeController.applyNightModeToActiveEngine(enabled: Boolean = _uiState.value.nightModeEnabled) {
    nightModeAudioProcessor.setNightModeEnabled(enabled)
    if (isUsingMpvEngine()) {
        mpvView?.applyNightModeDrc(enabled)
    } else {
        playbackSpeedAwareAudioSink?.notifyAudioProcessingRequirementChanged()
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().build()
        }
    }
}

internal fun PlayerRuntimeController.showSleepTimerDialog() {
    hideControlsJob?.cancel()
    _uiState.update {
        it.copy(
            showSleepTimerDialog = true,
            showMoreDialog = false,
            showSpeedDialog = false,
            showAudioOverlay = false,
            showSubtitleOverlay = false,
            showSubtitleStylePanel = false,
            showSubtitleTimingDialog = false,
            showSubtitleDelayOverlay = false,
            showControls = true
        )
    }
}

internal fun PlayerRuntimeController.dismissSleepTimerDialog() {
    _uiState.update { it.copy(showSleepTimerDialog = false) }
    scheduleHideControls()
}

internal fun PlayerRuntimeController.cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    restoreSleepTimerVolume()
    _uiState.update {
        it.copy(
            sleepTimerMinutes = null,
            sleepTimerEndOfEpisode = false,
            sleepTimerRemainingSec = null
        )
    }
}

internal fun PlayerRuntimeController.restoreSleepTimerVolume() {
    val orig = sleepTimerOriginalVolume ?: return
    sleepTimerOriginalVolume = null
    if (isUsingMpvEngine()) {
        mpvView?.applyAudioAmplificationDb(_uiState.value.audioAmplificationDb)
    } else {
        _exoPlayer?.let { player -> runCatching { player.volume = orig } }
    }
}

internal fun PlayerRuntimeController.setSleepTimer(minutes: Int?, endOfEpisode: Boolean = false) {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    restoreSleepTimerVolume()

    if (endOfEpisode) {
        _uiState.update {
            it.copy(
                sleepTimerMinutes = null,
                sleepTimerEndOfEpisode = true,
                sleepTimerRemainingSec = null,
                showSleepTimerDialog = false
            )
        }
        scheduleHideControls()
        return
    }

    if (minutes == null || minutes <= 0) {
        _uiState.update {
            it.copy(
                sleepTimerMinutes = null,
                sleepTimerEndOfEpisode = false,
                sleepTimerRemainingSec = null,
                showSleepTimerDialog = false
            )
        }
        scheduleHideControls()
        return
    }

    val totalSec = minutes * 60
    _uiState.update {
        it.copy(
            sleepTimerMinutes = minutes,
            sleepTimerEndOfEpisode = false,
            sleepTimerRemainingSec = totalSec,
            showSleepTimerDialog = false
        )
    }
    scheduleHideControls()

    sleepTimerJob = scope.launch {
        var remaining = totalSec
        while (remaining > 0) {
            delay(1000L)
            remaining -= 1
            if (remaining in 1..10) {
                if (sleepTimerOriginalVolume == null) {
                    sleepTimerOriginalVolume = _exoPlayer?.volume ?: 1f
                }
                val fraction = remaining / 10f
                if (isUsingMpvEngine()) {
                    mpvView?.setVolumeFraction(fraction, _uiState.value.audioAmplificationDb)
                } else {
                    val base = sleepTimerOriginalVolume ?: 1f
                    _exoPlayer?.let { p -> runCatching { p.volume = (base * fraction).coerceIn(0f, 1f) } }
                }
            }
            _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
        }
        // Sleep timer expired: pause playback smoothly and restore volume for next session.
        userPausedManually = true
        if (isUsingMpvEngine()) {
            mpvView?.setPaused(true)
        } else {
            _exoPlayer?.let { p ->
                runCatching {
                    p.playWhenReady = false
                    p.pause()
                }
            }
        }
        restoreSleepTimerVolume()
        _uiState.update {
            it.copy(
                sleepTimerMinutes = null,
                sleepTimerEndOfEpisode = false,
                sleepTimerRemainingSec = null,
                showControls = true
            )
        }
    }
}
