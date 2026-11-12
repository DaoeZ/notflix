package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.R
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.ui.components.ContinueWatchingOptionsDialog
import com.nuvio.tv.ui.components.TrailerPlayer
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.RatingSource
import com.nuvio.tv.domain.model.stableItemKeys
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixNavigationState
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.notflixRating
import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.format.TextStyle

internal enum class NotflixNewEpisodeBadge { NEW_EPISODE, NEW_SEASON }

internal const val NOTFLIX_AIRING_ROW_KEY = "airing"

/**
 * M66: a KIDS profile hides everything whose age rating cannot be resolved, and without the TMDB
 * key nothing can be resolved. Home then explains why instead of showing a generic empty message.
 */
internal fun notflixKidsNeedsTmdbKey(ageLimit: com.nuvio.tv.core.profile.AgeLevel?, tmdbKeyAvailable: Boolean): Boolean =
    com.nuvio.tv.core.profile.isKidsAgeLimit(ageLimit) && !tmdbKeyAvailable

internal data class NotflixFullscreenTrailer(
    val identity: String,
    val title: String,
    val logo: String?,
    val source: PreviewSource,
    val startPositionMs: Long,
    val meta: MetaPreview,
    val base: String,
    /** Acción enfocada antes de Arriba; Abajo desde el arte del regreso vuelve a ella. */
    val actionIndex: Int = 0
)

/** Salida de la vista grande: siempre devuelve el foco a la tarjeta; solo Atrás/Volver reanuda el vídeo. */
internal data class NotflixFullscreenReturn(val identity: String, val resumePositionMs: Long?, val actionIndex: Int = 0)

internal data class NotflixTile(
    val key: String, val canonical: String, val title: String, val poster: String?,
    val backdrop: String? = null, val subtitle: String? = null, val meta: MetaPreview? = null,
    val cw: ContinueWatchingItem? = null, val folder: Pair<String, String>? = null,
    val progress: Float? = null, val watched: Boolean = false,
    val seeAllCatalog: Triple<String, String, String>? = null,
    val newEpisodeBadge: NotflixNewEpisodeBadge? = null,
    val caption: String? = null,
    /** «Ver todo» tiles: the first posters of the row, drawn as a small fanned collage. */
    val collage: List<String> = emptyList(),
    /** Collection folder tiles: the cover fields the official folder card already exposes. */
    val coverEmoji: String? = null,
    val tileShape: PosterShape = PosterShape.SQUARE,
    val hideTitle: Boolean = false
)
internal data class NotflixDisplayRow(
    val key: String, val loadKey: String, val title: String, val phase: NotflixRowPhase,
    val tiles: List<NotflixTile>, val wide: Boolean = false,
    val folderTarget: Pair<String, String>? = null, val empty: Boolean = false
)
private class NotflixFocusMoves {
    var job: Job? = null
    var serial = 0L
    var expected: String? = null
    var acquiring: String? = null
    var pending: NotflixFocusRecord? = null
    var requestLoop = false
}

internal fun notflixRows(state: HomeUiState, keepPendingCw: Boolean, cwTitle: String, episodePattern: String, seeAll: String,
    myListRow: NotflixCatalogRow? = null, becauseRow: NotflixCatalogRow? = null,
    airingTitle: String = "", airingCaption: (AiringEntry) -> String = { "" },
    airingNow: java.time.Instant = java.time.Instant.now(),
    airingZone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<NotflixDisplayRow> = buildList {
    if (notflixKidsNeedsTmdbKey(state.ageLimit, state.ageTmdbKeyAvailable)) return@buildList
    val hiddenKeys = state.hiddenTitleKeys
    val alertByContentId = state.continueWatchingItems.asSequence()
        .filterIsInstance<ContinueWatchingItem.NextUp>()
        .filter { it.info.isReleaseAlert }
        .associate { it.info.contentId to it.info.isNewSeasonRelease }
    val airing = airingThisWeek(state.upcomingItems, airingNow, airingZone)
        .filter { hiddenTitleKey(it.info.contentType, it.info.contentId) !in hiddenKeys }
        .mapNotNull { entry ->
            val meta = entry.info.toAiringMetaPreview()
            if (!state.allowsForAge(meta)) null else entry to meta
        }
    if (state.continueWatchingEnabled && (state.continueWatchingItems.isNotEmpty() ||
            (keepPendingCw && state.notflixCwPhase != NotflixRowPhase.READY))) {
        add(NotflixDisplayRow("cw", "cw", cwTitle, state.notflixCwPhase,
            state.continueWatchingItems.map { item ->
                when (item) {
                    is ContinueWatchingItem.InProgress -> item.progress.let { p ->
                        NotflixTile(continueWatchingItemKey(item), "${p.contentType}:${p.contentId}", p.name,
                            p.poster, item.episodeThumbnail ?: p.backdrop,
                            p.season?.let { episodePattern.format(it, p.episode ?: "?") }, cw = item, progress = p.progressPercentage)
                    }
                    is ContinueWatchingItem.NextUp -> item.info.let { p ->
                        NotflixTile(continueWatchingItemKey(item), "${p.contentType}:${p.contentId}", p.name,
                            p.poster, p.thumbnail ?: p.backdrop, episodePattern.format(p.season, p.episode), cw = item)
                    }
                }
            }, wide = true))
    }
    if (airing.isNotEmpty() && NOTFLIX_AIRING_ROW_KEY !in state.disabledHomeCatalogKeys) {
        add(NotflixDisplayRow(
            key = NOTFLIX_AIRING_ROW_KEY,
            loadKey = NOTFLIX_AIRING_ROW_KEY,
            title = airingTitle,
            phase = NotflixRowPhase.READY,
            tiles = airing.map { (entry, meta) ->
                NotflixTile(
                    key = "airing:${entry.info.contentType}:${entry.info.contentId}",
                    canonical = notflixMetaKey(meta),
                    title = meta.name,
                    poster = meta.poster,
                    backdrop = meta.background ?: meta.landscapePoster,
                    meta = meta,
                    caption = airingCaption(entry)
                )
            }
        ))
    }
    // Personal rows (M28): My list right after Continue watching, Because after the first catalogue row.
    val catalogRows = state.notflixCatalog.rows
    val ordered = buildList {
        myListRow?.let(::add)
        catalogRows.forEachIndexed { index, row -> add(row); if (index == 0) becauseRow?.let(::add) }
        if (catalogRows.isEmpty()) becauseRow?.let(::add)
    }
    ordered.forEach { row ->
        val tiles = if (row.collection != null) row.collection.folders.map { folder ->
            NotflixTile(folder.id, "folder:${row.collection.id}:${folder.id}", folder.title,
                folder.coverImageUrl, folder.heroBackdropUrl, folder = row.collection.id to folder.id,
                coverEmoji = folder.coverEmoji, tileShape = folder.tileShape, hideTitle = folder.hideTitle)
        } else row.catalog?.let { catalog ->
            val ageItems = if (state.ageLimit == null) catalog.items else catalog.items.filter { state.allowsForAge(it) }
            val items = if (hiddenKeys.isEmpty()) ageItems else ageItems.filterNot { it.isHiddenTitle(hiddenKeys) }
            val keys = if (items === catalog.items) catalog.stableItemKeys() else catalog.copy(items = items).stableItemKeys()
            items.mapIndexed { index, item ->
                val isSeries = item.apiType.equals("series", ignoreCase = true) ||
                    item.apiType.equals("tv", ignoreCase = true)
                val watched = if (isSeries) {
                    item.id in state.fullyWatchedSeriesIds
                } else {
                    item.id in state.watchedMovieIds
                }
                val newEpisodeBadge = if (isSeries) {
                    alertByContentId[item.id]?.let { newSeason ->
                        if (newSeason) NotflixNewEpisodeBadge.NEW_SEASON else NotflixNewEpisodeBadge.NEW_EPISODE
                    }
                } else null
                NotflixTile(keys[index], notflixMetaKey(item), item.name, item.poster,
                    item.background ?: item.landscapePoster, meta = item, watched = watched,
                    newEpisodeBadge = newEpisodeBadge)
            }
        }.orEmpty()
        val folderAction = row.folderTarget?.let { target ->
            NotflixTile("see-all", "folder:${target.first}:${target.second}", seeAll, null, folder = target,
                collage = tiles.mapNotNull { it.poster }.take(3))
        }
        val catalogAction = row.catalog?.takeIf { row.folderTarget == null && it.addonId != "notflix" && tiles.isNotEmpty() }?.let { catalog ->
            NotflixTile(
                key = "see-all",
                canonical = "catalog:${row.key}",
                title = seeAll,
                poster = null,
                seeAllCatalog = Triple(catalog.catalogId, catalog.addonId, catalog.apiType),
                collage = tiles.mapNotNull { it.poster }.take(3)
            )
        }
        add(NotflixDisplayRow(row.key, row.loadKey, row.title, row.phase,
            if (row.folderTarget != null && row.phase == NotflixRowPhase.PENDING && tiles.isEmpty()) emptyList()
            else tiles + listOfNotNull(folderAction, catalogAction),
            wide = row.collection?.let { notflixCollectionRowIsWide(it.folders) } == true,
            folderTarget = row.folderTarget,
            empty = row.catalog?.let { catalog ->
                catalog.items.isEmpty() || catalog.items.none {
                    state.allowsForAge(it) && !it.isHiddenTitle(hiddenKeys)
                }
            } == true && row.phase == NotflixRowPhase.READY))
    }
}

/** Native Notflix composition; official data/actions and optional scoped official card preview. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotflixHomeContent(
    state: HomeUiState,
    memory: NotflixHomeMemory,
    enriched: Map<String, MetaPreview> = emptyMap(),
    navigation: NotflixNavigationState = requireNotNull(LocalNotflixPresentation.current.navigation),
    onDetail: (String, String, String) -> Unit,
    onPlay: ((String, String, String) -> Unit)? = null,
    onContinue: (ContinueWatchingItem) -> Unit,
    onContinueFromBeginning: (ContinueWatchingItem) -> Unit = onContinue,
    onContinueManually: (ContinueWatchingItem) -> Unit = onContinue,
    showContinueManually: Boolean = false,
    onRemoveContinue: (ContinueWatchingItem) -> Unit = {},
    onFolder: (String, String) -> Unit,
    onSeeAllCatalog: (String, String, String) -> Unit = { _, _, _ -> },
    onVisibleRow: (String) -> Unit,
    onHiddenRow: (String) -> Unit = {},
    onLoadMore: (String, String, String) -> Unit = { _, _, _ -> },
    onFocusedMeta: (MetaPreview) -> Unit = {},
    onLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    previewSession: NotflixPreviewSession? = null,
    cardActions: NotflixCardActionsHost? = null,
    myListRow: NotflixCatalogRow? = null,
    becauseRow: NotflixCatalogRow? = null,
    /** M66: settings shortcut from the KIDS notice; the host decides the destination. */
    onOpenIntegrations: () -> Unit = {},
    onRetry: () -> Unit
) {
    val presentation = retainNotflixPresentation(state, memory)
    SideEffect {
        memory.retainedCatalog = presentation.notflixCatalog
        memory.retainedCw = presentation.continueWatchingItems
    }
    val cwTitle = stringResource(R.string.notflix_continue_watching)
    val episodePattern = stringResource(R.string.notflix_episode)
    val seeAll = stringResource(R.string.notflix_collection_see_all)
    val airingTitle = stringResource(R.string.notflix_airing_this_week)
    val todayLabel = stringResource(R.string.cw_airs_today_short)
    val tomorrowLabel = stringResource(R.string.cw_airs_tomorrow_short)
    val locale = LocalConfiguration.current.locales[0]
    val airingCaption = remember(todayLabel, tomorrowLabel, episodePattern, locale) {
        { entry: AiringEntry ->
            val day = when (entry.dayLabel) {
                AiringDayLabel.TODAY -> todayLabel
                AiringDayLabel.TOMORROW -> tomorrowLabel
                AiringDayLabel.WEEKDAY -> entry.localDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            }
            "$day · ${episodePattern.format(entry.info.season, entry.info.episode)}"
        }
    }
    var focused by remember { mutableStateOf(memory.focus) }
    // M32: rows only care whether focus sits on Continue watching. Keying the whole list by the
    // focused row rebuilt every tile on each Up/Down; this derived flag changes only at that row.
    val keepPendingCw by remember { derivedStateOf { focused?.rowKey == "cw" } }
    val rows = remember(presentation.notflixCatalog, presentation.continueWatchingItems, state.continueWatchingEnabled,
        state.notflixCwPhase, keepPendingCw, cwTitle, episodePattern, seeAll, myListRow, becauseRow,
        state.ageLimit, state.ageTmdbKeyAvailable, state.ageRatings, state.watchedMovieIds, state.fullyWatchedSeriesIds,
        state.hiddenTitleKeys, state.disabledHomeCatalogKeys, airingTitle, airingCaption) {
        notflixRows(presentation, keepPendingCw, cwTitle, episodePattern, seeAll, myListRow, becauseRow,
            airingTitle, airingCaption)
    }
    val heroes = if (state.heroSectionEnabled && !notflixKidsNeedsTmdbKey(state.ageLimit, state.ageTmdbKeyAvailable)) {
        val source = presentation.notflixCatalog.heroItems
        val ageAllowed = if (state.ageLimit == null) source else source.filter { state.allowsForAge(it) }
        val visible = if (state.hiddenTitleKeys.isEmpty()) ageAllowed else ageAllowed.filterNot { it.isHiddenTitle(state.hiddenTitleKeys) }
        notflixBillboardItems(visible)
    } else emptyList()
    val reducedHome = LocalNotflixPresentation.current.reduceHomeMotion
    val cardOpening = LocalNotflixPresentation.current.cardOpening
    val cardOpenDelayMs by rememberUpdatedState(Spec.cardOpenDelayMs(cardOpening))
    val introPhase by com.nuvio.tv.ui.appearance.NotflixIntroProcess.coordinator.state.collectAsState()
    val idleHeaderReady = previewSession?.billboardReady == true &&
        (introPhase as? com.nuvio.tv.ui.appearance.IntroPhase.Finished)?.reason != com.nuvio.tv.ui.appearance.IntroFinishReason.SKIPPED
    var manualEditorialEpoch by remember { mutableLongStateOf(0L) }
    var billboardLaunched by remember { mutableStateOf(false) }
    val freshBillboardEntry = remember(memory, previewSession?.binding) { memory.focus == null }
    var editorial by remember { mutableIntStateOf(memory.editorialIndex.coerceIn(0, (heroes.size - 1).coerceAtLeast(0))) }
    var editorialKey by remember { mutableStateOf(memory.editorialKey) }
    val selectedEditorial = notflixBillboardIndex(heroes, editorialKey, editorial)
    val hero = heroes.getOrNull(selectedEditorial)?.let { original ->
        enriched[original.id]?.takeIf { notflixMetaKey(it) == notflixMetaKey(original) } ?: original
    }
    SideEffect {
        memory.editorialIndex = selectedEditorial
        memory.editorialKey = hero?.let(::notflixMetaKey)
    }
    val column = rememberLazyListState(memory.columnIndex, memory.columnOffset)
    val visibleLoadKeys = remember { mutableSetOf<String>() }
    LaunchedEffect(column, rows, presentation.notflixCatalog, state.hideUnreleasedContent) {
        snapshotFlow { column.layoutInfo.visibleItemsInfo.map { it.key }.toSet() }.distinctUntilChanged().collect { visible ->
            val keys = rows.filter { it.key in visible }.map { it.loadKey }.toSet()
            (visibleLoadKeys - keys).forEach(onHiddenRow)
            visibleLoadKeys.clear(); visibleLoadKeys.addAll(keys)
            keys.forEach(onVisibleRow)
        }
    }
    DisposableEffect(Unit) { onDispose { visibleLoadKeys.toList().forEach(onHiddenRow); visibleLoadKeys.clear() } }
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val heroRequester = remember { FocusRequester() }
    val heroPlayRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    // M66: the KIDS notice without the TMDB key leads with «Integraciones»; retrying cannot fix it.
    val kidsIntegrationsRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val moves = remember { NotflixFocusMoves() }
    val latestRows by rememberUpdatedState(rows)
    val latestEnriched by rememberUpdatedState(enriched)
    val latestOnFocusedMeta by rememberUpdatedState(onFocusedMeta)
    val latestConfigured by rememberUpdatedState(state.notflixCatalog.configured)
    var expanded by remember { mutableStateOf(memory.focus?.takeIf { it.expanded }?.let { "${it.rowKey}|${it.itemKey}" }) }
    var initialPlacement by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var cwOptions by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    var actualPreviewFocus by remember { mutableStateOf<String?>(null) }
    var actualPreviewSession by remember { mutableStateOf<NotflixPreviewSession?>(null) }
    var previewFocusEpoch by remember { mutableLongStateOf(0L) }
    var cardCursor by remember { mutableStateOf<NotflixCardCursor?>(null) }
    var fullscreenTrailer by remember { mutableStateOf<NotflixFullscreenTrailer?>(null) }
    var pendingFullscreenReturn by remember { mutableStateOf<NotflixFullscreenReturn?>(null) }
    LaunchedEffect(state.notflixCatalog.context, previewSession?.binding) {
        if (fullscreenTrailer != null) {
            fullscreenTrailer = null
            pendingFullscreenReturn = null
        }
    }
    LaunchedEffect(fullscreenTrailer) {
        if (fullscreenTrailer == null) {
            val exit = pendingFullscreenReturn ?: return@LaunchedEffect
            pendingFullscreenReturn = null
            runCatching { requesters[exit.identity]?.requestFocus() }
            val resume = exit.resumePositionMs
            if (resume != null) {
                cardCursor = NotflixCardCursor(exit.identity, exit.actionIndex, artwork = true)
                previewSession?.restartWithPosition(exit.identity, resume)
            } else {
                cardCursor = null
                previewSession?.suppressNextVisit(exit.identity)
                previewSession?.cancel()
            }
        }
    }
    // La capa grande oculta la barra superior; al salir de Inicio también se restaura.
    LaunchedEffect(fullscreenTrailer != null) { navigation.immersive = fullscreenTrailer != null }
    DisposableEffect(navigation) { onDispose { navigation.immersive = false } }
    fun recordActualFocus(identity: String, hasFocus: Boolean) {
        val recorded = focused?.let { "${it.rowKey}|${it.itemKey}" }
        if (hasFocus && recorded == identity) {
            if (actualPreviewFocus != identity || actualPreviewSession !== previewSession) {
                previewSession?.cancel()
                actualPreviewFocus = identity; actualPreviewSession = previewSession; previewFocusEpoch++
            }
        } else if (!hasFocus && actualPreviewFocus == identity) {
            previewSession?.cancel(); actualPreviewFocus = null; actualPreviewSession = null
        }
    }
    val contentRequester = LocalContentFocusRequester.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun acceptFocus(identity: String): Boolean {
        val expected = moves.expected
        val saved = memory.focus?.let { "${it.rowKey}|${it.itemKey}" }
        // Compose can pick a neighbour when a keyed node disappears. That is not
        // a user decision and must not overwrite the identity awaiting resolution.
        if (expected != null && expected != identity && moves.acquiring != identity) return false
        if (expected == null && saved != null && saved != identity && moves.acquiring != identity) return false
        if (expected == identity) moves.expected = null
        return true
    }

    fun move(requestLoop: Boolean = false, block: suspend () -> Unit) {
        previewSession?.cancel()
        val serial = ++moves.serial
        moves.job?.cancel()
        moves.requestLoop = requestLoop
        moves.acquiring = null
        if (!requestLoop) moves.pending = null
        moves.job = scope.launch {
            moving = true
            try { block() } finally {
                if (serial == moves.serial) {
                    moving = false; moves.requestLoop = false; moves.acquiring = null
                }
            }
        }
    }
    fun cancelMoves() {
        moves.serial++; moves.job?.cancel()
        moves.expected = null; moves.acquiring = null; moves.pending = null
        moves.requestLoop = false; moving = false
    }

    fun recordTile(rowIndex: Int, row: NotflixDisplayRow, index: Int, tile: NotflixTile) {
        val identity = "${row.key}|${tile.key}"
        if (!acceptFocus(identity)) return
        navigation.topFocused = false
        navigation.homeAtTop = false
        navigation.homeBrowsing = rowIndex > 0
        val changed = focused?.let { "${it.rowKey}|${it.itemKey}" } != identity
        if (changed) expanded = null
        val record = NotflixFocusRecord(row.key, tile.key, tile.canonical, index, rowIndex, expanded == identity)
        focused = record; memory.focus = record
        if (changed) presentation.notflixCatalog.rows.firstOrNull { it.key == row.key }?.catalog?.let { catalog ->
            if (row.folderTarget == null && row.phase == NotflixRowPhase.READY && catalog.hasMore && index >= row.tiles.size - 4) onLoadMore(catalog.catalogId, catalog.addonId, catalog.apiType)
        }
    }
    fun recordStatus(rowIndex: Int, row: NotflixDisplayRow) {
        if (!acceptFocus("${row.key}|status")) return
        navigation.topFocused = false; navigation.homeAtTop = false; navigation.homeBrowsing = rowIndex > 0
        val record = NotflixFocusRecord(row.key, "status", "", 0, rowIndex)
        focused = record; memory.focus = record
    }

    fun request(rowIndex: Int, itemIndex: Int) {
        val row = latestRows.getOrNull(rowIndex) ?: return
        val targetIndex = itemIndex.coerceIn(0, row.tiles.lastIndex.coerceAtLeast(0))
        val tile = row.tiles.getOrNull(targetIndex)
        moves.pending = NotflixFocusRecord(row.key, tile?.key ?: "status", tile?.canonical.orEmpty(), targetIndex, rowIndex)
        moves.expected = "${row.key}|${tile?.key ?: "status"}"
        // Conflate incoming intent, but finish each acquisition. Cancelling on every
        // repeat starves focus when events arrive faster than layout's six frames.
        if (moves.requestLoop && moves.job?.isActive == true) return
        move(requestLoop = true) {
            while (moves.pending != null) {
                val intent = requireNotNull(moves.pending)
                moves.pending = null
                val current = latestRows
                val sameRow = current.indexOfFirst { it.key == intent.rowKey }
                val exact = current.getOrNull(sameRow)?.tiles?.indexOfFirst { it.key == intent.itemKey } ?: -1
                val result = when {
                    exact >= 0 -> NotflixFocusResolution.Item(sameRow, exact)
                    intent.itemKey == "status" && sameRow >= 0 && current[sameRow].tiles.isEmpty() &&
                        current[sameRow].phase != NotflixRowPhase.READY -> NotflixFocusResolution.Item(sameRow, 0)
                    else -> resolveNotflixFocus(intent, current.map { candidate -> NotflixFocusRow(candidate.key,
                        candidate.phase, candidate.tiles.map { NotflixFocusItem(it.key, it.canonical) }) }, latestConfigured)
                }
                when (result) {
                    NotflixFocusResolution.Wait -> {
                        if (moves.pending == null) moves.pending = intent
                        withFrameNanos { }
                    }
                    NotflixFocusResolution.Top -> { navigation.focusTop(); break }
                    is NotflixFocusResolution.Item -> {
                        val destination = current[result.row]
                        val index = result.index
                        navigation.homeBrowsing = result.row > 0
                        navigation.homeAtTop = false
                        // Presentation alignment only: keep the editorial tail for the first
                        // carousel, and previous-row context below the persistent TV header.
                        if (result.row == 0) column.scrollToItem(0, if (hero == null) 0 else
                            with(density) { (Spec.HERO_HEIGHT - 164).dp.roundToPx() })
                        else column.scrollToItem(result.row + 1, -with(density) { (Spec.NAV_HEIGHT + 80).dp.roundToPx() })
                        repeat(3) { withFrameNanos { } }
                        val rowState = rowStates[destination.key]
                        if (destination.tiles.isNotEmpty() && rowState?.layoutInfo?.visibleItemsInfo?.none { it.index == index } != false) {
                            rowState?.scrollToItem(index)
                        }
                        rowState?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.index == index }?.let { info ->
                            val margin = with(density) { 8.dp.toPx() }
                            val start = rowState.layoutInfo.viewportStartOffset + margin
                            val end = rowState.layoutInfo.viewportEndOffset - margin
                            if (info.offset < start) rowState.scrollBy(info.offset - start)
                            else if (info.offset + info.size > end) rowState.scrollBy(info.offset + info.size - end)
                        }
                        repeat(3) { withFrameNanos { } }
                        val identity = "${destination.key}|${destination.tiles.getOrNull(index)?.key ?: "status"}"
                        if (moves.pending == null) moves.expected = identity
                        moves.acquiring = identity
                        val acquired = requesters[identity]?.let { runCatching { it.requestFocus() }.getOrDefault(false) } == true
                        if (acquired) destination.tiles.getOrNull(index)?.let { recordTile(result.row, destination, index, it) }
                            ?: recordStatus(result.row, destination)
                        else if (moves.pending == null) moves.pending = intent
                        moves.acquiring = null
                    }
                }
            }
        }
    }
    fun focusHeroOrFirst() {
        navigation.topFocused = false
        moves.expected = hero?.let { "hero|${notflixMetaKey(it)}" }
        move {
            column.scrollToItem(0)
            navigation.homeAtTop = true
            navigation.homeBrowsing = false
            repeat(2) { withFrameNanos { } }
            if (hero != null) runCatching {
                (if (onPlay != null && memory.editorialAction == "play") heroPlayRequester else heroRequester).requestFocus()
            }
            else {
                val first = rows.indexOfFirst { it.tiles.isNotEmpty() || it.phase != NotflixRowPhase.READY }
                if (first >= 0) request(first, 0)
                else if (notflixKidsNeedsTmdbKey(state.ageLimit, state.ageTmdbKeyAvailable)) {
                    moves.expected = "kids-integrations|kids-integrations"
                    runCatching { kidsIntegrationsRequester.requestFocus() }
                }
                else if (state.notflixCatalog.configured) {
                    moves.expected = "retry|retry"
                    runCatching { retryRequester.requestFocus() }
                } else navigation.focusTop()
            }
        }
    }
    fun restore(fromTop: Boolean = false): Boolean {
        val original = memory.focus
        val replacement = original?.rowKey?.let { presentation.notflixCatalog.focusAliases[it] }
        val saved = if (original != null && rows.none { it.key == original.rowKey } && rows.any { it.key == replacement })
            original.copy(rowKey = requireNotNull(replacement)) else original
        if (saved != original) memory.focus = saved
        if (saved?.rowKey == "hero" && hero != null) { focusHeroOrFirst(); return true }
        else if (saved != null) {
            when (val result = resolveNotflixFocus(saved, rows.map { row ->
                NotflixFocusRow(row.key, row.phase, row.tiles.map { NotflixFocusItem(it.key, it.canonical) })
            }, state.notflixCatalog.configured)) {
                is NotflixFocusResolution.Item -> request(result.row, result.index)
                NotflixFocusResolution.Top -> if (fromTop) focusHeroOrFirst() else navigation.focusTop()
                NotflixFocusResolution.Wait -> return false
            }
        } else focusHeroOrFirst()
        return true
    }

    DisposableEffect(navigation, memory) {
        navigation.homeDown = { restore(true) }
        navigation.homeEntry = {
            cancelMoves()
            scope.launch { column.scrollToItem(0); navigation.homeAtTop = true }
        }
        navigation.homeCancelFocus = { cancelMoves() }
        navigation.homeNeedsFocusRepair = {
            moves.expected != null || (!initialPlacement && memory.focus != null) ||
                notflixFocusNeedsRepair(memory.focus, latestRows.map { row ->
                    NotflixFocusRow(row.key, row.phase, row.tiles.map { NotflixFocusItem(it.key, it.canonical) })
                }, latestConfigured)
        }
        onDispose {
            memory.columnIndex = column.firstVisibleItemIndex
            memory.columnOffset = column.firstVisibleItemScrollOffset
            rowStates.forEach { (key, value) -> memory.rowScroll[key] = value.firstVisibleItemIndex to value.firstVisibleItemScrollOffset }
            navigation.homeDown = null
            navigation.homeEntry = null
            navigation.homeCancelFocus = null
            navigation.homeNeedsFocusRepair = null
            // Keep browsing geometry across child destinations; explicit top focus resets it.
            if (navigation.topFocused) navigation.homeBrowsing = false
            navigation.homeAtTop = true
        }
    }
    // Update callbacks without disposing the session bridge when only data changes.
    SideEffect { navigation.homeDown = { restore(true) } }
    // M32: `moving` only gates the reconciliation below. Reading it as an effect key restarted this
    // body and recomposed Home on every move start/end; observe it inside the coroutine instead.
    LaunchedEffect(rows, hero?.id, state.notflixCatalog.configured) {
        snapshotFlow { moving }.collectLatest { isMoving ->
            if (!initialPlacement && state.notflixCatalog.configured && !navigation.topFocused && !navigation.interacted) {
                if (freshBillboardEntry && hero != null && previewSession?.binding != null && !reducedHome) {
                    // Idle Normal entry uses the Home header. Down enters the primary CTA and pauses rotation.
                    memory.editorialAction = "play"
                    // Keep content focus under the intro; Back-to-skip must not inherit a root exit target.
                    if (idleHeaderReady) {
                        navigation.focusTop()
                        initialPlacement = true
                    } else initialPlacement = restore()
                } else initialPlacement = restore()
            } else if (!initialPlacement && memory.focus != null && !navigation.topFocused) {
                initialPlacement = restore()
            } else if (initialPlacement && !navigation.topFocused && !isMoving) {
                val saved = memory.focus
                // The empty-state actions (Retry, KIDS Integrations) are not rows: never bounce them to the top bar.
                val emptyAction = saved?.rowKey in NOTFLIX_EMPTY_ACTION_KEYS
                if (emptyAction && rows.any { it.tiles.isNotEmpty() }) focusHeroOrFirst()
                else if (saved != null && saved.rowKey != "hero" && !emptyAction && rows.none { row ->
                        row.key == saved.rowKey && row.tiles.any { it.key == saved.itemKey }
                    }) restore()
                else if (saved != null && saved.rowKey != "hero" && !emptyAction) {
                    val rowIndex = rows.indexOfFirst { it.key == saved.rowKey }
                    val index = rows.getOrNull(rowIndex)?.tiles?.indexOfFirst { it.key == saved.itemKey } ?: -1
                    if (index >= 0 && (saved.index != index || saved.rowIndex != rowIndex)) {
                        val updated = saved.copy(index = index, rowIndex = rowIndex)
                        memory.focus = updated; focused = updated
                    }
                }
            }
        }
    }
    LaunchedEffect(memory, previewSession?.binding, freshBillboardEntry, reducedHome, hero != null, idleHeaderReady) {
        if (freshBillboardEntry && hero != null && previewSession?.binding != null && !reducedHome && idleHeaderReady) {
            // Chrome can be subcomposed after Home's first effect. Wait for registration,
            // and never override a real key/action that arrived during initial layout.
            repeat(2) { withFrameNanos { } }
            if (!navigation.interacted && !billboardLaunched) navigation.focusTop()
        }
    }
    LaunchedEffect(navigation.topFocused) {
        if (navigation.topFocused) { expanded = null; memory.focus = memory.focus?.copy(expanded = false) }
    }
    // The action cursor belongs to one expanded visit; collapse, another title or another context drops it.
    LaunchedEffect(expanded, previewSession?.binding) { if (cardCursor?.identity != expanded) cardCursor = null }
    // M32: focus was this effect's key, so every navigation step invalidated Home's composition.
    // Observe the record inside the coroutine; the expand delay and callback order are unchanged.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(focused?.rowKey, focused?.itemKey, navigation.topFocused) }
            .collectLatest { (rowKey, itemKey, topFocused) ->
                if (topFocused) return@collectLatest
                val saved = focused?.takeIf { it.rowKey == rowKey && it.itemKey == itemKey } ?: return@collectLatest
                if (saved.rowKey == "hero") return@collectLatest
                val row = latestRows.find { it.key == saved.rowKey } ?: return@collectLatest
                val tile = row.tiles.find { it.key == saved.itemKey } ?: return@collectLatest
                tile.meta?.let(latestOnFocusedMeta)
                if (!row.wide && tile.meta != null && expanded != "${row.key}|${tile.key}") {
                    delay(cardOpenDelayMs)
                    if (!navigation.topFocused) {
                        expanded = "${row.key}|${tile.key}"
                        memory.focus = saved.copy(expanded = true)
                    }
                }
            }
    }
    LaunchedEffect(hero?.let(::notflixMetaKey), navigation.topFocused) {
        val selected = hero
        if (selected != null && !navigation.topFocused && memory.focus?.rowKey == "hero" &&
            memory.focus?.canonicalKey != notflixMetaKey(selected)) {
            val record = NotflixFocusRecord("hero", notflixMetaKey(selected), notflixMetaKey(selected), selectedEditorial, -1)
            focused = record; memory.focus = record
            onFocusedMeta(selected)
        }
    }
    // M32: candidate and scroll/focus gating were computed in composition; every move recomposed the
    // whole screen. Publish both from one snapshot flow so the session only hears real changes.
    LaunchedEffect(previewSession) {
        val session = previewSession ?: return@LaunchedEffect
        snapshotFlow {
            val saved = focused?.takeIf {
                actualPreviewSession === session && actualPreviewFocus == "${it.rowKey}|${it.itemKey}" && !navigation.topFocused
            }
            val row = saved?.let { s -> latestRows.find { it.key == s.rowKey && !it.wide && it.phase == NotflixRowPhase.READY } }
            val tile = row?.tiles?.find { "${row.key}|${it.key}" == actualPreviewFocus }
            val meta = tile?.meta?.let { latestEnriched[it.id] ?: it }
            val candidate = session.binding?.let { binding ->
                if (row != null && tile != null && meta != null) {
                    val identity = "${row.key}|${tile.key}"
                    PreviewCandidate(PreviewVisit(binding, identity, notflixMetaKey(meta), previewFocusEpoch),
                        meta, expanded == identity)
                } else null
            }
            candidate to (moving || column.isScrollInProgress || rowStates.values.any { it.isScrollInProgress } || cwOptions != null)
        }.collectLatest { (candidate, blocked) -> session.card(candidate, blocked) }
    }
    DisposableEffect(previewSession) { onDispose { previewSession?.card(null, true) } }
    BackHandler(!navigation.topFocused) {
        // M32: one handler reads the action cursor at event time; the second handler's enabled flag
        // was a composition read. Back inside the panel still returns to the card itself.
        if (cardCursor != null) { cardCursor = null; return@BackHandler }
        previewSession?.cancel()
        val saved = memory.focus
        if (saved != null && saved.rowKey != "hero" && saved.index > 0) {
            val index = rows.indexOfFirst { it.key == saved.rowKey }
            if (index >= 0) request(index, 0) else navigation.focusTop()
        } else navigation.focusTop()
    }
    // All scrolling comes from explicit row navigation/one expansion edge adjustment.
    val noAutomaticScroll = remember { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
    } }
    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides noAutomaticScroll) {
        LazyColumn(
            state = column, contentPadding = PaddingValues(bottom = 24.dp),
            // Perf: the Notflix shell root paints the background; a second full-screen fill here was pure overdraw.
            modifier = Modifier.fillMaxSize()
                .focusRequester(contentRequester).focusGroup().testTag("notflix-home")
        ) {
            item("editorial") {
                Column(Modifier.fillMaxWidth()) {
                    if (hero != null) {
                        fun selectEditorial(position: Int, manual: Boolean) {
                            val target = position.coerceIn(heroes.indices)
                            editorial = target; editorialKey = notflixMetaKey(heroes[target])
                            memory.editorialIndex = target; memory.editorialKey = editorialKey
                            if (manual) manualEditorialEpoch++
                        }
                        fun recordHero(action: BillboardAction) {
                            if (!acceptFocus("hero|${notflixMetaKey(hero)}")) return
                            navigation.topFocused = false
                            navigation.homeAtTop = true
                            memory.editorialAction = if (action == BillboardAction.PLAY) "play" else "info"
                            editorialKey = notflixMetaKey(hero); memory.editorialKey = editorialKey
                            onFocusedMeta(hero)
                            val record = NotflixFocusRecord("hero", notflixMetaKey(hero), notflixMetaKey(hero), selectedEditorial, -1)
                            focused = record; memory.focus = record
                        }
                        fun activateHero(action: BillboardAction) {
                            if (billboardLaunched || (previewSession != null && !previewSession.billboardReady)) return
                            billboardLaunched = true
                            previewSession?.cancel(); cancelMoves()
                            moves.expected = "hero|${notflixMetaKey(hero)}"
                            recordHero(action)
                            if (action == BillboardAction.PLAY) onPlay?.invoke(hero.id, hero.apiType, hero.sourceAddonBaseUrl.orEmpty())
                            else onDetail(hero.id, hero.apiType, hero.sourceAddonBaseUrl.orEmpty())
                        }
                        NotflixBillboard(hero, selectedEditorial, heroes.size, previewSession?.binding, reducedHome,
                            eligible = previewSession?.billboardReady == true && !billboardLaunched && cwOptions == null && !moving &&
                                navigation.homeAtTop && column.firstVisibleItemIndex == 0 && column.firstVisibleItemScrollOffset == 0,
                            manualEpoch = manualEditorialEpoch, infoRequester = heroRequester, playRequester = heroPlayRequester,
                            liveAllowed = { previewSession?.billboardReady == true },
                            onAdvance = { selectEditorial((selectedEditorial + 1) % heroes.size, false) },
                            onSelect = { selectEditorial(it, true) }, onInteraction = {}, onFocus = ::recordHero,
                            onKey = { action, key ->
                                when (key) {
                                    Key.DirectionUp -> navigation.focusTop()
                                    Key.DirectionDown -> rows.indexOfFirst { it.tiles.isNotEmpty() || it.phase != NotflixRowPhase.READY }
                                        .takeIf { it >= 0 }?.let { request(it, 0) }
                                    Key.DirectionLeft, Key.DirectionRight -> {
                                        val step = if ((key == Key.DirectionRight) != rtl) 1 else -1
                                        when {
                                            onPlay != null && action == BillboardAction.PLAY && step > 0 -> heroRequester.requestFocus()
                                            onPlay != null && action == BillboardAction.INFO && step < 0 -> heroPlayRequester.requestFocus()
                                            else -> selectEditorial((selectedEditorial + step).coerceIn(heroes.indices), true)
                                        }
                                    }
                                    else -> Unit
                                }
                            }, onInfo = { activateHero(BillboardAction.INFO) },
                            onPlay = onPlay?.let { { activateHero(BillboardAction.PLAY) } })
                    } else Spacer(Modifier.height((Spec.NAV_HEIGHT + 12).dp))
                }
            }
            itemsIndexed(rows, key = { _, row -> row.key }) { rowIndex, row ->
                if (row.tiles.isNotEmpty() || row.phase != NotflixRowPhase.READY) {
                    val savedScroll = memory.rowScroll[row.key]
                    val horizontal = rememberLazyListState(savedScroll?.first ?: 0, savedScroll?.second ?: 0)
                    DisposableEffect(row.key, horizontal) {
                        rowStates[row.key] = horizontal
                        onDispose {
                            memory.rowScroll[row.key] = horizontal.firstVisibleItemIndex to horizontal.firstVisibleItemScrollOffset
                            rowStates.remove(row.key)
                        }
                    }
                    NotflixRow(row, horizontal, enriched, expanded, requesters,
                        preview = previewSession, onActualFocus = ::recordActualFocus,
                        cardActions = cardActions, canPlay = onPlay != null, forward = !rtl, cursor = { cardCursor },
                        onCursor = { navigation.interacted = true; cardCursor = it },
                        onOpenFullscreen = { tile, identity ->
                            val meta = tile.meta
                            val source = previewSession?.playingSource(identity)
                            if (meta != null && source != null) {
                                val position = previewSession.currentPositionMs()
                                previewSession.cancel()
                                fullscreenTrailer = NotflixFullscreenTrailer(
                                    identity = identity,
                                    title = meta.name,
                                    logo = meta.logo,
                                    source = source,
                                    startPositionMs = position,
                                    meta = meta,
                                    base = rowCatalogBase(state, row.key)
                                        .ifBlank { meta.sourceAddonBaseUrl.orEmpty() },
                                    actionIndex = cardCursor?.takeIf { it.identity == identity }?.index ?: 0
                                )
                            }
                        },
                        onAction = { tile, action ->
                            val meta = tile.meta
                            val identity = "${row.key}|${tile.key}"
                            val base = rowCatalogBase(state, row.key).ifBlank { meta?.sourceAddonBaseUrl.orEmpty() }
                            if (meta != null) when (action) {
                                NotflixCardAction.PLAY, NotflixCardAction.INFO -> {
                                    previewSession?.cancel()
                                    cancelMoves()
                                    moves.expected = identity
                                    recordTile(rowIndex, row, row.tiles.indexOfFirst { it.key == tile.key }, tile)
                                    if (action == NotflixCardAction.PLAY) onPlay?.invoke(meta.id, meta.apiType, base)
                                    else onDetail(meta.id, meta.apiType, base)
                                }
                                NotflixCardAction.LIBRARY -> cardActions?.onLibrary?.invoke(meta, base)
                                NotflixCardAction.WATCHED -> cardActions?.onWatched?.invoke(meta)
                                NotflixCardAction.SOUND -> previewSession?.toggleSound(identity)
                                NotflixCardAction.HIDE -> cardActions?.onHide?.invoke(meta)
                            }
                        },
                        onStatusFocused = statusFocus@ {
                            recordStatus(rowIndex, row)
                        },
                        onFocused = tileFocus@ { index, tile ->
                            recordTile(rowIndex, row, index, tile)
                        },
                        onKey = { index, key ->
                            navigation.interacted = true
                            previewSession?.cancel()
                            when (key) {
                                Key.DirectionRight, Key.DirectionLeft -> {
                                    val step = if ((key == Key.DirectionRight) != rtl) 1 else -1
                                    // Several repeat/down events can arrive before the focus job
                                    // completes. Advance from its pending target, not the old node.
                                    val pending = moves.expected?.let { expected -> row.tiles.indexOfFirst { "${row.key}|${it.key}" == expected } }
                                        ?.takeIf { it >= 0 } ?: index
                                    request(rowIndex, (pending + step).coerceIn(0, row.tiles.lastIndex.coerceAtLeast(0)))
                                }
                                Key.DirectionUp -> {
                                    val previous = (rowIndex - 1 downTo 0).firstOrNull { rows[it].tiles.isNotEmpty() || rows[it].phase != NotflixRowPhase.READY }
                                    if (previous == null) { if (hero != null) focusHeroOrFirst() else navigation.focusTop() }
                                    else request(previous, index)
                                }
                                Key.DirectionDown -> (rowIndex + 1 until rows.size)
                                    .firstOrNull { rows[it].tiles.isNotEmpty() || rows[it].phase != NotflixRowPhase.READY }
                                    ?.let { request(it, index) }
                                else -> Unit
                            }
                        }, onClick = { tile ->
                            previewSession?.cancel()
                            cancelMoves()
                            moves.expected = "${row.key}|${tile.key}"
                            recordTile(rowIndex, row, row.tiles.indexOfFirst { it.key == tile.key }, tile)
                            when {
                                tile.meta != null -> onDetail(tile.meta.id, tile.meta.apiType,
                                    rowCatalogBase(state, row.key).ifBlank { tile.meta.sourceAddonBaseUrl.orEmpty() })
                                tile.cw != null -> onContinue(tile.cw)
                                tile.folder != null -> onFolder(tile.folder.first, tile.folder.second)
                                tile.seeAllCatalog != null -> onSeeAllCatalog(
                                    tile.seeAllCatalog.first,
                                    tile.seeAllCatalog.second,
                                    tile.seeAllCatalog.third
                                )
                            }
                        }, onLongPress = { tile ->
                            previewSession?.cancel()
                            cancelMoves()
                            moves.expected = "${row.key}|${tile.key}"
                            recordTile(rowIndex, row, row.tiles.indexOfFirst { it.key == tile.key }, tile)
                            if (tile.cw != null) cwOptions = tile.cw
                            else tile.meta?.let { onLongPress(it, rowCatalogBase(state, row.key).ifBlank { it.sourceAddonBaseUrl.orEmpty() }) }
                        }, onRetry = { row.folderTarget?.let { onFolder(it.first, it.second) } ?: onRetry() })
                }
            }
            if (rows.none { it.tiles.isNotEmpty() } && rows.none { it.phase == NotflixRowPhase.PENDING }) {
                item("empty") {
                    Column(Modifier.padding(horizontal = Spec.GUTTER.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val kidsNeedsKey = notflixKidsNeedsTmdbKey(state.ageLimit, state.ageTmdbKeyAvailable)
                        if (state.ageLimit != null) {
                            if (kidsNeedsKey) {
                                Text(stringResource(R.string.notflix_kids_tmdb_required), color = Spec.Secondary, fontSize = 18.sp)
                            } else {
                                Text(stringResource(R.string.notflix_age_empty), color = Spec.Secondary, fontSize = 18.sp)
                                if (!state.ageTmdbKeyAvailable) {
                                    Text(stringResource(R.string.notflix_age_empty_no_tmdb), color = Spec.Secondary, fontSize = 16.sp)
                                }
                            }
                        } else {
                            Text(if (!state.notflixCatalog.configured) stringResource(R.string.notflix_loading_catalogs)
                                else state.error ?: stringResource(R.string.notflix_no_content),
                                color = Spec.Secondary, fontSize = 18.sp)
                        }
                        if (kidsNeedsKey) {
                            NotflixAction(stringResource(R.string.settings_integration),
                                Modifier.focusRequester(kidsIntegrationsRequester).onFocusChanged {
                                    if (it.isFocused && acceptFocus("kids-integrations|kids-integrations")) {
                                        navigation.topFocused = false
                                        focused = NotflixFocusRecord("kids-integrations", "kids-integrations", "", 0, -1)
                                        memory.focus = focused
                                    }
                                }.testTag("notflix-kids-integrations"),
                                onClick = onOpenIntegrations)
                        }
                        if (state.notflixCatalog.configured) NotflixAction(stringResource(R.string.notflix_retry), Modifier.focusRequester(retryRequester)
                            .onFocusChanged {
                                if (it.isFocused && acceptFocus("retry|retry")) {
                                    navigation.topFocused = false
                                    focused = NotflixFocusRecord("retry", "retry", "", 0, -1)
                                    memory.focus = focused
                                }
                            }.testTag("notflix-retry"), onClick = onRetry)
                    }
                }
            }
        }
    }
    fullscreenTrailer?.let { trailer ->
        NotflixFullscreenTrailerOverlay(
            trailer = trailer,
            onPlay = {
                fullscreenTrailer = null
                pendingFullscreenReturn = null
                cardCursor = null
                onPlay?.invoke(trailer.meta.id, trailer.meta.apiType, trailer.base)
            },
            onClose = { restore, position ->
                fullscreenTrailer = null
                pendingFullscreenReturn = NotflixFullscreenReturn(
                    trailer.identity,
                    position.takeIf { restore },
                    trailer.actionIndex
                )
            }
        )
    }
    }
    cwOptions?.let { selected ->
        ContinueWatchingOptionsDialog(
            item = selected,
            onDismiss = { cwOptions = null; restore() },
            onRemove = { onRemoveContinue(selected); cwOptions = null },
            onDetails = { cwOptions = null; onDetail(selected.contentId(), selected.contentType(), "") },
            onStartFromBeginning = { cwOptions = null; onContinueFromBeginning(selected) },
            showPlayManually = showContinueManually,
            onPlayManually = { cwOptions = null; onContinueManually(selected) }
        )
    }
}

private fun rowCatalogBase(state: HomeUiState, key: String) = state.notflixCatalog.rows.firstOrNull { it.key == key }?.catalog?.addonBaseUrl.orEmpty()

private val directions = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

internal fun KeyEvent.toCardKey(): NotflixCardKey = when (nativeKeyEvent.keyCode) {
    android.view.KeyEvent.KEYCODE_DPAD_UP -> NotflixCardKey.UP
    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> NotflixCardKey.DOWN
    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> NotflixCardKey.LEFT
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> NotflixCardKey.RIGHT
    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> NotflixCardKey.CENTER
    else -> NotflixCardKey.OTHER
}

@Composable
private fun NotflixRow(
    row: NotflixDisplayRow, horizontal: LazyListState, enriched: Map<String, MetaPreview>, expanded: String?,
    requesters: MutableMap<String, FocusRequester>, preview: NotflixPreviewSession?, onActualFocus: (String, Boolean) -> Unit,
    cardActions: NotflixCardActionsHost?, canPlay: Boolean, forward: Boolean, cursor: () -> NotflixCardCursor?,
    onCursor: (NotflixCardCursor?) -> Unit, onAction: (NotflixTile, NotflixCardAction) -> Unit,
    onFocused: (Int, NotflixTile) -> Unit,
    onStatusFocused: () -> Unit,
    onKey: (Int, Key) -> Unit, onClick: (NotflixTile) -> Unit, onLongPress: (NotflixTile) -> Unit, onRetry: () -> Unit,
    onOpenFullscreen: (NotflixTile, String) -> Unit
) {
    val density = LocalDensity.current
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val cardSize = LocalNotflixPresentation.current.cardSize
    LaunchedEffect(expanded, reducedMotion, cardSize) {
        val target = row.tiles.indexOfFirst { "${row.key}|${it.key}" == expanded }
        if (target >= 0) {
            withFrameNanos { }
            val info = horizontal.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (info != null) {
                val overflow = info.offset + with(density) { (Spec.expandedWidth(cardSize) + 8).dp.toPx() } - horizontal.layoutInfo.viewportEndOffset
                if (overflow > 0) {
                    if (reducedMotion) horizontal.scrollBy(overflow) else horizontal.animateScrollBy(overflow)
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("notflix-row-${row.key}")) {
        val title = if (row.folderTarget != null && row.phase == NotflixRowPhase.ERROR && row.tiles.none { it.meta != null })
            stringResource(R.string.notflix_collection_unavailable, row.title)
            else if (row.empty) stringResource(R.string.notflix_collection_empty, row.title) else if (row.tiles.isEmpty() && row.folderTarget == null) row.title else when (row.phase) {
            NotflixRowPhase.PENDING -> stringResource(R.string.notflix_row_updating, row.title)
            NotflixRowPhase.ERROR -> stringResource(R.string.notflix_row_retained, row.title)
            NotflixRowPhase.READY -> row.title
        }
        Text(title, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 2.dp))
        if (row.tiles.isEmpty()) {
            val requester = remember { FocusRequester() }
            DisposableEffect(row.key) {
                requesters["${row.key}|status"] = requester
                onDispose { requesters.remove("${row.key}|status") }
            }
            NotflixAction(stringResource(if (row.folderTarget != null) R.string.notflix_collection_see_all else if (row.phase == NotflixRowPhase.ERROR) R.string.notflix_retry_load else R.string.notflix_loading),
                Modifier.focusRequester(requester).padding(horizontal = Spec.GUTTER.dp, vertical = 24.dp)
                    .onFocusChanged { if (it.isFocused) onStatusFocused() }
                    .testTag("notflix-status-${row.key}")
                    .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key in directions) { onKey(0, it.key); true } else false }, onRetry)
        } else LazyRow(state = horizontal, contentPadding = PaddingValues(horizontal = Spec.GUTTER.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            itemsIndexed(row.tiles, key = { _, tile -> tile.key }) { index, original ->
                val tile = original.meta?.let { enriched[it.id] }?.let { meta ->
                    // Promoter (08-11): focus enrichment must not swap the catalog art for another TMDB version.
                    original.copy(meta = meta, title = meta.name, poster = original.poster ?: meta.poster,
                        backdrop = original.backdrop ?: meta.background ?: meta.landscapePoster)
                } ?: original
                val identity = "${row.key}|${tile.key}"
                val requester = remember { FocusRequester() }
                DisposableEffect(identity) {
                    requesters[identity] = requester
                    onDispose { requesters.remove(identity) }
                }
                val meta = tile.meta
                // Actions exist only when Home supplies its official callbacks/state; without them the card keeps M15 behaviour.
                val withActions = expanded == identity && !row.wide && meta != null && cardActions != null
                val sound = if (withActions) preview?.sound(identity) else null
                val actions = if (withActions && meta != null && cardActions != null) notflixCardActions(meta, cardActions, sound, canPlay) else emptyList()
                // M44: activating an expanded-card action is a confirmation and sounds like one;
                // these buttons never go through the card clickable (the cursor consumes OK).
                val confirmSound = rememberNotflixConfirmSound()
                LaunchedEffect(withActions, meta?.id) { if (withActions && meta != null) cardActions?.onExpanded?.invoke(meta) }
                val cursorIndex = notflixCardCursorIndex(withActions, cursor, identity, actions.size)
                // Only the expanded card reads the cursor; its artwork border exists while the trailer plays.
                val artworkCursor = withActions && cursor()?.let { it.identity == identity && it.artwork } == true
                val trailerFocused = artworkCursor && preview?.artworkPlaying(identity) == true
                var busyPulse by remember(identity) { mutableIntStateOf(0) }
                var busyPulseIndex by remember(identity) { mutableIntStateOf(-1) }
                // M56: a saving control cannot run a second write, but the press must answer.
                fun activateAction(position: Int) {
                    val action = actions.getOrNull(position) ?: return
                    if (action.enabled) {
                        confirmSound()
                        onAction(tile, action.action)
                    } else if (action.busy) {
                        busyPulseIndex = position
                        busyPulse++
                    }
                }
                // M56: pending flips enabled and recomposes; an armed cursor must not survive that change.
                val actionStates = actions.joinToString(",") { if (it.enabled) "1" else "0" }
                LaunchedEffect(withActions, actionStates) {
                    val current = cursor()
                    if (current != null && current.identity == identity && current.armed) {
                        onCursor(current.copy(armed = false))
                    }
                }
                val cardModifier = Modifier.focusRequester(requester).onFocusChanged {
                    if (it.isFocused) onFocused(index, tile)
                    onActualFocus(identity, it.isFocused)
                }
                    .onPreviewKeyEvent { event ->
                        val key = event.toCardKey()
                        if (actions.isNotEmpty() && key != NotflixCardKey.OTHER) {
                            // Read at event time: down and up of one press can arrive before any recomposition.
                            val current = cursor()
                            val (next, result) = notflixCardKey(current, identity, actions.size, key,
                                event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount, forward,
                                trailerPlaying = preview?.isPlaying(identity) == true)
                            // The artwork cursor clears even when the key then falls through to row movement.
                            if (next != current) onCursor(next)
                            if (result != NotflixCardKeyResult.Pass) {
                                when (result) {
                                    is NotflixCardKeyResult.Activate -> activateAction(result.index)
                                    NotflixCardKeyResult.ActivateArtwork -> onOpenFullscreen(tile, identity)
                                    NotflixCardKeyResult.ExitDown -> onKey(index, Key.DirectionDown)
                                    else -> Unit
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (event.type == KeyEventType.KeyDown && event.key in directions) { onKey(index, event.key); true }
                        else false
                    }.testTag("notflix-card-$identity")
                val card: @Composable () -> Unit = {
                    NotflixCard(tile, identity, row.wide, expanded == identity, cardModifier,
                        onLongClick = if (tile.meta != null || tile.cw != null) { { onLongPress(tile) } } else null,
                        // OK on the expanded card with no button selected plays (promoter 08-11); otherwise it opens info.
                        onClick = { actions.firstOrNull { it.action == NotflixCardAction.PLAY && it.enabled }?.let { onAction(tile, it.action) } ?: onClick(tile) },
                        preview = preview, actions = actions, cursorIndex = cursorIndex,
                        trailerFocused = trailerFocused,
                        busyPulse = busyPulse, busyPulseIndex = busyPulseIndex,
                        onActivate = ::activateAction)
                }
                if (tile.caption.isNullOrBlank()) {
                    card()
                } else {
                    Column(horizontalAlignment = Alignment.Start) {
                        card()
                        Text(
                            text = tile.caption,
                            color = Spec.Secondary,
                            fontFamily = Spec.BodyFont,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(Spec.posterWidth(cardSize).dp).padding(top = 4.dp, start = 2.dp, end = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun NotflixCard(tile: NotflixTile, identity: String, wide: Boolean, expanded: Boolean, modifier: Modifier,
    onLongClick: (() -> Unit)?, onClick: () -> Unit, preview: NotflixPreviewSession? = null,
    actions: List<NotflixCardActionUi> = emptyList(), cursorIndex: Int? = null,
    trailerFocused: Boolean = false, busyPulse: Int = 0, busyPulseIndex: Int = -1,
    onActivate: (Int) -> Unit = {},
    /** M63: la cuadrícula de colección abre el panel al lado, con el alto del póster de la rejilla. */
    besidePanelHeight: Dp? = null,
    /** M63: ancho del arte en esa apertura lateral: el del póster de la rejilla, no el de Inicio. */
    besideArtWidth: Dp? = null,
    /** Panel width beside the art; the collection grid passes one wide enough for every action. */
    besidePanelWidth: Dp? = null,
    /** M63: líneas de sinopsis del panel lateral; la rejilla pequeña no tiene alto para tres. */
    besideSynopsisLines: Int = 3,
    besidePanelAtStart: Boolean = false,
    focusScaleOverride: Float? = null,
    /** M63: la tarjeta dibujada encima de la rejilla no recibe foco; el cursor vive en el póster. */
    interactive: Boolean = true, looksFocused: Boolean = false,
    widthOverride: Dp? = null, heightOverride: Dp? = null, panelProgressOverride: Float? = null) {
    var focused by remember { mutableStateOf(false) }
    val longPress = rememberLongPressKeyTracker()
    val confirmSound = rememberNotflixConfirmSound()
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val cardSize = LocalNotflixPresentation.current.cardSize
    // M32: browsing branches already return identical sizes (NotflixVisualSpec), so this card no
    // longer reads homeBrowsing; the texture is the same and row items stop invalidating on it.
    val collapsedWidth = Spec.posterWidth(cardSize)
    val targetWidth = (if (wide) Spec.wideWidth(cardSize) else if (expanded) Spec.expandedWidth(cardSize) else collapsedWidth).dp
    // M64: the expanded card keeps its current height room so the panel never clips at Small.
    val targetHeight = when {
        wide -> Spec.wideHeight(cardSize).dp
        expanded && besidePanelHeight != null -> besidePanelHeight
        expanded -> Spec.expandedHeight(cardSize).dp
        else -> Spec.posterHeight(cardSize).dp
    }
    val animatedWidth by animateDpAsState(targetWidth,
        if (reducedMotion) snap() else tween(Spec.EXPAND_MS), label = "notflix-t1-width")
    val animatedHeight by animateDpAsState(targetHeight,
        if (reducedMotion) snap() else tween(Spec.EXPAND_MS), label = "notflix-t1-height")
    val focusedLook = focused || looksFocused
    val animatedScale by animateFloatAsState(if (focusedLook && !reducedMotion) 1.025f else 1f,
        if (reducedMotion) snap() else tween(Spec.FOCUS_MS), label = "notflix-focus")
    val contentAlpha by animateFloatAsState(
        targetValue = if (tile.watched && !focusedLook) 0.5f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(Spec.FOCUS_MS),
        label = "notflix-watched-alpha"
    )
    val width = widthOverride ?: if (reducedMotion) targetWidth else animatedWidth
    val height = heightOverride ?: if (reducedMotion) targetHeight else animatedHeight
    val scale = focusScaleOverride ?: if (reducedMotion) 1f else animatedScale
    // M56: the panel is laid out at its final width and revealed as the card grows; reduced motion jumps.
    val panelProgress = panelProgressOverride ?: if (reducedMotion) 1f else ((width.value - collapsedWidth) /
        (Spec.expandedWidth(cardSize) - collapsedWidth).toFloat()).coerceIn(0f, 1f)
    val cardBox = modifier.width(width).height(height)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(5.dp))
        .background(Spec.Surface).border(2.dp, if (focusedLook) Spec.Text else Color.Transparent, RoundedCornerShape(5.dp))
    Box(
        (if (interactive) cardBox
            .onPreviewKeyEvent { event ->
                if (onLongClick == null) false else longPress.handle(event.nativeKeyEvent,
                    { it == android.view.KeyEvent.KEYCODE_DPAD_CENTER || it == android.view.KeyEvent.KEYCODE_ENTER || it == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER },
                    onLongClick)
            }.clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
        else cardBox).semantics {
            contentDescription = tile.title
            this[Spec.RenderedCardScale] = scale
            this[Spec.RenderedCardWidthDp] = width.value
        }) {
        if (tile.meta == null && tile.folder != null) {
            NotflixFolderArtwork(tile, focusedLook, Modifier.fillMaxSize(), wide)
        } else if (tile.meta == null && tile.seeAllCatalog != null) {
            NotflixSeeAllArtwork(tile, focusedLook, Modifier.fillMaxSize())
        } else if (expanded && !wide && (besidePanelHeight != null || Spec.CARD_LANDSCAPE)) {
            val artWidth = besideArtWidth ?: Spec.posterWidth(cardSize).dp
            val panelWidth = besidePanelWidth ?: (Spec.expandedWidth(cardSize) - Spec.posterWidth(cardSize)).dp
            val artwork: @Composable () -> Unit = {
                CardArtwork(tile, tile.backdrop ?: tile.poster, true,
                    Modifier.width(artWidth).fillMaxHeight().testTag("notflix-card-art-$identity"),
                    contentAlpha = contentAlpha, trailerFocused = trailerFocused) {
                    preview?.Artwork(identity, Modifier.matchParentSize())
                }
            }
            val panel: @Composable () -> Unit = {
                val panelModifier = Modifier.width(panelWidth).fillMaxHeight()
                    .graphicsLayer { alpha = panelProgress }.padding(Spec.PANEL_PADDING.dp)
                    .testTag("notflix-card-panel-$identity")
                if (tile.meta != null) NotflixCardPanel(tile.meta, identity, actions, cursorIndex, onLongClick != null, false,
                    panelModifier, onActivate, synopsisLines = besideSynopsisLines,
                    busyPulse = busyPulse, busyPulseIndex = busyPulseIndex)
                else Box(panelModifier)
            }
            // Measure once at the final width; the outer card clips the reveal without reflow.
            Row(Modifier.fillMaxHeight()
                .wrapContentWidth(if (besidePanelAtStart) Alignment.End else Alignment.Start, unbounded = true)
                .requiredWidth(artWidth + panelWidth)) {
                if (besidePanelAtStart) { panel(); artwork() } else { artwork(); panel() }
            }
        } else if (expanded && !wide) Column(Modifier.fillMaxSize()) {
            CardArtwork(tile, tile.backdrop ?: tile.poster, true,
                Modifier.fillMaxWidth().weight(1f - Spec.PANEL_WEIGHT).testTag("notflix-card-art-$identity"),
                contentAlpha = contentAlpha, trailerFocused = trailerFocused,
                sharpImage = if (tile.backdrop.isNullOrBlank() && focusedLook && Spec.SHARPER_FOCUSED_POSTER)
                    sharperPosterUrl(tile.poster) else null) {
                preview?.Artwork(identity, Modifier.matchParentSize())
            }
            val panel = Modifier.weight(Spec.PANEL_WEIGHT)
                .requiredWidth(Spec.expandedWidth(cardSize).dp)
                .fillMaxHeight().graphicsLayer { alpha = panelProgress }.padding(Spec.PANEL_PADDING.dp)
                .testTag("notflix-card-panel-$identity")
            // M29: the larger portrait panel had empty space (promoter); two synopsis lines fill it.
            if (tile.meta != null) NotflixCardPanel(tile.meta, identity, actions, cursorIndex, onLongClick != null, tile.backdrop.isNullOrBlank(), panel, onActivate,
                synopsisLines = 2, busyPulse = busyPulse, busyPulseIndex = busyPulseIndex)
            else Box(panel)
        } else {
            // Landscape tiles show backdrop + logo/title like Continue watching; portrait keeps the poster.
            val landscape = wide || Spec.CARD_LANDSCAPE
            CardArtwork(
                tile = tile,
                image = if (landscape) tile.backdrop ?: tile.poster else tile.poster,
                showTitle = landscape,
                modifier = Modifier.fillMaxSize(),
                contentAlpha = contentAlpha,
                trailerFocused = trailerFocused,
                sharpImage = if (!landscape && focusedLook && Spec.SHARPER_FOCUSED_POSTER)
                    sharperPosterUrl(tile.poster) else null
            )
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tile.newEpisodeBadge?.let { NewEpisodeBadge(it) }
                RatingBadge(tile.meta?.imdbRating, source = tile.meta?.ratingSource ?: RatingSource.IMDB)
            }
        }
        tile.progress?.let { progress ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Spec.Secondary)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Spec.Accent))
            }
        }
    }
}

@Composable
private fun CardArtwork(tile: NotflixTile, image: String?, showTitle: Boolean, modifier: Modifier,
    contentAlpha: Float = 1f, sharpImage: String? = null, trailerFocused: Boolean = false,
    preview: @Composable BoxScope.() -> Unit = {}) {
    var failed by remember(image) { mutableStateOf(false) }
    var logoFailed by remember(tile.meta?.logo) { mutableStateOf(false) }
    var sharpFailed by remember(sharpImage) { mutableStateOf(false) }
    var sharpLoaded by remember(sharpImage) { mutableStateOf(false) }
    // Perf: the card already paints Spec.Surface underneath; no second fill below the poster.
    // Watched dimming is a dark veil drawn on top (read in draw only), not layer alpha, which forced an
    // offscreen buffer per watched card on the TV GPU.
    Box(modifier.drawWithContent {
        drawContent()
        val veil = 1f - contentAlpha
        if (veil > 0f) drawRect(Spec.Background.copy(alpha = veil))
    }) {
        if (!image.isNullOrBlank() && !failed) AsyncImage(image, null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop, onError = { failed = true })
        if (!sharpImage.isNullOrBlank() && sharpImage != image && !sharpFailed) {
            // La imagen normal ya está visible; la nítida solo tapa cuando Coil la tiene.
            AsyncImage(
                model = sharpImage,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = if (sharpLoaded) 1f else 0f },
                contentScale = ContentScale.Crop,
                onSuccess = { sharpLoaded = true },
                onError = { sharpFailed = true; sharpLoaded = false }
            )
        }
        preview()
        if (showTitle || image.isNullOrBlank() || failed) {
            // M29: lighter scrim so landscape art keeps its colour; only the lower half darkens behind the title.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, .5f to Color.Transparent, 1f to Spec.Background.copy(.8f))))
            val logo = tile.meta?.logo
            if (showTitle && !logo.isNullOrBlank() && !logoFailed) AsyncImage(logo, tile.title,
                Modifier.align(Alignment.BottomStart).padding(10.dp).widthIn(max = 170.dp).height(40.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, onError = { logoFailed = true })
            else Text(tile.title, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = if (showTitle) 15.sp else 12.sp,
                lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            tile.subtitle?.let { Text(it, color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) }
        }
        if (trailerFocused) {
            Box(Modifier.matchParentSize().border(2.dp, Spec.Text, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
internal fun RatingBadge(value: Float?, modifier: Modifier = Modifier, source: RatingSource = RatingSource.IMDB) {
    val rating = notflixRating(value) ?: return
    Row(modifier.background(Color.Black.copy(.85f), RoundedCornerShape(2.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(source.label, color = if (source == RatingSource.TMDB) Spec.Secondary else Color(0xFFF5C518),
            fontFamily = Spec.BodyFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(rating, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 10.sp)
    }
}

@Composable
private fun NewEpisodeBadge(badge: NotflixNewEpisodeBadge) {
    Text(
        text = stringResource(
            if (badge == NotflixNewEpisodeBadge.NEW_SEASON) R.string.cw_new_season else R.string.cw_new_episode_short
        ),
        color = Color.White,
        fontFamily = Spec.BodyFont,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(Spec.Accent, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .testTag("notflix-new-episode-badge")
    )
}

@Composable
internal fun NotflixFullscreenTrailerOverlay(
    trailer: NotflixFullscreenTrailer,
    onPlay: () -> Unit,
    onClose: (restore: Boolean, positionMs: Long) -> Unit,
    controlsTimeoutMs: Long = Spec.FULLSCREEN_CONTROLS_TIMEOUT_MS,
    video: @Composable (onEnded: () -> Unit, onProgress: (Long) -> Unit) -> Unit = { ended, progress ->
        val pool = LocalTrailerPlayerPool.current
        // Movimiento reducido: la vista aparece directamente, sin animación de apertura.
        val motion = if (LocalNotflixPresentation.current.reduceHomeMotion)
            EnterTransition.None else fadeIn(tween(800))
        TrailerPlayer(
            trailerUrl = trailer.source.video,
            trailerAudioUrl = trailer.source.audio,
            isPlaying = true,
            onEnded = ended,
            onProgressChanged = { position, _ -> progress(position) },
            muted = false,
            cropToFill = true,
            startPositionMs = trailer.startPositionMs,
            modifier = Modifier.fillMaxSize(),
            enter = motion,
            trailerPlayerPool = pool
        )
    },
    onRelease: (TrailerPlayerPool?) -> Unit = { it?.stop() }
) {
    val pool = LocalTrailerPlayerPool.current
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var playFocused by remember { mutableStateOf(true) }
    // Posición real del vídeo: Atrás/Volver reanuda el tráiler de la tarjeta desde donde está.
    var currentPositionMs by remember { mutableLongStateOf(trailer.startPositionMs) }
    val playRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    val rootRequester = remember { FocusRequester() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val description = stringResource(R.string.notflix_trailer_description, trailer.title)
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { playRequester.requestFocus() } else runCatching { rootRequester.requestFocus() }
    }
    // Inactivity timer: any key press restarts it; the controls only hide while they are shown.
    LaunchedEffect(controlsVisible, interactions) {
        if (!controlsVisible) return@LaunchedEffect
        delay(controlsTimeoutMs)
        controlsVisible = false
    }
    DisposableEffect(Unit) { onDispose { onRelease(pool) } }
    BackHandler { onClose(true, currentPositionMs) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) interactions++
                val wasHidden = !controlsVisible
                if (wasHidden && event.key != Key.Back) {
                    controlsVisible = true
                    // The key that wakes the controls only wakes them; the next one navigates.
                    true
                } else if (event.type == KeyEventType.KeyDown && controlsVisible) {
                    // Two focusable controls: Left/Right swap them and Up/Down never escape to Home.
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val toBack = (event.key == Key.DirectionRight) != rtl
                            if (toBack && playFocused) backRequester.requestFocus()
                            else if (!toBack && !playFocused) playRequester.requestFocus()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                } else false
            }
            .semantics { contentDescription = description }
            .testTag("notflix-fullscreen-trailer")
    ) {
        video({ onClose(false, currentPositionMs) }, { currentPositionMs = it })
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spec.GUTTER.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!trailer.logo.isNullOrBlank()) {
                    AsyncImage(
                        trailer.logo,
                        trailer.title,
                        Modifier.widthIn(max = 280.dp).height(64.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart
                    )
                } else {
                    Text(
                        text = trailer.title,
                        color = Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotflixAction(
                        title = stringResource(R.string.notflix_billboard_play),
                        modifier = Modifier.focusRequester(playRequester).testTag("notflix-fullscreen-play")
                            .onFocusChanged { if (it.isFocused) playFocused = true },
                        onClick = onPlay
                    )
                    NotflixAction(
                        title = stringResource(R.string.notflix_fullscreen_back),
                        modifier = Modifier.focusRequester(backRequester).testTag("notflix-fullscreen-back")
                            .onFocusChanged { if (it.isFocused) playFocused = false },
                        onClick = { onClose(true, currentPositionMs) },
                        primary = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun HiddenTitleUndoNotice(undo: HiddenTitleUndo, onUndo: () -> Unit, modifier: Modifier = Modifier, autoFocus: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val navigation = LocalNotflixPresentation.current.navigation
    // Home's D-pad routing never reaches the bottom notice, so the undo button takes focus while it is
    // shown and hands it back to the remembered Home position when it goes away.
    LaunchedEffect(undo.key, autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { requester.requestFocus() }
    }
    DisposableEffect(undo.key, autoFocus) {
        onDispose {
            if (focused) {
                if (autoFocus) navigation?.homeDown?.invoke() else navigation?.focusTop()
            }
        }
    }
    Row(
        modifier = modifier
            .background(Spec.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, Spec.Secondary.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.notflix_title_hidden, undo.title),
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.notflix_undo),
            color = Spec.Background,
            fontFamily = Spec.BodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                // The gap keeps the white focus ring visible around the white button.
                .border(2.dp, if (focused) Spec.Text else Color.Transparent, RoundedCornerShape(4.dp))
                .padding(3.dp)
                .background(Spec.Text, RoundedCornerShape(3.dp))
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("notflix-undo-hide")
        )
    }
}

@Composable
private fun NotflixAction(title: String, modifier: Modifier = Modifier, onClick: () -> Unit, primary: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    Text(title, color = if (primary) Spec.Background else Spec.Text, fontFamily = Spec.BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, modifier = modifier
        .onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) Spec.Text else Color.Transparent, RoundedCornerShape(4.dp)).padding(3.dp)
        .background(if (primary) Spec.Text else Spec.Surface, RoundedCornerShape(3.dp))
        .clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
        .padding(horizontal = 16.dp, vertical = 8.dp))
}

/** Folder card: official cover respecting the folder shape, emoji or the «Ver todo» drawing as fallback. */
@Composable
private fun NotflixFolderArtwork(tile: NotflixTile, focused: Boolean, modifier: Modifier, wide: Boolean = false) {
    var imageFailed by remember(tile.poster) { mutableStateOf(false) }
    val mode = notflixFolderArtworkMode(tile.poster, imageFailed, tile.coverEmoji, tile.tileShape, wide)
    if (mode == NotflixFolderArtworkMode.FALLBACK) {
        NotflixSeeAllArtwork(tile, focused, modifier, showTitle = !tile.hideTitle)
        return
    }
    Box(modifier.background(Spec.Surface), contentAlignment = Alignment.Center) {
        when (mode) {
            NotflixFolderArtworkMode.CROP -> AsyncImage(tile.poster, null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, onError = { imageFailed = true })
            NotflixFolderArtworkMode.FIT -> AsyncImage(tile.poster, null,
                Modifier.fillMaxSize().padding(14.dp), contentScale = ContentScale.Fit,
                onError = { imageFailed = true })
            NotflixFolderArtworkMode.EMOJI -> Text(tile.coverEmoji.orEmpty(), color = Spec.Text,
                fontFamily = Spec.BodyFont, fontSize = 56.sp)
            NotflixFolderArtworkMode.FALLBACK -> Unit
        }
        if (!tile.hideTitle) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                .55f to Color.Transparent, 1f to Spec.Background.copy(.85f))))
            Text(tile.title, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 28.dp))
        }
    }
}

/** «Ver todo»: fanned collage of the row's first posters, an arrow and a clear label. */
@Composable
private fun NotflixSeeAllArtwork(tile: NotflixTile, focused: Boolean, modifier: Modifier, showTitle: Boolean = true) {
    Box(modifier.background(Brush.verticalGradient(listOf(Spec.Surface, Spec.Background)))) {
        val fan = listOf(-1, 1, 0) // side posters first, the centre one on top
        tile.collage.take(3).forEachIndexed { index, url ->
            val slot = if (tile.collage.size == 1) 0 else fan[index]
            AsyncImage(url, null, Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
                .fillMaxWidth(.5f).aspectRatio(2f / 3f)
                .graphicsLayer {
                    translationX = slot * size.width * .42f
                    rotationZ = slot * 7f
                    alpha = if (slot == 0) .85f else .6f
                }.clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .45f to Spec.Background.copy(.35f), .78f to Spec.Background.copy(.95f))))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape)
                .background(if (focused) Spec.Text else Color.Transparent)
                .border(2.dp, Spec.Text, CircleShape), contentAlignment = Alignment.Center) {
                Text("→", color = if (focused) Color.Black else Spec.Text, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = Spec.BodyFont)
            }
            if (showTitle) Text(tile.title, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}
