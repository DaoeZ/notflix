package com.nuvio.tv.ui.appearance.notflix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.home.NotflixCard
import com.nuvio.tv.ui.screens.home.NotflixCardAction
import com.nuvio.tv.ui.screens.home.NotflixCardActionUi
import com.nuvio.tv.ui.screens.home.NotflixCardActionsHost
import com.nuvio.tv.ui.screens.home.NotflixCardCursor
import com.nuvio.tv.ui.screens.home.NotflixCardKey
import com.nuvio.tv.ui.screens.home.NotflixCardKeyResult
import com.nuvio.tv.ui.screens.home.NotflixPreviewSession
import com.nuvio.tv.ui.screens.home.NotflixTile
import com.nuvio.tv.ui.screens.home.PreviewCandidate
import com.nuvio.tv.ui.screens.home.PreviewVisit
import com.nuvio.tv.ui.screens.home.notflixCardActions
import com.nuvio.tv.ui.screens.home.notflixCardCursorIndex
import com.nuvio.tv.ui.screens.home.notflixCardKey
import com.nuvio.tv.ui.screens.home.notflixMetaKey
import com.nuvio.tv.ui.screens.home.toCardKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

private val categoryDirections = setOf(
    androidx.compose.ui.input.key.Key.DirectionUp,
    androidx.compose.ui.input.key.Key.DirectionDown,
    androidx.compose.ui.input.key.Key.DirectionLeft,
    androidx.compose.ui.input.key.Key.DirectionRight
)

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

/** Beta.4: start of a [size]-long span kept inside [min, max]; pinned to [min] if it cannot fit. */
internal fun notflixCategoryClamp(start: Float, size: Float, min: Float, max: Float): Float =
    if (max - min <= size) min else start.coerceIn(min, max - size)

/**
 * M63: la tarjeta abierta se despliega hacia el lado contrario en la última columna, para no salir
 * de la pantalla. `true` = la columna final de la rejilla (se abre hacia el borde de inicio).
 */
internal fun notflixCategoryExpandToStart(index: Int, columns: Int): Boolean =
    columns > 0 && index >= 0 && index % columns == columns - 1

/**
 * M63: borde de inicio de la tarjeta abierta. El póster sigue en su sitio y la tarjeta crece hacia
 * el lado libre: a la derecha, o anclada al borde final del póster cuando se abre al revés.
 */
internal fun notflixCategoryExpandedOriginX(
    posterX: Float,
    posterWidth: Float,
    openWidth: Float,
    openToStart: Boolean
): Float = if (openToStart) posterX + posterWidth - openWidth else posterX

/** M63: identidad de la tarjeta de la rejilla, estable entre pestañas y visitas. */
internal fun notflixCategoryCardIdentity(tabKey: String, posterKey: String): String =
    "tab:$tabKey|$posterKey"

/** M63: el tráiler de la rejilla solo se pide con la tarjeta abierta y la rejilla quieta. */
internal fun notflixCategoryShouldPreview(expanded: Boolean, scrolling: Boolean): Boolean =
    expanded && !scrolling

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
    emptyMessage: String? = null,
    /** Datos oficiales de cada póster (la meta enriquecida), por clave de tarjeta. */
    itemsByKey: Map<String, MetaPreview> = emptyMap(),
    /** M63: con estos datos la tarjeta se abre; sin ellos la rejilla se queda como estaba. */
    cardActions: NotflixCardActionsHost? = null,
    previewSession: NotflixPreviewSession? = null,
    onCardPlay: ((MetaPreview) -> Unit)? = null,
    canPlay: Boolean = false,
    cardBase: (NotflixPosterSpec) -> String = { "" }
) {
    val tabIndex = notflixCategoryTabIndex(selectedTabIndex, tabs.size)
    val current = tabs.getOrNull(tabIndex)
    val items = current?.items.orEmpty()
    val showTabs = notflixCategoryShowTabs(tabs.size)
    val emptyText = emptyTitle ?: stringResource(R.string.notflix_category_empty)
    val cardSize = LocalNotflixPresentation.current.cardSize
    val presentation = LocalNotflixPresentation.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cardOpenDelayMs = Spec.cardOpenDelayMs(presentation.cardOpening)
    val expandable = cardActions != null

    val keys = remember(items) { items.map { it.key } }
    val specsByKey = remember(items) { items.associateBy { it.key } }

    Box(modifier.fillMaxSize().background(Spec.Background)) {
        key(current?.key ?: "notflix-category-empty") {
            val gridState = rememberLazyGridState()
            val selectedChipRequester = remember { FocusRequester() }
            var lastRequestedTotal by remember { mutableIntStateOf(-1) }
            val headerItemCount = if (showTabs) 2 else 1
            val tabKey = current?.key ?: "empty"
            val cards = itemsByKey

            // M63: la tarjeta abierta de la rejilla. El cursor se lee solo dentro de la tarjeta
            // abierta y en los manejadores de teclas: moverse por los botones no recompone la página.
            val focusedKey = remember { mutableStateOf<String?>(null) }
            val pendingHideKey = remember { mutableStateOf<String?>(null) }
            val expandedKey = remember { mutableStateOf<String?>(null) }
            val previewReadyKey = remember { mutableStateOf<String?>(null) }
            val focusEpoch = remember { mutableLongStateOf(0L) }
            val cardCursor = remember { mutableStateOf<NotflixCardCursor?>(null) }
            val expandedActions = remember { mutableStateOf<List<NotflixCardActionUi>>(emptyList()) }
            // M67: un botón que está guardando responde con un pulso, igual que en Inicio (M56).
            val busyPulse = remember { mutableIntStateOf(0) }
            val busyPulseIndex = remember { mutableIntStateOf(-1) }
            val columns = NotflixPosterMetrics.columns(cardSize)
            val posterWidth = NotflixPosterMetrics.width(cardSize)
            val posterHeight = NotflixPosterMetrics.height(cardSize)
            val currentCards = rememberUpdatedState(cards)
            val currentSpecs = rememberUpdatedState(specsByKey)
            val confirmSound = rememberNotflixConfirmSound()

            fun activateCardAction(action: NotflixCardActionUi?, spec: NotflixPosterSpec, position: Int = -1) {
                if (action == null) return
                // M67: un control que guarda no repite la escritura, pero la pulsación contesta (M56).
                if (!action.enabled) {
                    if (action.busy && position >= 0) {
                        busyPulseIndex.intValue = position
                        busyPulse.intValue++
                    }
                    return
                }
                val meta = cards[spec.key] ?: return
                confirmSound()
                when (action.action) {
                    NotflixCardAction.PLAY -> onCardPlay?.invoke(meta)
                    NotflixCardAction.INFO -> onPosterClick(tabIndex, spec)
                    NotflixCardAction.SOUND ->
                        previewSession?.toggleSound(notflixCategoryCardIdentity(tabKey, spec.key))
                    NotflixCardAction.LIBRARY -> cardActions?.onLibrary?.invoke(meta, cardBase(spec))
                    NotflixCardAction.WATCHED -> cardActions?.onWatched?.invoke(meta)
                    NotflixCardAction.HIDE -> {
                        pendingHideKey.value = spec.key
                        cardActions?.onHide?.invoke(meta)
                    }
                }
            }

            // M63: misma espera que M56. El foco sin moverse abre; desplazarse rápido no abre nada.
            LaunchedEffect(expandable, tabKey, cardOpenDelayMs) {
                snapshotFlow { Triple(focusedKey.value, gridState.isScrollInProgress, focusEpoch.longValue) }.collectLatest { (key, scrolling) ->
                    expandedKey.value = null
                    previewReadyKey.value = null
                    cardCursor.value = null
                    if (expandable && key != null && !scrolling) {
                        delay(cardOpenDelayMs)
                        expandedKey.value = key
                    }
                }
            }
            // Tráiler: mismo coordinador y misma sesión que Inicio. El candidato nace con el foco
            // (como en Inicio, la permanencia cuenta desde ahí) y solo arranca con la tarjeta
            // abierta y la rejilla quieta.
            LaunchedEffect(previewSession, tabKey, items) {
                val session = previewSession ?: return@LaunchedEffect
                snapshotFlow {
                    val spec = focusedKey.value?.let { currentSpecs.value[it] }
                    val meta = spec?.let { currentCards.value[it.key] }
                    val binding = session.binding
                    val openNow = spec != null && expandedKey.value == spec.key && previewReadyKey.value == spec.key
                    val candidate = if (binding != null && spec != null && meta != null) {
                        PreviewCandidate(
                            PreviewVisit(binding, notflixCategoryCardIdentity(tabKey, spec.key), notflixMetaKey(meta), focusEpoch.longValue),
                            meta,
                            notflixCategoryShouldPreview(openNow, gridState.isScrollInProgress)
                        )
                    } else null
                    candidate to gridState.isScrollInProgress
                }.collectLatest { pair -> session.card(pair.first, pair.second) }
            }
            DisposableEffect(previewSession, tabKey) {
                onDispose { previewSession?.card(null, false) }
            }
            NotflixCategoryCursorBack(cardCursor)

            val onCardKey: ((NotflixPosterSpec, KeyEvent) -> Boolean)? = if (!expandable) null else { spec, event ->
                val identity = notflixCategoryCardIdentity(tabKey, spec.key)
                val key = event.toCardKey()
                var handled = false
                if (expandedKey.value == spec.key && expandedActions.value.isNotEmpty() && key != NotflixCardKey.OTHER) {
                    val actions = expandedActions.value
                    val current = cardCursor.value
                    val (next, result) = notflixCardKey(
                        current, identity, actions.size, key,
                        event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount,
                        forward = !rtl, trailerPlaying = false
                    )
                    if (next != current) cardCursor.value = next
                    when (result) {
                        is NotflixCardKeyResult.Activate -> {
                            activateCardAction(actions.getOrNull(result.index), spec, result.index)
                            handled = true
                        }
                        // Abajo sale de los botones y baja de fila con el movimiento normal de la rejilla.
                        NotflixCardKeyResult.ExitDown -> cardCursor.value = null
                        NotflixCardKeyResult.Pass -> Unit
                        else -> handled = true
                    }
                }
                handled
            }

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
                if (emptyAndIdle && pendingHideKey.value != null && !showTabs) {
                    presentation.navigation?.focusTop()
                }
                if (emptyAndIdle && showTabs) {
                    selectedChipRequester.requestFocusAfterFrames(frames = 2)
                }
            }

            NotflixPosterGrid(
                items = items,
                onClick = { spec ->
                    val play = if (expandable && expandedKey.value == spec.key) {
                        expandedActions.value.firstOrNull { it.action == NotflixCardAction.PLAY && it.enabled }
                    } else null
                    if (play != null) activateCardAction(play, spec) else onPosterClick(tabIndex, spec)
                },
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                columns = columns,
                posterWidth = posterWidth,
                posterHeight = posterHeight,
                saveKey = notflixCategoryGridSaveKey(saveScope, current?.key ?: "empty"),
                initialFocusKey = null,
                requestInitialFocus = items.isNotEmpty(),
                onFocused = { spec ->
                    onPosterFocus?.invoke(tabIndex, spec)
                },
                onLongClick = onPosterLongClick?.let { press ->
                    { spec -> press(tabIndex, spec) }
                },
                onCardKey = onCardKey,
                onActualFocus = if (!expandable) null else { spec, focused ->
                    if (focused) {
                        if (focusedKey.value != spec.key) {
                            focusEpoch.longValue++
                            focusedKey.value = spec.key
                        }
                    } else if (focusedKey.value == spec.key) {
                        focusedKey.value = null
                        // Losing focus to tabs/chrome while the item still exists cancels repair.
                        if (spec.key in currentSpecs.value) pendingHideKey.value = null
                    }
                },
                covered = if (!expandable) null else { spec -> expandedKey.value == spec.key },
                removedFocusKey = if (!expandable) null else { { pendingHideKey.value } },
                headerItemCount = headerItemCount,
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

            // M63: la tarjeta abierta se dibuja encima de la rejilla, sin recolocar a las vecinas.
            if (cardActions != null) NotflixCategoryExpansion(
                expandedKey, previewReadyKey, cardCursor, expandedActions, tabKey, keys, specsByKey, cards,
                gridState, columns, rtl, cardActions, previewSession, canPlay, busyPulse, busyPulseIndex,
                onActivate = ::activateCardAction,
                onLongClick = onPosterLongClick?.let { press -> { spec -> press(tabIndex, spec) } }
            )
        }
    }
}

/** Reads expansion and action state only here; remote movement does not recompose the grid/page. */
@Composable
private fun NotflixCategoryExpansion(
    expandedKey: State<String?>,
    previewReadyKey: MutableState<String?>,
    cursor: MutableState<NotflixCardCursor?>,
    actionState: MutableState<List<NotflixCardActionUi>>,
    tabKey: String,
    keys: List<String>,
    specs: Map<String, NotflixPosterSpec>,
    cards: Map<String, MetaPreview>,
    gridState: LazyGridState,
    columns: Int,
    rtl: Boolean,
    host: NotflixCardActionsHost,
    preview: NotflixPreviewSession?,
    canPlay: Boolean,
    busyPulse: State<Int>,
    busyPulseIndex: State<Int>,
    onActivate: (NotflixCardActionUi?, NotflixPosterSpec, Int) -> Unit,
    onLongClick: ((NotflixPosterSpec) -> Unit)?
) {
    val spec = expandedKey.value?.let { specs[it] }
    val meta = spec?.let { cards[it.key] }
    if (spec == null || meta == null) {
        SideEffect { actionState.value = emptyList() }
        return
    }
    val identity = notflixCategoryCardIdentity(tabKey, spec.key)
    val actions = notflixCardActions(meta, host, preview?.sound(identity), canPlay)
    SideEffect { actionState.value = actions }
    LaunchedEffect(identity) { host.onExpanded(meta) }
    // M67 (M56): guardar cambia `enabled`; un cursor armado no puede sobrevivir a ese cambio.
    val actionStates = actions.joinToString(",") { if (it.enabled) "1" else "0" }
    LaunchedEffect(identity, actionStates) {
        val current = cursor.value
        if (current != null && current.identity == identity && current.armed) {
            cursor.value = current.copy(armed = false)
        }
    }
    NotflixCategoryExpandedCard(
        spec, meta, identity, gridState, keys.indexOf(spec.key), columns, rtl,
        actions, { cursor.value }, preview,
        busyPulse.value, busyPulseIndex.value,
        onActivate = { index -> onActivate(actions.getOrNull(index), spec, index) },
        onOpened = { previewReadyKey.value = spec.key },
        onClick = {
            val play = actions.indexOfFirst { it.action == NotflixCardAction.PLAY && it.enabled }
            onActivate(actions.getOrNull(play), spec, play)
        },
        onLongClick = onLongClick?.let { press -> { press(spec) } }
    )
}

/** M63: Arriba/Atrás cierra los botones de la tarjeta abierta sin salir de la página. */
@Composable
private fun NotflixCategoryCursorBack(cursor: MutableState<NotflixCardCursor?>) {
    BackHandler(enabled = cursor.value != null) { cursor.value = null }
}

/**
 * M63: la tarjeta abierta de una rejilla de colección. Se ancla al póster enfocado, crece hacia el
 * lado libre (izquierda en la última columna) y se dibuja por encima: las vecinas no se mueven. No
 * recibe foco; el mando sigue en el póster y el cursor de botones vive en la página.
 */
@Composable
private fun NotflixCategoryExpandedCard(
    spec: NotflixPosterSpec,
    meta: MetaPreview,
    identity: String,
    gridState: LazyGridState,
    index: Int,
    columns: Int,
    rtl: Boolean,
    actions: List<NotflixCardActionUi>,
    cursor: () -> NotflixCardCursor?,
    preview: NotflixPreviewSession?,
    busyPulse: Int,
    busyPulseIndex: Int,
    onActivate: (Int) -> Unit,
    onOpened: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == spec.key } ?: return
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val cardSize = LocalNotflixPresentation.current.cardSize
    val density = LocalDensity.current
    // GridCells.Fixed may constrain the item to a wider cell than the preferred poster width.
    val posterWidth = with(density) { info.size.width.toDp() }
    val posterHeight = with(density) { info.size.height.toDp() }
    val toStart = notflixCategoryExpandToStart(index, columns)
    val openToStart = if (rtl) !toStart else toStart
    // Beta.4 (promotor): the same expanded card as Home, artwork on top and actions, data and
    // synopsis below. It grows over the neighbours and only shifts as needed to stay on screen.
    val targetWidth = maxOf(Spec.expandedWidth(cardSize).dp, posterWidth)
    val targetHeight = maxOf(Spec.expandedHeight(cardSize).dp, posterHeight)
    // Item offsets exclude the grid start content padding (NotflixPosterGrid default: GUTTER).
    val gutterPx = with(density) { Spec.GUTTER.dp.toPx() }
    val progress = remember(identity) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(identity, reducedMotion) {
        if (reducedMotion) progress.snapTo(1f) else progress.animateTo(1f, tween(Spec.EXPAND_MS))
        onOpened()
    }
    val openWidth = posterWidth + (targetWidth - posterWidth) * progress.value
    val openHeight = posterHeight + (targetHeight - posterHeight) * progress.value
    val posterPx = with(density) { posterWidth.toPx() }
    val openWidthPx = with(density) { openWidth.toPx() }
    val openHeightPx = with(density) { openHeight.toPx() }
    val cursorIndex = notflixCardCursorIndex(true, cursor, identity, actions.size)
    Box(Modifier.fillMaxSize().zIndex(1f)) {
        Box(
            Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset {
                    // Se relee la posición del póster: si la rejilla se mueve, la tarjeta la sigue.
                    val fresh = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == spec.key } ?: info
                    val viewport = gridState.layoutInfo.viewportSize
                    val x = notflixCategoryClamp(
                        notflixCategoryExpandedOriginX(fresh.offset.x + gutterPx, posterPx, openWidthPx, openToStart),
                        openWidthPx, gutterPx, viewport.width - gutterPx
                    )
                    val y = notflixCategoryClamp(fresh.offset.y.toFloat(), openHeightPx, 0f, viewport.height.toFloat())
                    IntOffset(Math.round(x), Math.round(y))
                }
                .testTag("notflix-category-expanded")
        ) {
            NotflixCard(
                tile = NotflixTile(
                    key = spec.key,
                    canonical = notflixMetaKey(meta),
                    title = meta.name,
                    poster = spec.poster ?: meta.poster,
                    backdrop = meta.background ?: meta.landscapePoster,
                    meta = meta,
                    watched = spec.watched
                ),
                identity = identity,
                wide = false,
                expanded = true,
                modifier = Modifier,
                onLongClick = onLongClick,
                onClick = onClick,
                preview = preview,
                actions = actions,
                cursorIndex = cursorIndex,
                onActivate = onActivate,
                busyPulse = busyPulse,
                busyPulseIndex = busyPulseIndex,
                focusScaleOverride = 1f,
                interactive = false,
                looksFocused = true,
                widthOverride = openWidth,
                heightOverride = openHeight,
                panelProgressOverride = progress.value
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
    val cardSize = LocalNotflixPresentation.current.cardSize
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("notflix-category-grid-skeleton"),
        verticalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)) {
                repeat(NotflixPosterMetrics.columns(cardSize)) {
                    NotflixSkeletonBlock(
                        Modifier
                            .width(NotflixPosterMetrics.width(cardSize))
                            .height(NotflixPosterMetrics.height(cardSize))
                    )
                }
            }
        }
    }
}

/** Whole page skeleton: header band, title line and two poster rows. */
@Composable
internal fun NotflixCategorySkeleton() {
    val cardSize = LocalNotflixPresentation.current.cardSize
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
                    repeat(NotflixPosterMetrics.columns(cardSize)) {
                        NotflixSkeletonBlock(
                            Modifier
                                .width(NotflixPosterMetrics.width(cardSize))
                                .height(NotflixPosterMetrics.height(cardSize))
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
