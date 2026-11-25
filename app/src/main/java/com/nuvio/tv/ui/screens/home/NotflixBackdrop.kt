package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private data class DecodedBackdrop(val url: String, val painter: Painter)

/** A late success for a superseded request must never replace the current backdrop. */
internal fun notflixBackdropMatches(desired: String?, decoded: Any?): Boolean =
    !desired.isNullOrBlank() && desired == decoded

internal fun notflixNeighbourBackdrops(items: List<MetaPreview>, index: Int, wrap: Boolean = false): List<String> {
    if (index !in items.indices || items.size < 2) return emptyList()
    return listOf(index - 1, index + 1).mapNotNull { neighbour ->
        val position = if (wrap) Math.floorMod(neighbour, items.size) else neighbour
        items.getOrNull(position)?.let { it.background ?: it.landscapePoster }?.takeIf { it.isNotBlank() }
    }.distinct().take(2)
}

/** Backdrop decode bounds capped at 720p max to save 40-80 MB on 4K TV outputs under dark scrims. */
@Composable
private fun backdropSize(): Pair<Int, Int> {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val rawW = (configuration.screenWidthDp * density).roundToInt().coerceAtLeast(1)
    val rawH = (configuration.screenHeightDp * density).roundToInt().coerceAtLeast(1)
    return rawW.coerceAtMost(1280) to rawH.coerceAtMost(720)
}

/** Keep the last decoded image through Loading/Error; crossfade only after the new one is ready.
 * [scopeKey] retires retained artwork when its profile/appearance binding changes.
 */
@Composable
internal fun NotflixRetainedBackdrop(
    url: String?, scopeKey: Any?, reduced: Boolean, modifier: Modifier,
    alignment: Alignment = Alignment.Center, fadeMs: Int = 220,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalContext.current),
    imageLayer: GraphicsLayerScope.(current: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val (width, height) = backdropSize()
    val request = remember(context, url, width, height) {
        ImageRequest.Builder(context).data(url).size(width, height).build()
    }
    val painter = rememberAsyncImagePainter(request, imageLoader)
    val state by painter.state.collectAsState()
    var displayed by remember(scopeKey) { mutableStateOf<DecodedBackdrop?>(null) }
    LaunchedEffect(state, url, scopeKey) {
        // Claude (M69 review): a title without art must not keep showing the previous title's art.
        if (url.isNullOrBlank()) { displayed = null; return@LaunchedEffect }
        val success = state as? AsyncImagePainter.State.Success
        val error = state as? AsyncImagePainter.State.Error
        if (success != null && notflixBackdropMatches(url, success.result.request.data)) {
            displayed = DecodedBackdrop(requireNotNull(url), success.painter)
        } else if (error != null && notflixBackdropMatches(url, error.result.request.data)) {
            displayed = null
        }
    }
    // Claude (M69 review): a new profile/appearance discards the whole crossfade at once, so the old
    // binding's art can't linger as an outgoing crossfade item.
    key(scopeKey) {
        Crossfade(displayed, modifier, animationSpec = if (reduced) snap() else tween(fadeMs),
            label = "notflix-decoded-backdrop") { image ->
            if (image != null) Image(image.painter, null,
                Modifier.fillMaxSize().graphicsLayer { imageLayer(image.url == url) },
                contentScale = ContentScale.Crop, alignment = alignment)
        }
    }
}

/** Latest focus is observed in a coroutine, so prefetch does not subscribe the Home root to focus.
 * Fast held-key navigation cancels its dwell and requests; at most two images enter Coil's cache.
 */
@Composable
internal fun NotflixBackdropPrefetch(scopeKey: Any?, urls: () -> List<String>) {
    val context = LocalContext.current
    val (width, height) = backdropSize()
    val latestUrls by rememberUpdatedState(urls)
    LaunchedEffect(context, scopeKey, width, height) {
        snapshotFlow { latestUrls().distinct().take(2) }.distinctUntilChanged().collectLatest { images ->
            delay(150)
            val requests = images.map { image ->
                SingletonImageLoader.get(context).enqueue(
                    ImageRequest.Builder(context).data(image).size(width, height).build())
            }
            try { awaitCancellation() } finally { requests.forEach { it.dispose() } }
        }
    }
}
