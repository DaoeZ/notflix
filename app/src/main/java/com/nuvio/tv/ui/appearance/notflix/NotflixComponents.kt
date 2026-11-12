package com.nuvio.tv.ui.appearance.notflix

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.domain.model.RatingSource
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.screens.home.RatingBadge
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/** Shared Notflix measurements for the 2.x screens (posters, rows and grids). */
object NotflixPosterMetrics {
    val Width = 136.dp
    val Height = 202.dp
    val Spacing = 11.dp
    val Corner = 4.dp
    const val COLUMNS = 6

    /** M64: category pages follow the card size; 136x202 stays the Large measurement. */
    fun width(size: NotflixCardSize) = Math.round(Width.value * size.scale).dp
    fun height(size: NotflixCardSize) = Math.round(Height.value * size.scale).dp
    /** 7 columns fit at Small with the current gutters (34 dp) and spacing (11 dp); Medium/Large keep 6. */
    fun columns(size: NotflixCardSize) = if (size == NotflixCardSize.SMALL) 7 else COLUMNS
}

/**
 * Minimal poster data every Notflix screen can build from its own model: nothing is invented,
 * missing fields are simply not drawn.
 */
@Immutable
data class NotflixPosterSpec(
    val key: String,
    val title: String,
    val poster: String? = null,
    val rating: Float? = null,
    /** M57: kept with the value so the badge never labels a TMDB rating as IMDb. */
    val ratingSource: RatingSource = RatingSource.IMDB,
    /** M39 rule: watched titles are dimmed while they are not focused. */
    val watched: Boolean = false,
    /** M40 rule: optional «New» style label; the caller decides when it applies. */
    val badge: String? = null,
    /** 0..1 progress bar at the bottom edge. */
    val progress: Float? = null
)

/** Same values the Inicio card shows; no extra metadata is requested here. */
internal fun MetaPreview.toNotflixPosterSpec(watched: Boolean = false): NotflixPosterSpec =
    NotflixPosterSpec(
        key = "$apiType:$id",
        title = name,
        poster = poster,
        rating = imdbRating,
        ratingSource = ratingSource,
        watched = watched
    )

internal fun LibraryEntry.toNotflixPosterSpec(watched: Boolean): NotflixPosterSpec =
    NotflixPosterSpec(
        key = "$type:$id",
        title = name,
        poster = poster,
        rating = imdbRating,
        watched = watched
    )

/**
 * Poster card shared by Mi lista, persons, studios and folders. It only draws: scale 1,06 and a
 * 2 dp white border while focused, watched titles with the Inicio dimming and an optional badge.
 * The shared card does not request a sharper poster variant; a screen that wants the Inicio
 * behaviour passes its own sharpened URL in [NotflixPosterSpec.poster].
 */
@Composable
fun NotflixPosterCard(
    item: NotflixPosterSpec,
    modifier: Modifier = Modifier,
    width: Dp = NotflixPosterMetrics.Width,
    height: Dp = NotflixPosterMetrics.Height,
    focused: Boolean = false,
    watched: Boolean = item.watched,
    badge: String? = item.badge,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember(item.key) { mutableStateOf(focused) }
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val longPress = rememberLongPressKeyTracker()
    val confirmSound = rememberNotflixConfirmSound()
    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reducedMotion) 1.06f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(Spec.FOCUS_MS),
        label = "notflix-poster-focus"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (watched && !isFocused) 0.5f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(Spec.FOCUS_MS),
        label = "notflix-poster-watched"
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(NotflixPosterMetrics.Corner))
            .background(Spec.Surface)
            .border(
                width = 2.dp,
                color = if (isFocused) Spec.Text else Color.Transparent,
                shape = RoundedCornerShape(NotflixPosterMetrics.Corner)
            )
            .onPreviewKeyEvent { event ->
                if (onLongClick == null) {
                    false
                } else {
                    longPress.handle(
                        event.nativeKeyEvent,
                        { code ->
                            code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                code == android.view.KeyEvent.KEYCODE_ENTER ||
                                code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                        },
                        onLongClick
                    )
                }
            }
            .clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
            .testTag("notflix-poster-${item.key}")
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = item.title,
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                badge?.takeIf { it.isNotBlank() }?.let { NotflixBadge(it) }
                RatingBadge(item.rating, source = item.ratingSource)
            }
        }
        item.progress?.let { progress ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Spec.Secondary)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Spec.Accent)
                )
            }
        }
    }
}

@Composable
private fun NotflixBadge(label: String) {
    Text(
        text = label,
        color = Color.White,
        fontFamily = Spec.BodyFont,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(Spec.Accent, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/**
 * Notflix chip: 16 sp, 14x7 padding, no border. The selected one is inverted; the focused one adds
 * a 2 dp white border so focus never depends on colour alone. A focused selected chip is already
 * white filled, so its focus border is Accent (same rule as the M31 search retry button).
 */
@Composable
fun NotflixChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    testTag: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) Spec.Text else Color.Transparent)
            .border(
                width = 2.dp,
                color = when {
                    focused && selected -> Spec.Accent
                    focused -> Spec.Text
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(3.dp)
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Spec.Background else Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Flat Notflix trigger used by dropdowns and secondary actions: text plus «▾». Focus inverts it
 * and adds the white border; when the fill is already white (`primary`), the focus border is
 * Accent so the focused state stays visible.
 */
@Composable
fun NotflixFlatButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String = "▾",
    primary: Boolean = false,
    testTag: String? = null,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .clip(RoundedCornerShape(3.dp))
            .background(if (focused || primary) Spec.Text else Color.Transparent)
            .border(
                width = 2.dp,
                color = when {
                    focused && primary -> Spec.Accent
                    focused -> Spec.Text
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(3.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = { confirmSound(); onClick() })
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (focused || primary) Spec.Background else Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailing.isNotEmpty()) {
                Text(
                    text = trailing,
                    color = if (focused || primary) Spec.Background else Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Flat Notflix segment for a two-option control: contiguous segments, the chosen one filled white
 * with black text, and focus shown with a 2 dp white border (Accent when the fill is already white).
 */
@Composable
fun NotflixSegment(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onFocused: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (chosen) Spec.Text else Color.Transparent)
            .border(
                width = 2.dp,
                color = when {
                    focused && chosen -> Spec.Accent
                    focused -> Spec.Text
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(2.dp)
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { selected = chosen }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (chosen) Spec.Background else Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Loading block: Spec.Surface with a soft shine; fully static with reduced motion. */
@Composable
fun NotflixSkeletonBlock(modifier: Modifier = Modifier, radius: Dp = 4.dp) {
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val pulseAlpha = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "notflix-skeleton")
        val animated by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_100),
                repeatMode = RepeatMode.Reverse
            ),
            label = "notflix-skeleton-alpha"
        )
        animated
    }
    Box(
        modifier
            .graphicsLayer { alpha = pulseAlpha }
            .background(Spec.Surface, RoundedCornerShape(radius))
    )
}

/** Section label used above poster rows and inside panels. */
@Composable
fun NotflixSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Spec.Text,
        fontFamily = Spec.BodyFont,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Notflix confirmation action: white fill, black text, white border while focused. */
@Composable
fun NotflixPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    NotflixFlatButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        trailing = "",
        primary = true,
        testTag = testTag
    )
}

/**
 * Extiende el contenido [horizontal] a la derecha sin ensanchar su hueco: la cabecera de una página
 * llega al borde de la pantalla aunque la cuadrícula use canaletas laterales.
 */
internal fun Modifier.notflixBleedRight(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(0, 0) }
}
