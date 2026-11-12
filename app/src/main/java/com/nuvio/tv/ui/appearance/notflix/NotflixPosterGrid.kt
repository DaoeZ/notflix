@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.appearance.notflix

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames

/**
 * Poster grid of «Mi lista» and the 2.1 category pages. It keeps the focused poster by key so
 * coming back from a detail page lands exactly where the user was.
 *
 * [header] receives the lazy grid scope so a page can put its own header/filters above the posters
 * inside the same scroll container: the focused row then always stays visible.
 */
@Composable
fun NotflixPosterGrid(
    items: List<NotflixPosterSpec>,
    onClick: (NotflixPosterSpec) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = NotflixPosterMetrics.COLUMNS,
    posterWidth: Dp = NotflixPosterMetrics.Width,
    posterHeight: Dp = NotflixPosterMetrics.Height,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(
        start = Spec.GUTTER.dp,
        end = Spec.GUTTER.dp,
        bottom = 40.dp
    ),
    verticalSpacing: Dp = NotflixPosterMetrics.Spacing,
    saveKey: String = "notflix-poster-grid",
    initialFocusKey: String? = null,
    requestInitialFocus: Boolean = true,
    header: (LazyGridScope.() -> Unit)? = null,
    onFocused: (NotflixPosterSpec) -> Unit = {},
    onLongClick: ((NotflixPosterSpec) -> Unit)? = null,
    /** M63: teclas del mando de la tarjeta enfocada (cursor de botones de la tarjeta abierta). */
    onCardKey: ((NotflixPosterSpec, androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onActualFocus: ((NotflixPosterSpec, Boolean) -> Unit)? = null,
    covered: ((NotflixPosterSpec) -> Boolean)? = null,
    removedFocusKey: (() -> String?)? = null,
    headerItemCount: Int = 0
) {
    val keys = remember(items) { items.map { it.key } }
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    var savedKey by rememberSaveable(saveKey) { mutableStateOf<String?>(null) }
    var initialFocusDone by remember(saveKey) { mutableStateOf(false) }
    // M67: posición del póster enfocado, para heredarla cuando ese título desaparece (ocultar).
    val focusedIndex = remember { mutableIntStateOf(-1) }

    LaunchedEffect(keys, requestInitialFocus) {
        if (initialFocusDone || !requestInitialFocus || keys.isEmpty()) return@LaunchedEffect
        val target = savedKey?.takeIf { keys.contains(it) }
            ?: initialFocusKey?.takeIf { keys.contains(it) }
            ?: keys.first()
        val index = keys.indexOf(target)
        if (index > 0 && state.firstVisibleItemIndex == 0) {
            runCatching { state.scrollToItem(index) }
        }
        repeat(2) { withFrameNanos { } }
        val requester = requesters[target]
        if (requester != null && requester.requestFocusAfterFrames(frames = 1)) {
            initialFocusDone = true
        }
    }

    // M67: si el título enfocado desaparece de la rejilla (Ocultar), el foco pasa a su vecino en
    // lugar de perderse; no se toca la posición ni el resto de la cuadrícula.
    LaunchedEffect(keys, initialFocusDone) {
        if (!initialFocusDone) return@LaunchedEffect
        val lost = removedFocusKey?.invoke()?.takeIf { it == savedKey } ?: return@LaunchedEffect
        if (keys.isEmpty() || lost in keys || focusedIndex.intValue < 0) return@LaunchedEffect
        val target = notflixPosterFocusAfterRemoval(focusedIndex.intValue, keys) ?: return@LaunchedEffect
        val index = keys.indexOf(target)
        // The next item may still be outside the lazy viewport (last row/column removal).
        if (state.layoutInfo.visibleItemsInfo.none { it.key == target }) {
            state.scrollToItem(index + headerItemCount.coerceAtLeast(0))
        }
        if (requesters[target]?.requestFocusAfterFrames(frames = 2) == true) {
            savedKey = target
            focusedIndex.intValue = index
        }
    }

    // TV focus scrolling pins the focused item at a pivot; here it would push the page header out of
    // view on entry. Scroll only as much as needed to show the focused poster.
    CompositionLocalProvider(LocalBringIntoViewSpec provides NotflixMinimalBringIntoView) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        state = state,
        modifier = modifier.testTag("notflix-poster-grid"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        header?.invoke(this)
        gridItems(items, key = { it.key }) { item ->
            val requester = requesters[item.key] ?: remember(item.key) { FocusRequester() }
            NotflixPosterCard(
                item = item,
                modifier = Modifier.focusRequester(requester)
                    .then(if (onActualFocus != null) Modifier.onFocusChanged { onActualFocus(item, it.isFocused) } else Modifier)
                    .then(if (covered != null) Modifier.graphicsLayer { alpha = if (covered(item)) 0f else 1f } else Modifier)
                    .then(
                    if (onCardKey != null) {
                        Modifier.onPreviewKeyEvent { event -> onCardKey(item, event) }
                    } else Modifier
                ),
                width = posterWidth,
                height = posterHeight,
                onFocused = {
                    savedKey = item.key
                    focusedIndex.intValue = keys.indexOf(item.key)
                    onFocused(item)
                },
                onClick = { onClick(item) },
                onLongClick = onLongClick?.let { press -> { press(item) } }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Bottom breathing room so the last row is never glued to the screen edge.
            Spacer(Modifier.height(12.dp))
        }
    }
    }
}

/**
 * M67: vecino que hereda el foco cuando desaparece el póster enfocado (p. ej. al ocultarlo). Se
 * queda la misma posición, que ya ocupa el siguiente título; en el final de la lista, el último.
 */
internal fun notflixPosterFocusAfterRemoval(previousIndex: Int, remainingKeys: List<String>): String? =
    if (remainingKeys.isEmpty()) null
    else remainingKeys[previousIndex.coerceIn(0, remainingKeys.lastIndex)]

/** Scrolls by the minimum needed to show the focused item; nothing when it is already visible. */
@OptIn(ExperimentalFoundationApi::class)
internal val NotflixMinimalBringIntoView: BringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> trailing - containerSize
        }
    }
}
