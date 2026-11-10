package com.nuvio.tv.ui.appearance.notflix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import kotlinx.coroutines.delay
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/** The focused title is only swapped after this dwell so fast scrolling never loads images. */
const val NOTFLIX_HEADER_DELAY_MS = 250L

/** Cross fade used when the header changes title. */
const val NOTFLIX_HEADER_FADE_MS = 200

/**
 * Header payload. Screens fill it from the model they already have; anything missing is simply not
 * drawn (no invented runtime, rating or synopsis).
 */
@Immutable
data class NotflixHeaderItem(
    val key: String,
    val title: String,
    val backdrop: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    /** Short facts joined with « · », e.g. 2019, Drama. */
    val metadata: List<String> = emptyList(),
    val overview: String? = null
)

/** Focused title, otherwise the first one, otherwise nothing. Pure so it can be proved. */
internal fun notflixHeaderKey(focusedKey: String?, keys: List<String>): String? =
    focusedKey?.takeIf { keys.contains(it) } ?: keys.firstOrNull()

/**
 * True when the header may already show [focusedKey]. [elapsedSinceFocusMs] comes from the injected
 * clock, so the 250 ms dwell is testable without Compose.
 */
internal fun notflixHeaderCommit(
    focusedKey: String?,
    shownKey: String?,
    elapsedSinceFocusMs: Long,
    delayMs: Long = NOTFLIX_HEADER_DELAY_MS
): Boolean = focusedKey != shownKey && elapsedSinceFocusMs >= delayMs

/**
 * Cinematic header: the focused title's backdrop on the right, black gradients to the left and to
 * the bottom, logo or BrandFont 44 name, one fact line and a two line synopsis.
 */
@Composable
fun NotflixFocusHeader(
    item: NotflixHeaderItem?,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    delayMs: Long = NOTFLIX_HEADER_DELAY_MS
) {
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    var shown by remember { mutableStateOf(item) }
    var focusedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(item?.key) {
        focusedAt = clock()
        val target = item
        when {
            target == null -> shown = null
            reducedMotion -> shown = target
            shown == null -> shown = target
            target.key == shown?.key -> Unit
            else -> {
                delay(delayMs)
                if (notflixHeaderCommit(target.key, shown?.key, clock() - focusedAt, delayMs)) shown = target
            }
        }
    }

    val fade = remember(shown?.key) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(shown?.key) {
        if (reducedMotion) fade.snapTo(1f) else fade.animateTo(1f, tween(NOTFLIX_HEADER_FADE_MS))
    }

    Box(modifier.fillMaxWidth().height(height).testTag("notflix-focus-header")) {
        shown?.let { current ->
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
                HeaderArtwork(current)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Spec.Background,
                                .32f to Spec.Background.copy(alpha = .92f),
                                .6f to Spec.Background.copy(alpha = 0f),
                                1f to Spec.Background.copy(alpha = 0f)
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Spec.Background.copy(alpha = .35f),
                                .45f to Spec.Background.copy(alpha = 0f),
                                1f to Spec.Background
                            )
                        )
                )
                HeaderCopy(current)
            }
        }
    }
}

@Composable
private fun HeaderArtwork(item: NotflixHeaderItem) {
    val image = item.backdrop?.takeIf { it.isNotBlank() } ?: item.poster?.takeIf { it.isNotBlank() }
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd
        )
    }
}

@Composable
private fun HeaderCopy(item: NotflixHeaderItem) {
    var logoFailed by remember(item.logo) { mutableStateOf(false) }
    val metadata = item.metadata.filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        val logo = item.logo
        if (!logo.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model = logo,
                contentDescription = item.title,
                modifier = Modifier.widthIn(max = 260.dp).height(70.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true }
            )
        } else {
            Text(
                text = item.title,
                color = Spec.Text,
                fontFamily = Spec.BrandFont,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                color = Spec.Secondary.copy(alpha = .92f),
                fontFamily = Spec.BodyFont,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 420.dp).padding(top = 6.dp)
            )
        }
    }
}
