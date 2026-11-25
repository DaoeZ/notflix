@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.domain.model.NotflixPlayerAction
import com.nuvio.tv.domain.model.NotflixPlayerActions
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoice
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoices
import com.nuvio.tv.ui.appearance.notflix.NotflixSidePanel
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames

/** Ephemeral focus bridge owned above the controls' AnimatedVisibility. No playback state. */
internal class NotflixPlayerFocus {
    val returnRequester = FocusRequester()
    var returnAction: NotflixPlayerAction? = null
    var moreAction: String? = null
    var focusedRowAction: NotflixPlayerAction? = null
    var rowOwnsFocus = false
    fun reset() { returnAction = null; moreAction = null; rowOwnsFocus = false }
}

internal fun PlayerUiState.notflixActionPanelOpen(): Boolean = showMoreDialog || showAudioOverlay ||
    showSubtitleOverlay || showSpeedDialog || showSleepTimerDialog || showEpisodesPanel || showSourcesPanel || showChaptersPanel ||
    showPartyPanel || showStreamInfoOverlay || showSubtitleStylePanel || showSubtitleTimingDialog || showSubtitleDelayOverlay

internal fun notflixAvailablePlayerActions(ui: PlayerUiState, live: Boolean, party: Boolean, external: Boolean): List<NotflixPlayerAction> =
    NotflixPlayerActions.configurable.filter { action -> when (action) {
        NotflixPlayerAction.AUDIO_SUBTITLES -> ui.audioTracks.isNotEmpty() || ui.serverAudioTracks.isNotEmpty() ||
            ui.subtitleTracks.isNotEmpty() || ui.serverSubtitleTracks.isNotEmpty() || ui.addonSubtitles.isNotEmpty()
        NotflixPlayerAction.EPISODES -> ui.currentSeason != null && ui.currentEpisode != null
        NotflixPlayerAction.NEXT_EPISODE -> ui.nextEpisode?.hasAired == true &&
            (ui.postPlayMode as? PostPlayMode.AutoPlay)?.let { !it.searching && it.countdownSec == null } != false
        NotflixPlayerAction.CHAPTERS -> !live && ui.chapters.isNotEmpty()
        NotflixPlayerAction.SKIP_SEGMENT -> ui.activeSkipInterval != null && !ui.skipIntervalDismissed
        NotflixPlayerAction.SHARPEN -> ui.internalPlayerEngine == InternalPlayerEngine.MVP_PLAYER && ui.notflixSharpenAvailable
        NotflixPlayerAction.PARTY -> party
        NotflixPlayerAction.EXTERNAL -> external
        NotflixPlayerAction.REPORT -> ui.playbackIssueReportsEnabled
        NotflixPlayerAction.MORE -> false
        else -> true
    } }

@Composable
internal fun notflixPlayerActionLabel(action: NotflixPlayerAction, ui: PlayerUiState? = null): String = when (action) {
    NotflixPlayerAction.AUDIO_SUBTITLES -> stringResource(R.string.notflix_player_audio_subtitles)
    NotflixPlayerAction.EPISODES -> stringResource(R.string.cd_episodes)
    NotflixPlayerAction.NEXT_EPISODE -> stringResource(R.string.next_episode_label)
    NotflixPlayerAction.SPEED -> stringResource(R.string.notflix_player_speed_value, formatSpeedLabel(ui?.playbackSpeed ?: 1f).removeSuffix("x"))
    NotflixPlayerAction.NIGHT_MODE -> stringResource(if (ui?.nightModeEnabled == true) R.string.night_mode_on else R.string.night_mode_label)
    NotflixPlayerAction.SLEEP_TIMER -> when {
        ui?.sleepTimerEndOfEpisode == true -> stringResource(R.string.sleep_timer_end_episode)
        ui?.sleepTimerRemainingSec != null -> stringResource(R.string.sleep_timer_remaining, ((ui.sleepTimerRemainingSec + 59) / 60).coerceAtLeast(1))
        else -> stringResource(R.string.sleep_timer_label)
    }
    NotflixPlayerAction.SOURCES -> stringResource(R.string.cd_sources)
    NotflixPlayerAction.ASPECT -> stringResource(R.string.cd_aspect_ratio)
    NotflixPlayerAction.STREAM_INFO -> stringResource(R.string.cd_stream_info)
    NotflixPlayerAction.CHAPTERS -> stringResource(R.string.player_chapters)
    NotflixPlayerAction.SKIP_SEGMENT -> if (ui?.activeSkipInterval != null)
        getSkipLabel(ui.activeSkipInterval.type, ui.activeSkipTargetsPostCredits) else stringResource(R.string.skip_intro)
    NotflixPlayerAction.SHARPEN -> stringResource(if (ui?.notflixSharpenEnabled == true) R.string.cd_notflix_sharpen_on else R.string.cd_notflix_sharpen_off)
    NotflixPlayerAction.PARTY -> stringResource(R.string.party_title)
    NotflixPlayerAction.ENGINE -> stringResource(R.string.cd_switch_player_engine)
    NotflixPlayerAction.EXTERNAL -> stringResource(R.string.cd_open_external_player)
    NotflixPlayerAction.REPORT -> stringResource(R.string.player_report_issue)
    NotflixPlayerAction.MORE -> stringResource(R.string.notflix_player_more)
}

private fun actionIcon(action: NotflixPlayerAction): ImageVector = when (action) {
    NotflixPlayerAction.AUDIO_SUBTITLES -> Icons.Outlined.Subtitles
    NotflixPlayerAction.EPISODES -> Icons.AutoMirrored.Filled.List
    NotflixPlayerAction.NEXT_EPISODE -> Icons.Outlined.SkipNext
    NotflixPlayerAction.SPEED -> Icons.Outlined.Speed
    NotflixPlayerAction.NIGHT_MODE -> Icons.Outlined.NightsStay
    NotflixPlayerAction.SLEEP_TIMER -> Icons.Outlined.Bedtime
    NotflixPlayerAction.SOURCES, NotflixPlayerAction.ENGINE -> Icons.Outlined.SwapHoriz
    NotflixPlayerAction.ASPECT -> Icons.Outlined.AspectRatio
    NotflixPlayerAction.STREAM_INFO -> Icons.Outlined.Info
    NotflixPlayerAction.CHAPTERS -> Icons.AutoMirrored.Filled.Toc
    NotflixPlayerAction.SKIP_SEGMENT -> Icons.Outlined.FastForward
    NotflixPlayerAction.SHARPEN -> Icons.Outlined.AutoAwesome
    NotflixPlayerAction.PARTY -> Icons.Outlined.Groups
    NotflixPlayerAction.EXTERNAL -> Icons.AutoMirrored.Filled.OpenInNew
    NotflixPlayerAction.REPORT -> Icons.Outlined.BugReport
    NotflixPlayerAction.MORE -> Icons.Outlined.MoreHoriz
}

@Composable
internal fun NotflixPlayerActionRow(
    ui: PlayerUiState, live: Boolean, party: Boolean, external: Boolean, partyActive: Boolean,
    focus: NotflixPlayerFocus, firstRequester: FocusRequester, upRequester: FocusRequester,
    onResetTimer: () -> Unit, onHide: () -> Unit, onToggleMore: () -> Unit,
    onAction: (NotflixPlayerAction) -> Unit, reportCodeVisible: Boolean
) {
    val available = notflixAvailablePlayerActions(ui, live, party, external)
    val labels = NotflixPlayerAction.entries.associateWith { notflixPlayerActionLabel(it, ui) }
    val style = TextStyle(fontFamily = Spec.BodyFont, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val widths = remember(labels, style, density, measurer) { labels.mapValues { (_, label) ->
        measurer.measure(AnnotatedString(label), style).size.width / density.density + 48f
    } }
    val requesters = remember { NotflixPlayerAction.entries.associateWith { FocusRequester() } }
    val enabled: (NotflixPlayerAction) -> Boolean = { action -> action != NotflixPlayerAction.REPORT ||
        ui.playbackIssueReportStatus !in listOf(PlaybackIssueReportStatus.Sending, PlaybackIssueReportStatus.Sent) }
    val active: (NotflixPlayerAction) -> Boolean = { action ->
        (action == NotflixPlayerAction.SHARPEN && ui.notflixSharpenEnabled) ||
            (action == NotflixPlayerAction.NIGHT_MODE && ui.nightModeEnabled) ||
            (action == NotflixPlayerAction.SLEEP_TIMER && (ui.sleepTimerMinutes != null || ui.sleepTimerEndOfEpisode)) ||
            (action == NotflixPlayerAction.PARTY && partyActive)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout = remember(available, ui.notflixPlayerButtons, maxWidth, widths) {
            NotflixPlayerActions.layout(available, ui.notflixPlayerButtons, maxWidth.value, widths, 14f)
        }
        val returning = focus.returnAction?.takeIf { it in layout.row && enabled(it) } ?: NotflixPlayerAction.MORE
        val firstEnabled = layout.row.first { enabled(it) }
        // Capture ownership before removing a focused node: Compose may move focus during applyChanges.
        val lostAction = focus.rowOwnsFocus &&
            (focus.focusedRowAction !in layout.row || focus.focusedRowAction?.let { !enabled(it) } == true)
        LaunchedEffect(layout.row, ui.playbackIssueReportStatus, ui.notflixActionPanelOpen(), lostAction) {
            if (!ui.notflixActionPanelOpen() && lostAction) {
                requesters[NotflixPlayerAction.MORE]?.requestFocusAfterFrames(0)
            }
        }
        Row(Modifier.fillMaxWidth().focusGroup().testTag("notflix-player-actions"),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            layout.row.forEachIndexed { index, action -> key(action) {
                NotflixPlayerActionButton(action, labels.getValue(action), widths.getValue(action), style,
                    Modifier.focusRequester(requesters.getValue(action))
                        .then(if (action == firstEnabled) Modifier.focusRequester(firstRequester) else Modifier)
                        .then(if (action == returning) Modifier.focusRequester(focus.returnRequester) else Modifier)
                        .focusProperties { up = upRequester; left = layout.row.take(index).lastOrNull { enabled(it) }?.let(requesters::get) ?: FocusRequester.Cancel
                            right = layout.row.drop(index + 1).firstOrNull { enabled(it) }?.let(requesters::get) ?: FocusRequester.Cancel; down = FocusRequester.Cancel },
                    enabled(action), active(action), onFocused = { focus.rowOwnsFocus = true; focus.focusedRowAction = action; onResetTimer() },
                    onClick = {
                        focus.returnAction = action
                        if (action !in listOf(NotflixPlayerAction.ASPECT, NotflixPlayerAction.SHARPEN,
                                NotflixPlayerAction.NIGHT_MODE, NotflixPlayerAction.REPORT)) focus.rowOwnsFocus = false
                        if (action == NotflixPlayerAction.MORE) { focus.rowOwnsFocus = false; onToggleMore() } else onAction(action)
                    }, onDown = onHide)
            } }
        }
        if (ui.showMoreDialog) {
            NotflixSidePanel(title = labels.getValue(NotflixPlayerAction.MORE), onDismiss = {
                focus.returnAction = NotflixPlayerAction.MORE; onToggleMore()
            }, width = 440.dp) {
                NotflixPanelChoices(modifier = Modifier.testTag("notflix-player-more-actions"), choices = layout.more.map { action -> NotflixPanelChoice(action.id,
                    labels.getValue(action), selected = active(action),
                    enabled = enabled(action), testTag = "notflix-player-more-${action.id}") }, initialFocusKey = focus.moreAction,
                    onSelect = { choice ->
                        val action = layout.more.firstOrNull { it.id == choice.key && enabled(it) } ?: return@NotflixPanelChoices
                        focus.returnAction = NotflixPlayerAction.MORE; focus.moreAction = action.id; focus.rowOwnsFocus = false
                        if (action !in listOf(NotflixPlayerAction.ASPECT, NotflixPlayerAction.SHARPEN,
                                NotflixPlayerAction.NIGHT_MODE, NotflixPlayerAction.ENGINE, NotflixPlayerAction.REPORT)) onToggleMore()
                        onAction(action)
                    })
                if (reportCodeVisible && !ui.playbackIssueReportId.isNullOrBlank())
                    Text(ui.playbackIssueReportId, color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 13.sp)
            }
        }
    }
    }
}

@Composable
internal fun NotflixAudioSubtitleTabs(audioSelected: Boolean, onAudio: () -> Unit, onSubtitles: () -> Unit) {
    val audioRequester = remember { FocusRequester() }
    val subtitleRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        (if (audioSelected) audioRequester else subtitleRequester).requestFocusAfterFrames(2)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(true to stringResource(R.string.audio_dialog_title), false to stringResource(R.string.subtitle_dialog_title))
            .forEach { (audio, label) ->
                var focused by remember { mutableStateOf(false) }
                Text(label, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 18.sp,
                    modifier = Modifier.focusRequester(if (audio) audioRequester else subtitleRequester)
                        .focusProperties { left = if (audio) FocusRequester.Cancel else audioRequester
                            right = if (audio) subtitleRequester else FocusRequester.Cancel; up = FocusRequester.Cancel }
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { selected = audioSelected == audio }
                        .drawWithContent { drawContent(); if (focused || audioSelected == audio)
                            drawLine(if (focused) Spec.Text else Spec.Accent,
                                androidx.compose.ui.geometry.Offset(0f, size.height),
                                androidx.compose.ui.geometry.Offset(size.width, size.height), 2.dp.toPx()) }
                        .clickable(role = Role.Tab, onClick = if (audio) onAudio else onSubtitles)
                        .padding(vertical = 8.dp).testTag(if (audio) "notflix-player-tab-audio" else "notflix-player-tab-subtitles"))
            }
    }
}

@Composable
private fun NotflixPlayerActionButton(action: NotflixPlayerAction, label: String, width: Float, style: TextStyle,
    modifier: Modifier, enabled: Boolean, active: Boolean, onFocused: () -> Unit, onClick: () -> Unit, onDown: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.width(width.dp).height(42.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .onPreviewKeyEventCompatDown(onDown)
        .drawWithContent { drawContent(); if (active) drawCircle(Spec.Accent, 2.dp.toPx(),
            androidx.compose.ui.geometry.Offset(size.width / 2f, size.height - 6.dp.toPx()))
            if (focused) drawLine(Spec.Text,
            androidx.compose.ui.geometry.Offset(8.dp.toPx(), size.height - 1.dp.toPx()),
            androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), size.height - 1.dp.toPx()), 2.dp.toPx()) }
        .semantics { contentDescription = label; selected = active }.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp).testTag("notflix-player-action-${action.id}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val color = if (enabled) Spec.Text else Spec.Secondary
        Icon(actionIcon(action), null, Modifier.size(22.dp), tint = color)
        Text(label, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

private fun Modifier.onPreviewKeyEventCompatDown(onDown: () -> Unit): Modifier =
    this.then(Modifier.onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) { onDown(); true } else false
    })
