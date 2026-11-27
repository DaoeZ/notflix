package com.nuvio.tv.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.ui.screens.home.RatingBadge
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/** One grid for every catalog: the first appearance wins and an id never repeats. */
private const val NOTFLIX_SEARCH_COLUMNS = 4
private const val NOTFLIX_KEY_COLUMNS = 6
/** System-keyboard mode: the results take the whole width, so more columns fit. */
private const val NOTFLIX_SYSTEM_SEARCH_COLUMNS = 6
/** Keep artwork requests around the visible rows. */
private const val NOTFLIX_TITLE_LOOKUPS = 16
private const val NOTFLIX_PLACEHOLDER_PREFIX = "__placeholder_"
private val NotflixSearchKeys: List<Char> = ('A'..'Z') + ('0'..'9')

/**
 * One unified list for every catalog row; placeholder skeletons never leak into the results grid.
 * Without a query the first appearance order wins. With one (promoter 08-11: «breaking» put
 * Breaking Bad 22nd, behind every movie row result) titles are ranked across rows by how well they
 * match, then by each addon's own relevance position, then by rating, then by row order.
 */
internal fun notflixSearchResults(rows: List<CatalogRow>, query: String = ""): List<MetaPreview> {
    data class Hit(val item: MetaPreview, val position: Int, val row: Int)
    val best = LinkedHashMap<String, Hit>()
    rows.forEachIndexed { rowIndex, row ->
        row.items.forEachIndexed { position, item ->
            if (item.id.startsWith(NOTFLIX_PLACEHOLDER_PREFIX)) return@forEachIndexed
            val key = notflixDiscoverKey(item)
            val previous = best[key]
            if (previous == null) {
                best[key] = Hit(item, position, rowIndex)
            } else if (item.originalTitle != null || previous.item.originalTitle != null) {
                val catalog = if (item.originalTitle == null) item else previous.item
                val tmdb = if (item.originalTitle != null) item else previous.item
                best[key] = Hit(catalog.copy(
                    name = tmdb.name, originalTitle = tmdb.originalTitle,
                    poster = tmdb.poster ?: catalog.poster, rawPosterUrl = catalog.rawPosterUrl ?: catalog.poster,
                    imdbRating = catalog.imdbRating ?: tmdb.imdbRating,
                    ratingSource = if (catalog.imdbRating != null) catalog.ratingSource else tmdb.ratingSource
                ), if (item.originalTitle != null) position else previous.position, rowIndex)
            } else if (position < previous.position) {
                best[key] = previous.copy(position = position)
            }
        }
    }
    val queryLower = normalizeSearchTitle(query)
    if (queryLower.isEmpty()) return best.values.map { it.item }
    return best.values.map { hit ->
        val rank = minOf(
            normalizedTitleMatchRank(hit.item.name, queryLower),
            hit.item.originalTitle?.let { normalizedTitleMatchRank(it, queryLower) } ?: Int.MAX_VALUE
        )
        hit to rank
    }.sortedWith(
        compareBy<Pair<Hit, Int>> { it.second }
            .thenByDescending { it.first.item.originalTitle != null }
            .thenBy { it.first.position }
            .thenByDescending { it.first.item.imdbRating ?: 0f }
            .thenBy { it.first.row }
    ).map { it.first.item }
}

/** 0 exact, 1 prefix, 2 a word starts with it, 3 contains, 4 every query word starts a title word, 5 other. */
private val NOTFLIX_WORD_SPLIT = Regex("""[^\p{L}\p{N}]+""")
private val NOTFLIX_LEADING_ARTICLE = Regex("^(the|el|la|los|las) ")
private val NOTFLIX_DIACRITICS = Regex("""\p{M}+""")

private fun normalizeSearchTitle(text: String): String =
    NOTFLIX_DIACRITICS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
        .trim()

internal fun notflixTitleMatchRank(title: String, query: String): Int =
    normalizedTitleMatchRank(title, normalizeSearchTitle(query))

private fun normalizedTitleMatchRank(title: String, queryLower: String): Int {
    val titleLower = normalizeSearchTitle(title)
    val withoutArticle = titleLower.replaceFirst(NOTFLIX_LEADING_ARTICLE, "")
    if (titleLower == queryLower || withoutArticle == queryLower) return 0
    if (titleLower.startsWith(queryLower) || withoutArticle.startsWith(queryLower)) return 1
    val words = titleLower.split(NOTFLIX_WORD_SPLIT).filter { it.isNotEmpty() }
    if (words.any { it.startsWith(queryLower) }) return 2
    if (titleLower.contains(queryLower)) return 3
    val queryWords = queryLower.split(NOTFLIX_WORD_SPLIT).filter { it.isNotEmpty() }
    if (queryWords.size > 1 && queryWords.all { q -> words.any { it.startsWith(q) } }) return 4
    return 5
}

/** Explore identity mirrors the ViewModel dedupe (apiType:id): equal ids of different types never collide. */
internal fun notflixDiscoverKey(item: MetaPreview): String = "${item.apiType}:${item.id}"

/**
 * Notflix presentation for Search. It owns no query logic: every key and the voice button call the
 * official SearchViewModel through [onQueryChange] and [onVoiceSearch].
 */
@Composable
fun NotflixSearchContent(
    query: String,
    submittedQuery: String,
    recentSearches: List<String>,
    discoverResults: List<MetaPreview>,
    catalogRows: List<CatalogRow>,
    isSearching: Boolean,
    error: String?,
    isVoiceSearchAvailable: Boolean,
    isVoiceListening: Boolean,
    onQueryChange: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onOpenCompanion: (() -> Unit)? = null,
    onRetry: () -> Unit,
    onRememberSearch: () -> Unit = {},
    onSubmitSearch: () -> Unit = onRememberSearch,
    onNavigateToDetail: (String, String, String) -> Unit,
    onItemFocused: (MetaPreview) -> Unit = {},
    onItemSelected: (MetaPreview) -> Unit = {},
    localizedTitles: Map<String, String> = emptyMap(),
    localizedPosters: Map<String, String> = emptyMap(),
    localizedRatings: Map<String, Float> = emptyMap(),
    onRequestTitles: (List<MetaPreview>) -> Unit = {},
    modifier: Modifier = Modifier,
    recommendedResults: List<MetaPreview> = emptyList(),
    recommendationTitle: String? = null,
    /** Search mode chosen in Settings: false = Notflix on-screen keyboard, true = text field with the system keyboard. */
    systemKeyboard: Boolean = false
) {
    val trimmedQuery = query.trim()
    val hasValidQuery = trimmedQuery.length >= MIN_SEARCH_QUERY_LENGTH
    val results = remember(catalogRows, submittedQuery, trimmedQuery) {
        notflixSearchResults(catalogRows, submittedQuery.ifBlank { trimmedQuery })
    }
    val hasPendingQuery = hasValidQuery && trimmedQuery != submittedQuery.trim()
    val showSkeletons = (isSearching || hasPendingQuery) && results.isEmpty()

    val keyRequesters = remember { List(NotflixSearchKeys.size + 2) { FocusRequester() } }
    val firstResultRequester = remember { FocusRequester() }
    val resultsGridState = rememberLazyGridState()
    val discoverGridState = rememberLazyGridState()
    val startRecommendations = remember(recommendedResults) { recommendedResults.distinctBy(::notflixDiscoverKey) }
    val startExplore = remember(discoverResults, startRecommendations) {
        val recommended = startRecommendations.map(::notflixDiscoverKey).toSet()
        discoverResults.filterNot { notflixDiscoverKey(it) in recommended }
    }
    val lookupItems = if (trimmedQuery.isNotEmpty()) results else startRecommendations + startExplore
    val lookupGrid = if (hasValidQuery) resultsGridState else discoverGridState
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, lookupItems, lookupGrid, onRequestTitles) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                snapshotFlow {
                    val first = lookupGrid.firstVisibleItemIndex.coerceIn(0, lookupItems.size)
                    val count = minOf(NOTFLIX_TITLE_LOOKUPS, lookupItems.size - first)
                    lookupItems.subList(first, first + count).toList()
                }
                    .distinctUntilChanged()
                    .collect { onRequestTitles(it) }
            } finally {
                onRequestTitles(emptyList())
            }
        }
    }
    val scope = rememberCoroutineScope()
    var lastFocusedKey by remember { mutableIntStateOf(0) }

    fun focusKey(index: Int) {
        val target = index.coerceIn(0, keyRequesters.lastIndex)
        scope.launch {
            withFrameNanos { }
            runCatching { keyRequesters[target].requestFocus() }
        }
    }

    fun returnToKeyboard() = focusKey(lastFocusedKey)

    fun focusFirstResult() {
        scope.launch {
            val grid = if (trimmedQuery.isEmpty()) discoverGridState else resultsGridState
            if (grid.firstVisibleItemIndex != 0) grid.scrollToItem(0)
            repeat(2) { withFrameNanos { } }
            runCatching { firstResultRequester.requestFocus() }
        }
    }

    val fieldRequester = remember { FocusRequester() }

    LaunchedEffect(systemKeyboard) {
        // The on-screen keyboard (or the text field) is the entry point; the shell's restorer still works afterwards.
        repeat(2) { withFrameNanos { } }
        runCatching { (if (systemKeyboard) fieldRequester else keyRequesters[0]).requestFocus() }
    }

    val resultsPane: @Composable (Int, Boolean, () -> Unit) -> Unit = { columns, backOnLeftEdge, onBack ->
        if (trimmedQuery.isEmpty()) {
            NotflixSearchStart(
                gridState = discoverGridState,
                recentSearches = recentSearches,
                discoverResults = startExplore,
                recommendedResults = startRecommendations,
                recommendationTitle = recommendationTitle,
                firstResultRequester = firstResultRequester,
                onBackToKeyboard = onBack,
                onRecentSearchClick = onRecentSearchClick,
                onItemFocused = onItemFocused,
                onItemSelected = onItemSelected,
                onNavigateToDetail = onNavigateToDetail,
                localizedTitles = localizedTitles,
                localizedPosters = localizedPosters,
                localizedRatings = localizedRatings,
                columns = columns,
                backOnLeftEdge = backOnLeftEdge
            )
        } else {
            NotflixSearchResults(
                query = trimmedQuery,
                results = results,
                showSkeletons = showSkeletons,
                error = error,
                gridState = resultsGridState,
                firstResultRequester = firstResultRequester,
                onBackToKeyboard = onBack,
                onRetry = onRetry,
                onItemFocused = onItemFocused,
                onItemSelected = onItemSelected,
                onNavigateToDetail = onNavigateToDetail,
                localizedTitles = localizedTitles,
                localizedPosters = localizedPosters,
                localizedRatings = localizedRatings,
                columns = columns,
                backOnLeftEdge = backOnLeftEdge
            )
        }
    }

    if (systemKeyboard) {
        NotflixSystemKeyboardSearch(
            query = query,
            fieldRequester = fieldRequester,
            isVoiceSearchAvailable = isVoiceSearchAvailable,
            isVoiceListening = isVoiceListening,
            onQueryChange = onQueryChange,
            onVoiceSearch = onVoiceSearch,
            onOpenCompanion = onOpenCompanion,
            canMoveToResults = (hasValidQuery && !hasPendingQuery && results.isNotEmpty()) ||
                (trimmedQuery.isEmpty() && lookupItems.isNotEmpty()),
            onSubmit = { if (hasValidQuery) onSubmitSearch() },
            onRemember = { if (hasValidQuery) onRememberSearch() },
            onMoveToResults = ::focusFirstResult,
            modifier = modifier
        ) {
            // Left from the first column goes to the side menu as anywhere else; Back returns to the field.
            resultsPane(NOTFLIX_SYSTEM_SEARCH_COLUMNS, false) {
                scope.launch {
                    withFrameNanos { }
                    runCatching { fieldRequester.requestFocus() }
                }
            }
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(horizontal = Spec.GUTTER.dp, vertical = 12.dp)
            .testTag("notflix-search"),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.30f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (query.isBlank()) {
                        stringResource(R.string.notflix_search_prompt)
                    } else {
                        query
                    },
                    color = if (query.isBlank()) Spec.Secondary.copy(alpha = 0.6f) else Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("notflix-search-query")
                )
                if (isVoiceSearchAvailable) {
                    NotflixMicButton(isVoiceListening, onVoiceSearch)
                }
                if (onOpenCompanion != null) {
                    Spacer(Modifier.size(6.dp))
                    var phoneFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (phoneFocused) Spec.Text else Color.White.copy(alpha = 0.12f))
                            .onFocusChanged { phoneFocused = it.isFocused }
                            .clickable(role = Role.Button, onClick = onOpenCompanion),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.tv.material3.Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = stringResource(R.string.companion_button_cd),
                            tint = if (phoneFocused) Spec.Background else Spec.Text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            NotflixKeyboard(
                requesters = keyRequesters,
                onFocused = { lastFocusedKey = it },
                onCharacter = { onQueryChange(query + it) },
                onSpace = { onQueryChange("$query ") },
                onBackspace = { if (query.isNotEmpty()) onQueryChange(query.dropLast(1)) },
                onClear = { if (query.isNotEmpty()) onQueryChange("") },
                onRightEdge = {
                    if ((hasValidQuery && results.isNotEmpty()) || (trimmedQuery.isEmpty() && lookupItems.isNotEmpty())) {
                        if (hasValidQuery) onRememberSearch()
                        focusFirstResult()
                        true
                    } else {
                        false
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(0.70f).fillMaxHeight()) {
            resultsPane(NOTFLIX_SEARCH_COLUMNS, true, ::returnToKeyboard)
        }
    }
}

@Composable
private fun NotflixKeyboard(
    requesters: List<FocusRequester>,
    onFocused: (Int) -> Unit,
    onCharacter: (Char) -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onRightEdge: () -> Boolean
) {
    val spaceIndex = NotflixSearchKeys.size
    val backspaceIndex = NotflixSearchKeys.size + 1
    val keyRows = remember { NotflixSearchKeys.chunked(NOTFLIX_KEY_COLUMNS) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keyRows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEachIndexed { columnIndex, char ->
                    val index = rowIndex * NOTFLIX_KEY_COLUMNS + columnIndex
                    NotflixKey(
                        label = char.toString(),
                        tag = "notflix-key-$char",
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .focusRequester(requesters[index])
                            .onFocusChanged { if (it.isFocused) onFocused(index) }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionRight &&
                                    columnIndex == NOTFLIX_KEY_COLUMNS - 1
                                ) {
                                    onRightEdge()
                                } else {
                                    false
                                }
                            },
                        onClick = { onCharacter(char) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NotflixKey(
                label = stringResource(R.string.notflix_search_space),
                tag = "notflix-key-space",
                modifier = Modifier
                    .weight(4f)
                    .height(40.dp)
                    .focusRequester(requesters[spaceIndex])
                    .onFocusChanged { if (it.isFocused) onFocused(spaceIndex) },
                onClick = onSpace
            )
            NotflixKey(
                label = stringResource(R.string.notflix_search_backspace),
                tag = "notflix-key-backspace",
                modifier = Modifier
                    .weight(2f)
                    .height(40.dp)
                    .focusRequester(requesters[backspaceIndex])
                    .onFocusChanged { if (it.isFocused) onFocused(backspaceIndex) }
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type != KeyEventType.KeyDown -> false
                            event.key == Key.DirectionRight -> onRightEdge()
                            event.nativeKeyEvent.isLongPress &&
                                (event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.Backspace ||
                                    event.key == Key.Delete) -> {
                                onClear()
                                true
                            }
                            else -> false
                        }
                    },
                onClick = onBackspace
            )
        }
    }
}

@Composable
private fun NotflixKey(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) Spec.Text else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (focused) Color.Transparent else Spec.Secondary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Spec.Background else Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = if (label.length > 1) 13.sp else 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Search with the system keyboard (Settings › Appearance › Search keyboard): a text field on top that
 * opens the device keyboard (Gboard and its own microphone) and the results across the whole width.
 * Same query, voice and companion calls as the Notflix keyboard; only the input changes.
 */
@Composable
private fun NotflixSystemKeyboardSearch(
    query: String,
    fieldRequester: FocusRequester,
    isVoiceSearchAvailable: Boolean,
    isVoiceListening: Boolean,
    onQueryChange: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onOpenCompanion: (() -> Unit)?,
    canMoveToResults: Boolean,
    onSubmit: () -> Unit,
    onRemember: () -> Unit,
    onMoveToResults: () -> Unit,
    modifier: Modifier = Modifier,
    results: @Composable () -> Unit
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(horizontal = Spec.GUTTER.dp, vertical = 12.dp)
            .testTag("notflix-search-system"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isVoiceSearchAvailable) NotflixMicButton(isVoiceListening, onVoiceSearch)
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Spec.Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    autoCorrectEnabled = false
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    onSubmit()
                    keyboard?.hide()
                    if (canMoveToResults) onMoveToResults()
                }),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(fieldRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    onSubmit()
                                    keyboard?.hide()
                                    if (canMoveToResults) onMoveToResults()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (canMoveToResults) {
                                    if (event.type == KeyEventType.KeyDown) {
                                        keyboard?.hide()
                                        onRemember()
                                        onMoveToResults()
                                    }
                                    true
                                } else {
                                    if (event.type == KeyEventType.KeyDown) keyboard?.hide()
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                    .testTag("notflix-search-field"),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Spec.Surface)
                            .border(2.dp, if (fieldFocused) Spec.Text else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                color = Spec.Secondary.copy(alpha = 0.6f),
                                fontFamily = Spec.BodyFont,
                                fontSize = 22.sp
                            )
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                NotflixRoundIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_clear_search),
                    tag = "notflix-search-clear"
                ) {
                    onQueryChange("")
                    runCatching { fieldRequester.requestFocus() }
                }
            }
            if (onOpenCompanion != null) {
                NotflixRoundIconButton(
                    icon = Icons.Default.Phone,
                    contentDescription = stringResource(R.string.companion_button_cd),
                    tag = "notflix-search-companion",
                    onClick = onOpenCompanion
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { results() }
    }
}

@Composable
private fun NotflixRoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) Spec.Text else Color.White.copy(alpha = 0.12f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        androidx.tv.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Spec.Background else Spec.Text,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NotflixMicButton(isVoiceListening: Boolean, onVoiceSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Spec.Text else Spec.Surface.copy(alpha = 0.5f))
            .border(
                width = 2.dp,
                color = if (isVoiceListening) Spec.Accent else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .clickable(role = Role.Button, onClick = onVoiceSearch)
            .testTag("notflix-search-voice"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = stringResource(R.string.cd_voice_search),
            tint = when {
                focused -> Spec.Background
                isVoiceListening -> Spec.Accent
                else -> Spec.Secondary
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NotflixSearchStart(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    recentSearches: List<String>,
    discoverResults: List<MetaPreview>,
    onRecentSearchClick: (String) -> Unit,
    onItemFocused: (MetaPreview) -> Unit,
    onItemSelected: (MetaPreview) -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    localizedTitles: Map<String, String> = emptyMap(),
    localizedPosters: Map<String, String> = emptyMap(),
    localizedRatings: Map<String, Float> = emptyMap(),
    recommendedResults: List<MetaPreview> = emptyList(),
    recommendationTitle: String? = null,
    firstResultRequester: FocusRequester,
    onBackToKeyboard: () -> Unit,
    columns: Int = NOTFLIX_SEARCH_COLUMNS,
    backOnLeftEdge: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    BackHandler(focused) { onBackToKeyboard() }
    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().onFocusChanged { focused = it.hasFocus },
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (recommendedResults.isNotEmpty()) {
            item(key = "recommended-title", span = { GridItemSpan(maxLineSpan) }) {
                NotflixSectionTitle(recommendationTitle ?: stringResource(R.string.notflix_search_explore))
            }
            itemsIndexed(recommendedResults, key = { _, item -> "recommended:${notflixDiscoverKey(item)}" }) { index, item ->
                NotflixSearchPoster(item = item, title = localizedTitles[notflixDiscoverKey(item)] ?: item.name,
                    localizedPoster = localizedPosters[notflixDiscoverKey(item)],
                    localizedRating = localizedRatings[notflixDiscoverKey(item)],
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        .then(if (index == 0) Modifier.focusRequester(firstResultRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && backOnLeftEdge && index % columns == 0) {
                                onBackToKeyboard(); true
                            } else false
                        }.testTag("notflix-search-recommended-${item.id}"),
                    onFocused = { onItemFocused(item) }, onClick = {
                        onItemSelected(item); onNavigateToDetail(item.id, item.apiType, item.sourceAddonBaseUrl.orEmpty())
                    })
            }
        }
        if (recentSearches.isNotEmpty()) {
            item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    NotflixSectionTitle(stringResource(R.string.notflix_search_recent))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItemsIndexed(recentSearches, key = { index, recent -> "$index-$recent" }) { index, recent ->
                            Box(
                                modifier = Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && backOnLeftEdge && index == 0) {
                                        onBackToKeyboard(); true
                                    } else false
                                }
                            ) {
                                NotflixRecentChip(
                                    text = recent,
                                    tag = "notflix-search-recent-$index",
                                    onClick = { onRecentSearchClick(recent) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (discoverResults.isNotEmpty()) {
            item(key = "explore-title", span = { GridItemSpan(maxLineSpan) }) {
                NotflixSectionTitle(stringResource(R.string.notflix_search_explore))
            }
            itemsIndexed(discoverResults, key = { _, item -> "explore:${notflixDiscoverKey(item)}" }) { index, item ->
                NotflixSearchPoster(
                    item = item,
                    title = localizedTitles[notflixDiscoverKey(item)] ?: item.name,
                    localizedPoster = localizedPosters[notflixDiscoverKey(item)],
                    localizedRating = localizedRatings[notflixDiscoverKey(item)],
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .then(if (index == 0 && recommendedResults.isEmpty()) Modifier.focusRequester(firstResultRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && backOnLeftEdge && index % columns == 0) {
                                onBackToKeyboard(); true
                            } else false
                        }
                        .testTag("notflix-search-explore-${item.id}"),
                    onFocused = { onItemFocused(item) },
                    onClick = {
                        onItemSelected(item)
                        onNavigateToDetail(item.id, item.apiType, item.sourceAddonBaseUrl.orEmpty())
                    }
                )
            }
        }
    }
}

@Composable
private fun NotflixSearchResults(
    query: String,
    results: List<MetaPreview>,
    showSkeletons: Boolean,
    error: String?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    firstResultRequester: FocusRequester,
    onBackToKeyboard: () -> Unit,
    onRetry: () -> Unit,
    onItemFocused: (MetaPreview) -> Unit,
    onItemSelected: (MetaPreview) -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    localizedTitles: Map<String, String> = emptyMap(),
    localizedPosters: Map<String, String> = emptyMap(),
    localizedRatings: Map<String, Float> = emptyMap(),
    columns: Int = NOTFLIX_SEARCH_COLUMNS,
    backOnLeftEdge: Boolean = true
) {
    var gridFocused by remember { mutableStateOf(false) }
    // Typing reorders the results and LazyGrid follows its old first item down the list (promoter
    // 09-11); while the keyboard has focus every new list starts at the first row.
    val shownResults = remember { arrayOfNulls<List<MetaPreview>>(1) }
    SideEffect {
        if (shownResults[0] !== results) {
            shownResults[0] = results
            if (!gridFocused) gridState.requestScrollToItem(0)
        }
    }
    val showGrid = !showSkeletons && results.isNotEmpty()
    BackHandler(enabled = showGrid && gridFocused) { onBackToKeyboard() }

    when {
        error != null && results.isEmpty() -> NotflixSearchError(error, onRetry)

        showSkeletons -> NotflixSearchSkeleton(columns)

        results.isNotEmpty() -> Box(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { gridFocused = it.hasFocus }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("notflix-search-results"),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(results, key = { _, item -> notflixDiscoverKey(item) }) { index, item ->
                    NotflixSearchPoster(
                        item = item,
                        title = localizedTitles[notflixDiscoverKey(item)] ?: item.name,
                        localizedPoster = localizedPosters[notflixDiscoverKey(item)],
                        localizedRating = localizedRatings[notflixDiscoverKey(item)],
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .then(
                                if (index == 0) Modifier.focusRequester(firstResultRequester) else Modifier
                            )
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionLeft &&
                                    backOnLeftEdge && index % columns == 0
                                ) {
                                    onBackToKeyboard()
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag("notflix-search-result-${item.id}"),
                        onFocused = { onItemFocused(item) },
                        onClick = {
                            onItemSelected(item)
                            onNavigateToDetail(item.id, item.apiType, item.sourceAddonBaseUrl.orEmpty())
                        }
                    )
                }
            }
        }

        else -> Column(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
            Text(
                text = stringResource(R.string.notflix_search_no_results, query),
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 20.sp,
                lineHeight = 26.sp
            )
        }
    }
}

@Composable
private fun NotflixSearchPoster(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    title: String = item.name,
    localizedPoster: String? = null,
    localizedRating: Float? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember(item.id) { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(Spec.FOCUS_MS),
        label = "notflixSearchPoster"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(4.dp))
            .background(Spec.Surface)
            .border(
                width = 2.dp,
                color = if (focused) Spec.Text else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
    ) {
        // A poster URL that fails to load left an empty tile (promoter 09-11): fall back to the title, as Home does.
        val candidates = remember(localizedPoster, item.poster, item.rawPosterUrl) {
            listOfNotNull(localizedPoster, item.poster, item.rawPosterUrl)
                .filter { it.startsWith("https://", true) || it.startsWith("http://", true) }.distinct()
        }
        var posterIndex by remember(candidates) { mutableIntStateOf(0) }
        val poster = candidates.getOrNull(posterIndex)
        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    if (candidates.getOrNull(posterIndex) == poster) posterIndex++
                }
            )
        } else {
            Text(
                text = title,
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
        val effectiveRating = item.imdbRating ?: localizedRating
        val effectiveSource = if (item.imdbRating != null) item.ratingSource else com.nuvio.tv.domain.model.RatingSource.TMDB
        RatingBadge(
            value = effectiveRating,
            source = effectiveSource,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
        )
    }
}

@Composable
private fun NotflixRecentChip(text: String, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .background(
                color = if (focused) Spec.Text else Spec.Surface,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 2.dp,
                color = if (focused) Spec.Text else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (focused) Spec.Background else Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NotflixSearchError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 18.sp,
            lineHeight = 24.sp
        )
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = if (focused) Spec.Accent else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(2.dp)
                .background(Spec.Text, RoundedCornerShape(3.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button, onClick = onRetry)
                .testTag("notflix-search-retry")
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.notflix_search_retry),
                color = Spec.Background,
                fontFamily = Spec.BodyFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun NotflixSearchSkeleton(columns: Int = NOTFLIX_SEARCH_COLUMNS) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().testTag("notflix-search-skeleton"),
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(columns * 3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Spec.Surface, RoundedCornerShape(4.dp))
                    .testTag("notflix-search-skeleton-$index")
            )
        }
    }
}

@Composable
private fun NotflixSectionTitle(text: String) {
    Text(
        text = text,
        color = Spec.Text,
        fontFamily = Spec.BodyFont,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}
