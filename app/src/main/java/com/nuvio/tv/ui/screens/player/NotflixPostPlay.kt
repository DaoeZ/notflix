@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/** Share of the "Next episode" button filled while the official countdown runs: full exactly when it reaches 1→0. */
internal fun notflixCountdownFill(countdownSec: Int?, totalSec: Int): Float =
    if (countdownSec == null || totalSec <= 0) 0f else ((totalSec - countdownSec + 1).toFloat() / totalSec).coerceIn(0f, 1f)

private val PostPlayButtonShape = RoundedCornerShape(4.dp)

/**
 * End-of-episode screen with the Notflix brand. Pure skin over the official PostPlayMode and events:
 * AutoPlay → «Ver créditos» (official dismiss) + «Siguiente episodio» (official play next, filled by the official countdown);
 * StillWatching → full-screen question with official continue/exit.
 */
@Composable
internal fun NotflixPostPlayOverlay(
    mode: PostPlayMode?,
    controlsVisible: Boolean,
    nextEpisodeFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester?,
    onPlayNext: () -> Unit,
    onWatchCredits: () -> Unit,
    onContinueStillWatching: () -> Unit,
    onDismissStillWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = mode != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(160)), modifier = modifier) {
        when (val current = mode) {
            is PostPlayMode.AutoPlay -> NotflixNextEpisode(current, controlsVisible, nextEpisodeFocusRequester,
                progressBarFocusRequester, onPlayNext, onWatchCredits)
            is PostPlayMode.StillWatching -> NotflixStillWatching(current, onContinueStillWatching, onDismissStillWatching)
            null -> Unit
        }
    }
}

@Composable
private fun NotflixNextEpisode(
    mode: PostPlayMode.AutoPlay, controlsVisible: Boolean, nextRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester?, onPlayNext: () -> Unit, onWatchCredits: () -> Unit
) {
    val episode = mode.nextEpisode
    val playable = episode.hasAired
    val creditsRequester = remember { FocusRequester() }
    // The official countdown only reports seconds left; its first value is the configured total (10 s by default).
    var total by remember(episode.videoId) { mutableIntStateOf(0) }
    mode.countdownSec?.let { if (it > total) total = it }
    val fill by animateFloatAsState(notflixCountdownFill(mode.countdownSec, total),
        tween(if (mode.countdownSec == null) 0 else 1000, easing = LinearEasing), label = "notflix-next-fill")
    var placed by remember(episode.videoId, controlsVisible) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("notflix-postplay-next")) {
        val status = when {
            !playable -> episode.unairedMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.next_episode_unaired)
            mode.searching -> stringResource(R.string.next_episode_finding_source)
            !mode.sourceName.isNullOrBlank() && mode.countdownSec != null ->
                stringResource(R.string.next_episode_playing_via, mode.sourceName, mode.countdownSec)
            else -> null
        }
        Text(listOfNotNull(nextEpisodeDisplayLabel(episode), status).joinToString("  ·  "), color = Spec.Text.copy(.85f),
            fontFamily = Spec.BodyFont, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp).testTag("notflix-postplay-next-label"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NotflixPostPlayButton(stringResource(R.string.notflix_postplay_watch_credits), primary = false, fill = 0f,
                enabled = true, requester = creditsRequester, tag = "notflix-postplay-credits",
                modifier = Modifier.focusProperties {
                    right = nextRequester
                    progressBarFocusRequester?.let { down = it }
                }, onClick = onWatchCredits)
            NotflixPostPlayButton(stringResource(R.string.notflix_postplay_next_episode), primary = true, fill = fill,
                enabled = playable, requester = nextRequester, tag = "notflix-postplay-next-button",
                modifier = Modifier.focusProperties {
                    left = creditsRequester
                    progressBarFocusRequester?.let { down = it }
                }.onPlaced {
                    // Same rule as the official card: take focus when it appears over hidden controls.
                    if (!controlsVisible && !placed) { placed = true; runCatching { nextRequester.requestFocus() } }
                }, onClick = { if (playable) onPlayNext() })
        }
    }
}

@Composable
private fun NotflixStillWatching(mode: PostPlayMode.StillWatching, onContinue: () -> Unit, onExit: () -> Unit) {
    val continueRequester = remember { FocusRequester() }
    val exitRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { continueRequester.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .82f)).testTag("notflix-still-watching")) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 96.dp, end = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.nuvio.tv.ui.appearance.NotflixWordmark(22.dp)
            Text(stringResource(R.string.still_watching_title), color = Spec.Text, fontFamily = Spec.BodyFont,
                fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(nextEpisodeDisplayLabel(mode.nextEpisode), color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NotflixPostPlayButton(stringResource(R.string.notflix_still_watching_continue), primary = true, fill = 0f,
                    enabled = true, requester = continueRequester, tag = "notflix-still-watching-continue",
                    modifier = Modifier.width(320.dp).focusProperties { down = exitRequester }, fillWidth = true, onClick = onContinue)
                NotflixPostPlayButton(stringResource(R.string.notflix_still_watching_exit), primary = false, fill = 0f,
                    enabled = true, requester = exitRequester, tag = "notflix-still-watching-exit",
                    modifier = Modifier.width(320.dp).focusProperties { up = continueRequester }, fillWidth = true, onClick = onExit)
            }
            mode.countdownSec?.let {
                Text(stringResource(R.string.still_watching_countdown, it), color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 14.sp)
            }
        }
    }
}

/** Rectangular button: primary is light with a white fill (countdown), secondary is outlined; focus = white. */
@Composable
internal fun NotflixPostPlayButton(
    label: String, primary: Boolean, fill: Float, enabled: Boolean, requester: FocusRequester, tag: String,
    modifier: Modifier = Modifier, fillWidth: Boolean = false, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val dark = Spec.Background
    Surface(
        onClick = onClick, enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(PostPlayButtonShape),
        // White only when focused (driven by our own flag, as the component's focused colour proved unreliable).
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when { focused -> Color.White; primary -> Color(0xFF8C8C8C); else -> Color.Black.copy(alpha = .55f) },
            focusedContainerColor = Color.White,
            contentColor = if (primary || focused) dark else Spec.Text, focusedContentColor = dark,
            disabledContainerColor = Color.White.copy(alpha = .18f), disabledContentColor = Spec.Text.copy(alpha = .6f)),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (primary) 0.dp else 2.dp, Color.White.copy(alpha = .75f)), shape = PostPlayButtonShape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = PostPlayButtonShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused }.testTag(tag)
    ) {
        // fillWidth: the caller fixed the button width, so the fill must span all of it, not just the label.
        Box((if (fillWidth) Modifier.fillMaxWidth() else Modifier).drawBehind {
            // Countdown fill only for the primary action: white over grey, or a darkening band over the focused white.
            if (primary && enabled && fill > 0f) drawRect(if (focused) Color.Black.copy(alpha = .22f) else Color.White,
                size = Size(size.width * fill, size.height))
        }.padding(horizontal = 22.dp, vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primary) Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                Text(label, fontFamily = Spec.BodyFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
