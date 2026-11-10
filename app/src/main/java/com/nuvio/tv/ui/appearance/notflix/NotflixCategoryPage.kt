package com.nuvio.tv.ui.appearance.notflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.flow.distinctUntilChanged
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * M49: the category page (collection folder and «Ver todo») asks for the next page when the last
 * visible poster is this many posters from the end. Same distance the official folder and catalog
 * screens already use, so what data is requested and when does not change.
 */
internal const val NOTFLIX_CATEGORY_LOAD_MORE_THRESHOLD = 10

/** A folder is a category page with chips only when it really has more than one catalog. */
internal fun notflixCategoryShowTabs(tabCount: Int): Boolean = tabCount > 1

/** Tab that is painted: the selected one if it still exists, otherwise the first. */
internal fun notflixCategoryTabIndex(selectedIndex: Int, tabCount: Int): Int {
    if (tabCount <= 0) return 0
    return selectedIndex.coerceIn(0, tabCount - 1)
}

/** Focus save key of each tab grid: stable between visits and different per tab. */
internal fun notflixCategoryGridSaveKey(scope: String, tabKey: String): String =
    "notflix-category-$scope-$tabKey"

/** Poster that takes the focus on (re)entry: the saved one by key, otherwise the first. */
internal fun notflixCategoryFocusTarget(savedKey: String?, keys: List<String>): String? =
    savedKey?.takeIf { key -> keys.contains(key) } ?: keys.firstOrNull()

/**
 * Asks for the next page when the last visible poster is [threshold] posters from the end. Counts
 * are in poster space, never in lazy-grid item space (the header items are not posters).
 */
internal fun notflixCategoryShouldLoadMore(
    lastVisiblePosterIndex: Int,
    totalPosters: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    threshold: Int = NOTFLIX_CATEGORY_LOAD_MORE_THRESHOLD
): Boolean {
    if (!hasMore || isLoading || totalPosters <= 0 || lastVisiblePosterIndex < 0) return false
    return lastVisiblePosterIndex >= totalPosters - threshold
}

/**
 * Stable and unique keys for a grid: the title identity, with an occurrence suffix only when the
 * same title appears twice in the row. A key follows its poster when the list shifts.
 */
internal fun notflixCategoryPosterKeys(items: List<MetaPreview>): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val identity = "${item.apiType}:${item.id}"
        val occurrence = seen.getOrDefault(identity, 0)
        seen[identity] = occurrence + 1
        if (occurrence == 0) identity else "$identity#$occurrence"
    }
}

/** Facts the card already carries; nothing is requested or invented here. */
internal fun MetaPreview.toNotflixCategoryHeader(): NotflixHeaderItem =
    NotflixHeaderItem(
        key = "$apiType:$id",
        title = name,
        backdrop = background ?: landscapePoster,
        poster = poster,
        logo = logo,
        metadata = buildList {
            releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
            genres.take(2).forEach { add(it) }
        },
        overview = description
    )

/** One catalog of the category page, already shaped for the shared grid. */
@Immutable
internal data class NotflixCategoryTab(
    val key: String,
    val label: String,
    val items: List<NotflixPosterSpec> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false
)

/**
 * Shared Notflix category page: focused title header (230 dp), page title with its collection or
 * add-on on the right, catalog chips when there is more than one and a six column poster grid.
 * Load, pagination, watched state and navigation all come from the caller; this only draws.
 */
@Composable
internal fun NotflixCategoryPage(
    title: String,
    collectionLabel: String?,
    tabs: List<NotflixCategoryTab>,
    selectedTabIndex: Int,
    saveScope: String,
    onSelectTab: (Int) -> Unit,
    headerFor: (NotflixPosterSpec) -> NotflixHeaderItem?,
    onPosterClick: (Int, NotflixPosterSpec) -> Unit,
    onLoadMore: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fallbackHeader: NotflixHeaderItem? = null,
    onPosterFocus: ((Int, NotflixPosterSpec) -> Unit)? = null,
    onPosterLongClick: ((Int, NotflixPosterSpec) -> Unit)? = null,
    emptyTitle: String? = null,
    emptyMessage: String? = null
) {
    val tabIndex = notflixCategoryTabIndex(selectedTabIndex, tabs.size)
    val current = tabs.getOrNull(tabIndex)
    val items = current?.items.orEmpty()
    val showTabs = notflixCategoryShowTabs(tabs.size)
    val emptyText = emptyTitle ?: stringResource(R.string.notflix_category_empty)

    var focusedKey by rememberSaveable(saveScope, current?.key) {
        mutableStateOf<String?>(null)
    }
    val keys = remember(items) { items.map { it.key } }
    val specsByKey = remember(items) { items.associateBy { it.key } }
    val focusTarget = remember(focusedKey, keys) {
        notflixCategoryFocusTarget(focusedKey, keys)
    }

    val headerItem = focusedKey?.let { specsByKey[it] }?.let(headerFor)
        ?: fallbackHeader
        ?: focusTarget?.let { specsByKey[it] }?.let(headerFor)

    Box(modifier.fillMaxSize().background(Spec.Background)) {
        key(current?.key ?: "notflix-category-empty") {
            val gridState = rememberLazyGridState()
            val selectedChipRequester = remember { FocusRequester() }
            var lastRequestedTotal by remember { mutableIntStateOf(-1) }
            val headerItemCount = if (showTabs) 2 else 1

            LaunchedEffect(current?.key, gridState, items.size, current?.hasMore, current?.isLoading) {
                val tab = current ?: return@LaunchedEffect
                if (!tab.hasMore) return@LaunchedEffect
                snapshotFlow {
                    val info = gridState.layoutInfo
                    ((info.visibleItemsInfo.lastOrNull()?.index ?: -1) - headerItemCount) to items.size
                }
                    .distinctUntilChanged()
                    .collect { (lastPoster, total) ->
                        if (lastPoster < total - NOTFLIX_CATEGORY_LOAD_MORE_THRESHOLD) {
                            lastRequestedTotal = -1
                        }
                        val shouldLoad = notflixCategoryShouldLoadMore(
                            lastVisiblePosterIndex = lastPoster,
                            totalPosters = total,
                            hasMore = tab.hasMore,
                            isLoading = tab.isLoading
                        )
                        if (shouldLoad && lastRequestedTotal != total) {
                            lastRequestedTotal = total
                            onLoadMore(tabIndex)
                        }
                    }
            }

            // Without posters the active chip takes the focus, so the remote is never stranded.
            val emptyAndIdle = current == null ||
                (!current.isLoading && current.error == null && items.isEmpty())
            LaunchedEffect(emptyAndIdle, showTabs, tabIndex) {
                if (emptyAndIdle && showTabs) {
                    selectedChipRequester.requestFocusAfterFrames(frames = 2)
                }
            }

            NotflixPosterGrid(
                items = items,
                onClick = { spec -> onPosterClick(tabIndex, spec) },
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                saveKey = notflixCategoryGridSaveKey(saveScope, current?.key ?: "empty"),
                initialFocusKey = focusTarget,
                requestInitialFocus = items.isNotEmpty(),
                onFocused = { spec ->
                    focusedKey = spec.key
                    onPosterFocus?.invoke(tabIndex, spec)
                },
                onLongClick = onPosterLongClick?.let { press ->
                    { spec -> press(tabIndex, spec) }
                },
                header = {
                    // Promoter (08-11): no focused-title header on list pages; title and grid only.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NotflixCategoryTitleRow(
                            title = title,
                            collectionLabel = collectionLabel
                        )
                    }
                    if (showTabs) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NotflixCategoryTabs(
                                tabs = tabs,
                                selectedIndex = tabIndex,
                                selectedRequester = selectedChipRequester,
                                onSelect = onSelectTab
                            )
                        }
                    }
                    when {
                        current != null && current.isLoading && items.isEmpty() -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                NotflixCategoryGridSkeleton()
                            }
                        }
                        current != null && !current.isLoading && items.isEmpty() ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                if (current.error != null) {
                                    NotflixCategoryInlineMessage(
                                        title = stringResource(R.string.notflix_category_error_title),
                                        message = current.error,
                                        actionLabel = stringResource(R.string.notflix_retry),
                                        onAction = { onRetry(tabIndex) }
                                    )
                                } else {
                                    NotflixCategoryInlineMessage(
                                        title = emptyText,
                                        message = emptyMessage,
                                        actionLabel = null,
                                        onAction = null
                                    )
                                }
                            }
                        current == null ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                NotflixCategoryInlineMessage(
                                    title = emptyText,
                                    message = emptyMessage,
                                    actionLabel = null,
                                    onAction = null
                                )
                            }
                    }
                }
            )
        }
    }
}

@Composable
private fun NotflixCategoryTitleRow(title: String, collectionLabel: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Spec.Text,
            fontFamily = Spec.BrandFont,
            fontSize = 56.sp,
            lineHeight = 58.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag("notflix-category-title")
        )
        if (!collectionLabel.isNullOrBlank()) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = collectionLabel,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("notflix-category-collection")
            )
        }
    }
}

@Composable
private fun NotflixCategoryTabs(
    tabs: List<NotflixCategoryTab>,
    selectedIndex: Int,
    selectedRequester: FocusRequester,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 4.dp, bottom = 10.dp)
            .testTag("notflix-category-tabs"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.key }) { index, tab ->
            NotflixChip(
                label = tab.label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = if (index == selectedIndex) {
                    Modifier.focusRequester(selectedRequester)
                } else {
                    Modifier
                },
                testTag = "notflix-category-tab-${tab.key}"
            )
        }
    }
}

/** Two rows of six blocks under the header while a catalog loads for the first time. */
@Composable
private fun NotflixCategoryGridSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("notflix-category-grid-skeleton"),
        verticalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)) {
                repeat(NotflixPosterMetrics.COLUMNS) {
                    NotflixSkeletonBlock(
                        Modifier
                            .width(NotflixPosterMetrics.Width)
                            .height(NotflixPosterMetrics.Height)
                    )
                }
            }
        }
    }
}

/** Whole page skeleton: header band, title line and two poster rows. */
@Composable
internal fun NotflixCategorySkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .testTag("notflix-category-skeleton")
    ) {
        NotflixSkeletonBlock(Modifier.fillMaxWidth().height(230.dp), radius = 0.dp)
        Column(Modifier.padding(horizontal = Spec.GUTTER.dp, vertical = 16.dp)) {
            NotflixSkeletonBlock(Modifier.width(300.dp).height(52.dp))
            Spacer(Modifier.height(12.dp))
            NotflixSkeletonBlock(Modifier.width(360.dp).height(30.dp))
            Spacer(Modifier.height(18.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)) {
                    repeat(NotflixPosterMetrics.COLUMNS) {
                        NotflixSkeletonBlock(
                            Modifier
                                .width(NotflixPosterMetrics.Width)
                                .height(NotflixPosterMetrics.Height)
                        )
                    }
                }
                Spacer(Modifier.height(NotflixPosterMetrics.Spacing))
            }
        }
    }
}

/** Full page message: no data to paint (missing folder, page level failure). */
@Composable
internal fun NotflixCategoryMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(actionLabel) {
        if (actionLabel != null) requester.requestFocusAfterFrames()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(horizontal = Spec.GUTTER.dp)
            .testTag("notflix-category-message"),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            NotflixPrimaryButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .focusRequester(requester),
                testTag = "notflix-category-message-action"
            )
        }
    }
}

/** Message inside the grid (tab error, empty catalog), below header and chips. */
@Composable
private fun NotflixCategoryInlineMessage(
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(actionLabel) {
        if (actionLabel != null) requester.requestFocusAfterFrames()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .testTag("notflix-category-inline-message"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 21.sp
            )
        }
        if (actionLabel != null && onAction != null) {
            NotflixPrimaryButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .focusRequester(requester),
                testTag = "notflix-category-inline-action"
            )
        }
    }
}
