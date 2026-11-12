package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.Crossfade
import com.nuvio.tv.ui.appearance.NotflixWordmark
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.screens.ambient.LocalAmbientIdleController
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.RatingSource
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.notflixRating

/** Test-only pull observation; default null, never publishes frame values to accessibility. */
internal val LocalBillboardFrameObserver = staticCompositionLocalOf<((() -> Float, () -> Float) -> Unit)?> { null }

/** Image-only motion; actions remain outside crossfades so focus and held keys keep their identity. */
@Composable
internal fun NotflixBillboard(
    item: MetaPreview, index: Int, count: Int, binding: PreviewBinding?, reduced: Boolean,
    eligible: Boolean, manualEpoch: Long, infoRequester: FocusRequester, playRequester: FocusRequester,
    liveAllowed: () -> Boolean = { true }, onAdvance: () -> Unit, onSelect: (Int) -> Unit,
    onInteraction: (Boolean) -> Unit, onFocus: (BillboardAction) -> Unit,
    onKey: (BillboardAction, Key) -> Unit, onInfo: () -> Unit, onPlay: (() -> Unit)?
) {

    val ambientController = LocalAmbientIdleController.current
    val ambient = ambientController?.let { it.isShowing.collectAsState().value } ?: false
    var interacting by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    LocalBillboardFrameObserver.current?.let { observer ->
        SideEffect { observer({ fraction }, { if (reduced) 1f else 1.02f + fraction * .035f }) }
    }
    val identity = notflixMetaKey(item)
    val latestItem by rememberUpdatedState(item)
    var displayedCopy by remember(binding) { mutableStateOf(item) }
    val copyAlpha = remember(binding) { Animatable(1f) }
    // Discrete phases gate activation; opacity is read only by the layer, never by composition.
    var copyPhase by remember(binding) { mutableIntStateOf(0) }
    val visibleItem = if (reduced || notflixMetaKey(displayedCopy) == identity) item else displayedCopy
    val actionsReady = reduced || (copyPhase == 0 && notflixMetaKey(displayedCopy) == identity)
    LaunchedEffect(binding, identity, reduced) {
        if (reduced) {
            displayedCopy = latestItem
            copyAlpha.snapTo(1f)
        } else {
            if (notflixMetaKey(displayedCopy) != identity) {
                copyPhase = 1
                copyAlpha.animateTo(0f, tween(120))
                displayedCopy = latestItem
            }
            if (copyAlpha.value < 1f) {
                copyPhase = 2
                copyAlpha.animateTo(1f, tween(180))
            }
        }
        copyPhase = 0
    }
    var periodIdentity by remember { mutableStateOf<Triple<PreviewBinding?, String, Long>?>(null) }
    val latestAdvance by rememberUpdatedState(onAdvance)
    val latestAllowed by rememberUpdatedState(liveAllowed)
    val running = eligible && !ambient && !interacting && !reduced && count > 1
    val latestRunning by rememberUpdatedState(running)
    LaunchedEffect(binding, identity, manualEpoch, eligible, running, ambient) {
        val token = Triple(binding, identity, manualEpoch)
        if (periodIdentity != token || !eligible || ambient || reduced || count <= 1) {
            fraction = 0f
            periodIdentity = token
        }
        if (!running) return@LaunchedEffect
        val clock = NotflixBillboardClock(fraction)
        while (true) {
            val frame = withFrameNanos { clock.frame(it) }
            if (!latestRunning || ambientController?.isShowing?.value == true || !latestAllowed()) { fraction = 0f; break }
            fraction = frame.fraction
            if (frame.advance) {
                if (latestRunning && latestAllowed()) latestAdvance()
                break
            }
        }
    }
    // Perf (TV GPU): no own background, the Notflix shell root already paints it; fewer full-width fills.
    Box(Modifier.fillMaxWidth().height(Spec.HERO_HEIGHT.dp).clipToBounds().testTag("notflix-editorial")
        .onFocusChanged { interacting = it.hasFocus; onInteraction(it.hasFocus) }.focusGroup()) {
        key(binding) {
            Crossfade(identity, Modifier.matchParentSize(), animationSpec = if (reduced) snap() else tween(450), label = "notflix-billboard-art") { shownIdentity ->
                var retained by remember(shownIdentity) { mutableStateOf(item) }
                val shown = if (shownIdentity == identity) item else retained
                SideEffect { if (shownIdentity == identity) retained = item }
                val art = shown.background ?: shown.landscapePoster
                if (!art.isNullOrBlank()) AsyncImage(art, null, Modifier.fillMaxSize().graphicsLayer {
                        val phase = if (shownIdentity == identity) fraction else 1f
                    scaleX = if (reduced) 1f else 1.02f + phase * .035f
                    scaleY = scaleX
                    translationX = if (reduced) 0f else -size.width * phase * .01f
                }.testTag("notflix-editorial-art"), contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.6f))
            }
        }
        // Perf: each scrim only covers the band where it is not transparent (same look, less fill rate).
        Box(Modifier.fillMaxHeight().fillMaxWidth(.75f).background(Brush.horizontalGradient(
            0f to Spec.Background.copy(.92f), .28f / .75f to Spec.Background.copy(.60f),
            .58f / .75f to Spec.Background.copy(.08f), 1f to Color.Transparent)))
        // The top darkening comes from the Notflix bar gradient that already sits over this band.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(.42f).background(Brush.verticalGradient(
            0f to Color.Transparent, (.86f - .58f) / .42f to Spec.Background.copy(.75f), 1f to Spec.Background)))
        Column(Modifier.align(Alignment.BottomStart).width(420.dp).padding(start = Spec.GUTTER.dp, bottom = 32.dp)
            .testTag("notflix-editorial-copy"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = if (reduced) 1f else copyAlpha.value }
                .testTag("notflix-billboard-visible-copy")) {
                BillboardCopy(visibleItem)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onPlay != null) BillboardButton(stringResource(R.string.notflix_billboard_play),
                    stringResource(R.string.notflix_billboard_play_description, visibleItem.name), "notflix-editorial-play", playRequester,
                    symbol = "▶", primary = true, enabled = actionsReady, activationToken = binding to identity, onFocus = { onFocus(BillboardAction.PLAY) }, onKey = { onKey(BillboardAction.PLAY, it) }, onClick = onPlay)
                BillboardButton(stringResource(R.string.notflix_billboard_info),
                    stringResource(R.string.notflix_billboard_info_description, visibleItem.name), "notflix-editorial-details", infoRequester,
                    symbol = "ⓘ", primary = onPlay == null, enabled = actionsReady, activationToken = binding to identity, onFocus = { onFocus(BillboardAction.INFO) }, onKey = { onKey(BillboardAction.INFO, it) }, onClick = onInfo)
            }
        }
        if (count > 1) Row(Modifier.align(Alignment.BottomEnd).padding(end = Spec.GUTTER.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            val confirmSound = rememberNotflixConfirmSound()
            repeat(count) { position ->
                var markerFocused by remember(position) { mutableStateOf(false) }
                val description = stringResource(R.string.notflix_billboard_select, position + 1, count)
                Box(Modifier.width(34.dp).height(24.dp).onFocusChanged { markerFocused = it.isFocused }
                    .border(1.dp, if (markerFocused) Spec.Text else Color.Transparent, RoundedCornerShape(2.dp)).semantics {
                    contentDescription = description; selected = position == index
                }.onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key in setOf(Key.DirectionUp, Key.DirectionDown)) {
                        onKey(BillboardAction.INFO, it.key); true
                    } else false
                }.clickable(role = Role.Button) { confirmSound(); onSelect(position) }.testTag("notflix-billboard-select-$position"), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(3.dp).drawWithContent {
                        drawRect(Spec.Secondary.copy(.55f))
                        if (position == index) {
                            val fill = if (reduced) 1f else fraction.coerceAtLeast(.04f)
                            drawRect(Spec.Text, size = Size(size.width * fill, size.height))
                        }
                    })
                }
            }
        }
    }
}

/** M57: the banner labels the rating with its real origin; the value keeps its existing format. */
internal fun notflixBillboardRatingText(source: RatingSource, rating: String): String =
    "${source.label} $rating"

@Composable
private fun BillboardCopy(item: MetaPreview) {
    var logoFailed by remember(item.logo) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NotflixWordmark(height = 10.dp)
            if (item.apiType in setOf("movie", "series")) Text(
                stringResource(if (item.apiType == "series") R.string.notflix_billboard_series else R.string.notflix_billboard_film),
                color = Spec.Text, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp, fontFamily = Spec.BodyFont)
        }
        if (!item.logo.isNullOrBlank() && !logoFailed) AsyncImage(item.logo, item.name,
            Modifier.widthIn(max = 280.dp).height(74.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            onError = { logoFailed = true })
        else Text(item.name, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 36.sp, lineHeight = 40.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            notflixRating(item.imdbRating)?.let { rating ->
                Text(notflixBillboardRatingText(item.ratingSource, rating), color = Spec.Text,
                    fontSize = 13.sp, lineHeight = 17.sp, fontFamily = Spec.BodyFont)
            }
            val facts = listOfNotNull(item.releaseInfo, item.runtime?.let { value ->
                value.toIntOrNull()?.let { stringResource(R.string.notflix_runtime_minutes, it) } ?: value }, item.ageRating)
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (facts.isNotEmpty()) Text(facts, color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 13.sp, lineHeight = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.genres.isNotEmpty()) Text(item.genres.take(3).joinToString(" · "), color = Spec.Secondary,
            fontFamily = Spec.BodyFont, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Spec.Text, fontFamily = Spec.BodyFont,
            fontSize = 14.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun BillboardButton(label: String, description: String, tag: String, requester: FocusRequester, symbol: String, primary: Boolean, enabled: Boolean, activationToken: Pair<PreviewBinding?, String>,
    onFocus: () -> Unit, onKey: (Key) -> Unit, onClick: () -> Unit) {

    var focused by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var heldToken by remember { mutableStateOf<Pair<PreviewBinding?, String>?>(null) }
    val latestClick by rememberUpdatedState(onClick)
    val confirmSound = rememberNotflixConfirmSound()
    LaunchedEffect(activationToken, enabled) {
        if (!enabled || heldToken != activationToken) { held = false; heldToken = null }
    }
    Text("$symbol $label", color = if (primary) Spec.Background else Spec.Text, fontFamily = Spec.BodyFont,
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.focusRequester(requester).onFocusChanged {
            focused = it.isFocused
            if (focused) onFocus() else { held = false; heldToken = null }
        }.onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            if (code in setOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (event.type == KeyEventType.KeyDown) { if (enabled && event.nativeKeyEvent.repeatCount == 0 && !held) { held = true; heldToken = activationToken } }
                else if (event.type == KeyEventType.KeyUp) {
                    val activate = enabled && held && heldToken == activationToken && !event.nativeKeyEvent.isCanceled
                    held = false; heldToken = null
                    if (activate) { confirmSound(); latestClick() }
                }
                true
            } else if (event.type == KeyEventType.KeyDown && event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
                onKey(event.key); true
            } else false
        }.border(2.dp, if (focused) Spec.Text else Color.Transparent, RoundedCornerShape(4.dp)).padding(3.dp)
            .background(if (primary) Spec.Text else Spec.Surface, RoundedCornerShape(3.dp))
            .clickable(role = Role.Button) { if (enabled) { confirmSound(); onClick() } }.padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = description; if (!enabled) disabled() }.testTag(tag))
}