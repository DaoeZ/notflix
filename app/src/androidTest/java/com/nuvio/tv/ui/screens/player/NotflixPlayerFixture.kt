package com.nuvio.tv.ui.screens.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import com.nuvio.tv.ui.appearance.*
import java.util.Locale

/** Synthetic timeline and callback sinks; no Hilt, player, engine, stores or media requests. */
internal class PlayerFixtureState {
    lateinit var input: android.view.View
    val play = FocusRequester(); val progress = FocusRequester(); val info = FocusRequester()
    var active by mutableStateOf(true)
    var visible by mutableStateOf(true)
    var spanish by mutableStateOf(false)
    var ui by mutableStateOf(PlayerUiState(title = "Synthetic film", releaseYear = "2024",
        isPlaying = true, isBuffering = false, currentStreamName = "Synthetic provider"))
    var timeline by mutableStateOf(PlaybackTimelineState(currentPosition = 30_000L, duration = 120_000L, bufferedPosition = 60_000L))
    val events = mutableListOf<String>()
    val preview = mutableListOf<Long>()
    var commits = 0; var timerResets = 0; var clearance = 0f; var progressClearance = 0f; var titleRight = -1f
    fun event(name: String) { events += name }
    fun toggle() { event("play"); ui = ui.copy(isPlaying = !ui.isPlaying) }
    fun hide() { event("hide"); visible = false }
}

@Composable
internal fun PlayerFixture(s: PlayerFixtureState) {
    s.input = LocalView.current
    val base = LocalContext.current
    val context = remember(s.spanish) {
        if (s.spanish) base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale("es")) }) else base
    }
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
        LocalNotflixPresentation provides NotflixPresentation(active = s.active, reduceHomeMotion = true)) {
        NotflixPresentationTheme(s.active) {
            Box(Modifier.fillMaxSize().background(Color(0xFF303039))) {
                if (s.visible) {
                    PlayerControlsContent(uiState = s.ui, playbackTimeline = s.timeline, playerBadges = emptyList(),
                        progressContent = { up, down, hide, reset, click ->
                            PlayerControlsProgressContent(s.timeline, s.ui.chapters, s.progress, up, down, hide, reset, click,
                                onSeekPreview = { s.preview += it }, onSeekCommit = { s.commits++ })
                        }, timeTextContent = { PlayerControlsTimeTextContent(s.timeline) },
                        playPauseFocusRequester = s.play, progressBarFocusRequester = s.progress,
                        streamInfoFocusRequester = s.info,
                        onPlayPause = s::toggle, onPlayNextEpisode = { s.event("next") },
                        onSeekForward = { s.event("forward") }, onSeekBackward = { s.event("backward") },
                        onSeekTo = { s.event("seekTo:$it") }, onShowChaptersPanel = { s.event("chapters") },
                        onShowEpisodesPanel = { s.event("episodes") }, onShowSourcesPanel = { s.event("sources") },
                        onShowPartyPanel = { s.event("party") }, partyActive = false,
                        onShowAudioDialog = { s.event("audio") }, onShowSubtitleDialog = { s.event("subtitle") },
                        onShowSpeedDialog = { s.event("speed") }, onToggleAspectRatio = { s.event("aspect") },
                        onSwitchPlayerEngine = { s.event("engine") }, onReportPlaybackIssue = { s.event("report") },
                        onToggleMoreActions = { s.event("more"); s.ui = s.ui.copy(showMoreDialog = !s.ui.showMoreDialog) },
                        onOpenInExternalPlayer = { s.event("external") }, onShowStreamInfo = { s.event("info") },
                        onResetHideTimer = { s.timerResets++ }, onHideControls = s::hide, onBack = { s.event("back") },
                        reportCodeVisible = false, onSubtitleClearanceChanged = { s.clearance = it },
                        onSubtitleTitleBoundsChanged = { p, r -> s.progressClearance = p; s.titleRight = r })
                }
            }
            LaunchedEffect(s.visible, s.active) { if (s.visible) s.play.requestFocus() }
        }
    }
}
