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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    onLongClick: ((NotflixPosterSpec) -> Unit)? = null
) {
    val keys = remember(items) { items.map { it.key } }
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    var savedKey by rememberSaveable(saveKey) { mutableStateOf<String?>(null) }
    var initialFocusDone by remember(saveKey) { mutableStateOf(false) }

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
                modifier = Modifier.focusRequester(requester),
                onFocused = {
                    savedKey = item.key
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
