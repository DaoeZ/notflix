@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import kotlinx.coroutines.launch

/** Presentation slots only. All selection, return state and actions stay in MetaDetailsContent. */
@Composable
internal fun NotflixDetailStage(
    series: Boolean,
    panelEntryFocusRequester: FocusRequester?,
    onPanelFocusChanged: (Boolean) -> Unit,
    hero: @Composable () -> Unit,
    seasons: @Composable () -> Unit,
    episodes: @Composable () -> Unit
) {
    // Focus relocation may leave an unconsumed delta when the inner list reaches
    // its edge. Keep that side effect inside the panel's viewport; moving focus
    // to a secondary section still uses the outer list's normal relocation.
    val panelScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.SideEffect) Offset(0f, available.y) else Offset.Zero
        }
    }
    Box(Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp).testTag("notflix_detail_stage")) {
        com.nuvio.tv.ui.appearance.NotflixWordmark(18.dp, Modifier.padding(start = 34.dp, top = 16.dp))
        Row(Modifier.fillMaxSize().padding(start = if (series) 80.dp else 64.dp, end = 34.dp, top = 52.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().testTag("notflix_detail_copy")) { hero() }
            if (series) {
                val shape = RoundedCornerShape(8.dp)
                Column(Modifier.width(300.dp).fillMaxHeight()
                    .nestedScroll(panelScrollBoundary)
                    .background(NotflixVisualSpec.Surface.copy(alpha = .82f), shape)
                    .border(1.dp, Color.White.copy(alpha = .10f), shape)
                    .onFocusChanged { onPanelFocusChanged(it.hasFocus) }
                    .focusProperties {
                        onEnter = {
                            if (requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Right) {
                                panelEntryFocusRequester?.requestFocus()
                            }
                        }
                    }
                    .focusGroup()
                    .testTag("notflix_episode_panel")) {
                    seasons()
                    Box(Modifier.weight(1f).fillMaxWidth()) { episodes() }
                }
            } else {
                // Sources retain their official screen; no placeholder provider panel.
                Spacer(Modifier.width(300.dp))
            }
        }
    }
}

/** Reuses the official image request and metadata/action/description slots. No IO or state owner. */
@Composable
internal fun NotflixDetailHero(
    name: String,
    logo: ImageRequest?,
    showLogo: Boolean,
    onLogoError: () -> Unit,
    creditLine: String?,
    cast: List<String>,
    metadata: @Composable () -> Unit,
    ratings: @Composable () -> Unit,
    description: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = 32.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.Bottom) {
        if (showLogo) {
            AsyncImage(logo, contentDescription = name, onError = { onLogoError() },
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.widthIn(max = 240.dp).height(64.dp).fillMaxWidth().testTag("notflix_detail_logo"))
        } else {
            Text(name, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 32.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.Bold, color = NotflixVisualSpec.Text, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("notflix_detail_title"))
        }
        Spacer(Modifier.height(16.dp))
        metadata()
        ratings()
        if (cast.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.pause_cast_label), fontFamily = NotflixVisualSpec.BodyFont,
                fontSize = 9.sp, color = NotflixVisualSpec.Secondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cast.take(3).forEach { person ->
                    Text(person, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 11.sp,
                        color = NotflixVisualSpec.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
        }
        if (!creditLine.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(creditLine, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 11.sp,
                color = NotflixVisualSpec.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        description()
        Spacer(Modifier.height(18.dp))
        actions()
    }
}

/** Labels already resolved by the official metadata formatter/visibility settings. */
@Composable
internal fun NotflixDetailMetadata(
    primary: List<String>,
    genres: String,
    secondary: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.testTag("notflix_detail_metadata")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            primary.forEach { label -> Text(label, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp,
                color = NotflixVisualSpec.Text, fontWeight = FontWeight.SemiBold) }
        }
        if (genres.isNotBlank()) Text(genres, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 11.sp,
            color = NotflixVisualSpec.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (secondary.isNotEmpty()) Text(secondary.joinToString(" · "), fontFamily = NotflixVisualSpec.BodyFont,
            fontSize = 10.sp, color = NotflixVisualSpec.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Local picker UI only: the authoritative season and its callback remain official. */
@Composable
internal fun NotflixSeasonSelector(
    seasons: List<Int>, selected: Int, onSelected: (Int) -> Unit, onLongPress: (Int) -> Unit,
    selectedFocus: FocusRequester, heroFocus: FocusRequester?, episodeFocus: FocusRequester?, enabled: Boolean
) {
    var open by remember { mutableStateOf(false) }
    var longTriggered by remember { mutableStateOf(false) }
    val tracker = rememberLongPressKeyTracker()
    val scope = rememberCoroutineScope()
    val index = seasons.indexOf(selected)
    val previous = seasons.getOrNull(index - 1)
    val next = seasons.getOrNull(index + 1)
    val shape = RoundedCornerShape(4.dp)
    val colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = .05f),
        focusedContainerColor = Color.White.copy(alpha = .14f), contentColor = NotflixVisualSpec.Text,
        focusedContentColor = Color.White)
    val border = ButtonDefaults.border(focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = shape))
    fun close() { open = false; scope.launch { selectedFocus.requestFocusAfterFrames() } }
    fun choose(season: Int) { onSelected(season); scope.launch { selectedFocus.requestFocusAfterFrames() } }
    fun selectKey(code: Int) = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { previous?.let { choose(it) } }, enabled = enabled && previous != null,
            modifier = Modifier.width(68.dp).height(36.dp).testTag("notflix-detail-prev")
                .focusProperties { canFocus = enabled && previous != null; left = heroFocus ?: FocusRequester.Cancel
                    up = heroFocus ?: FocusRequester.Default; down = episodeFocus ?: FocusRequester.Default },
            colors = colors, shape = ButtonDefaults.shape(shape), border = border, scale = ButtonDefaults.scale(focusedScale = 1f),
            contentPadding = PaddingValues(4.dp)) {
            Text(stringResource(R.string.notflix_season_previous), fontFamily = NotflixVisualSpec.BodyFont, fontSize = 11.sp, maxLines = 1)
        }
        Button(onClick = { if (longTriggered) longTriggered = false else open = true }, enabled = enabled,
            modifier = Modifier.weight(1f).height(36.dp).focusRequester(selectedFocus).testTag("notflix-detail-season-$selected")
                .focusProperties { canFocus = enabled
                    if (previous == null) left = heroFocus ?: FocusRequester.Cancel
                    up = heroFocus ?: FocusRequester.Default; down = episodeFocus ?: FocusRequester.Default }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount == 0 && selectKey(native.keyCode)) {
                        longTriggered = false
                    }
                    if (native.action == android.view.KeyEvent.ACTION_DOWN && native.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                        if (native.repeatCount == 0) { longTriggered = true; onLongPress(selected) }
                        true
                    } else if (tracker.handle(native, ::selectKey) { longTriggered = true; onLongPress(selected) }) {
                        if (native.action == android.view.KeyEvent.ACTION_UP) longTriggered = false
                        true
                    } else if (native.action == android.view.KeyEvent.ACTION_UP && longTriggered &&
                        (selectKey(native.keyCode) || native.keyCode == android.view.KeyEvent.KEYCODE_MENU)) {
                        longTriggered = false; true
                    } else false
                }, colors = colors, shape = ButtonDefaults.shape(shape), border = border,
            scale = ButtonDefaults.scale(focusedScale = 1f), contentPadding = PaddingValues(4.dp)) {
            Text((if (selected == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, selected)) + " ▾",
                fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = { next?.let { choose(it) } }, enabled = enabled && next != null,
            modifier = Modifier.width(68.dp).height(36.dp).testTag("notflix-detail-next")
                .focusProperties { canFocus = enabled && next != null; right = FocusRequester.Cancel
                    up = heroFocus ?: FocusRequester.Default; down = episodeFocus ?: FocusRequester.Default },
            colors = colors, shape = ButtonDefaults.shape(shape), border = border, scale = ButtonDefaults.scale(focusedScale = 1f),
            contentPadding = PaddingValues(4.dp)) {
            Text(stringResource(R.string.notflix_season_next), fontFamily = NotflixVisualSpec.BodyFont, fontSize = 11.sp, maxLines = 1)
        }
    }
    if (open) {
        val choiceFocus = remember { FocusRequester() }
        NuvioDialog(onDismiss = { close() }, title = stringResource(R.string.notflix_choose_season), width = 320.dp) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0)),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(seasons.size, key = { seasons[it] }) { position ->
                    val season = seasons[position]
                    Button(onClick = { open = false; choose(season) },
                        modifier = Modifier.fillMaxWidth().testTag("notflix-detail-season-option-$season")
                            .then(if (season == selected) Modifier.focusRequester(choiceFocus) else Modifier),
                        colors = colors, shape = ButtonDefaults.shape(shape), border = border, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                        Text(if (season == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, season))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { choiceFocus.requestFocusAfterFrames() }
    }
}

/** Static detail gradients. No CSS animation or blur surface. */
internal fun Modifier.notflixDetailScrim(alpha: () -> Float): Modifier = drawWithCache {
    val bg = NotflixVisualSpec.Background
    val horizontal = Brush.horizontalGradient(0f to bg.copy(alpha = .94f), .32f to bg.copy(alpha = .70f),
        .60f to bg.copy(alpha = .12f), 1f to bg.copy(alpha = .35f))
    val bottom = Brush.verticalGradient(0f to Color.Transparent, .62f to Color.Transparent, 1f to bg.copy(alpha = .9f))
    val top = Brush.verticalGradient(0f to Color.Black.copy(alpha = .5f), .18f to Color.Transparent, 1f to Color.Transparent)
    onDrawBehind { val a = alpha(); drawRect(horizontal, alpha = a); drawRect(bottom, alpha = a); drawRect(top, alpha = a) }
}
