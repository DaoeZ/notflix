package com.nuvio.tv.ui.screens.tmdb

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.tmdb.TmdbEntityBrowseData
import com.nuvio.tv.core.tmdb.TmdbEntityHeader
import com.nuvio.tv.core.tmdb.TmdbEntityKind
import com.nuvio.tv.core.tmdb.TmdbEntityMediaType
import com.nuvio.tv.core.tmdb.TmdbEntityRail
import com.nuvio.tv.core.tmdb.TmdbEntityRailType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.notflix.NOTFLIX_HEADER_DELAY_MS
import com.nuvio.tv.ui.appearance.notflix.NOTFLIX_HEADER_FADE_MS
import com.nuvio.tv.ui.appearance.notflix.NotflixChip
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterCard
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterMetrics
import com.nuvio.tv.ui.appearance.notflix.NotflixPrimaryButton
import com.nuvio.tv.ui.appearance.notflix.NotflixSkeletonBlock
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

// ─── Reglas puras de la página de estudio/cadena (probadas en JVM) ───

/** Elementos que faltan para el final de una fila cuando se pide la página siguiente. */
internal const val NOTFLIX_ENTITY_LOAD_MORE_THRESHOLD = 4

/**
 * País de origen de TMDB (código ISO de dos letras) como nombre en el idioma del dispositivo. Si
 * no se puede resolver, se deja el código tal cual. La resolución entra inyectada para probarla.
 */
internal fun notflixEntityCountryLabel(
    rawCountry: String?,
    toDisplayName: (String) -> String? = ::notflixEntityLocaleCountryName
): String? {
    val code = rawCountry?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val name = runCatching { toDisplayName(code) }.getOrNull()?.trim()
    return name?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code
}

/** Nombre del país en el idioma del dispositivo a partir de su código ISO. */
internal fun notflixEntityLocaleCountryName(code: String): String? =
    runCatching {
        Locale.Builder()
            .setRegion(code.trim().uppercase(Locale.US))
            .build()
            .displayCountry
    }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * Línea secundaria de la cabecera: tipo de entidad, país y `secondaryLabel` si aporta algo
 * distinto. Cada dato ausente se omite y un texto repetido no se duplica.
 */
internal fun notflixEntitySecondaryLine(
    kind: TmdbEntityKind,
    originCountry: String?,
    secondaryLabel: String?,
    kindLabel: (TmdbEntityKind) -> String,
    countryLabel: (String) -> String? = ::notflixEntityLocaleCountryName
): String = listOfNotNull(
    kindLabel(kind).trim().takeIf { it.isNotBlank() },
    notflixEntityCountryLabel(originCountry, countryLabel),
    secondaryLabel?.trim()?.takeIf { it.isNotBlank() }
)
    .distinctBy { it.lowercase(Locale.US) }
    .joinToString(" · ")

/** Clave estable de una fila: el tipo de medio y el tipo de lista ya la identifican. */
internal fun notflixEntityRailKey(rail: TmdbEntityRail): String =
    "${rail.mediaType.value}_${rail.railType.value}"

/**
 * Tipo por defecto del selector: el que más elementos tiene en POPULAR. En caso de empate manda el
 * orden de las filas (el que ya decidió el servicio) y, si no hay POPULAR, el total de elementos.
 */
internal fun notflixEntityDefaultMediaType(rails: List<TmdbEntityRail>): TmdbEntityMediaType? {
    val types = rails.map { it.mediaType }.distinct()
    if (types.isEmpty()) return null
    if (types.size == 1) return types.first()

    fun popularCount(type: TmdbEntityMediaType): Int =
        rails.firstOrNull {
            it.mediaType == type && it.railType == TmdbEntityRailType.POPULAR
        }?.items?.size ?: 0

    val popularCounts = types.associateWith(::popularCount)
    if ((popularCounts.values.maxOrNull() ?: 0) > 0) {
        return types.maxByOrNull { popularCounts[it] ?: 0 }
    }

    fun totalCount(type: TmdbEntityMediaType): Int =
        rails.filter { it.mediaType == type }.sumOf { it.items.size }

    val totals = types.associateWith(::totalCount)
    return types.maxByOrNull { totals[it] ?: 0 }
}

/** Filas que se pintan para un tipo: sólo las que tienen elementos y en el orden del servicio. */
internal fun notflixEntityRailsOf(
    rails: List<TmdbEntityRail>,
    mediaType: TmdbEntityMediaType?
): List<TmdbEntityRail> = rails.filter { rail ->
    rail.items.isNotEmpty() && (mediaType == null || rail.mediaType == mediaType)
}

/** El selector de tipo sólo aparece si hay filas de los dos tipos. */
internal fun notflixEntityHasBothTypes(rails: List<TmdbEntityRail>): Boolean =
    rails.filter { it.items.isNotEmpty() }
        .map { it.mediaType }
        .distinct()
        .size > 1

/**
 * Título de fila: «Lo más popular de <nombre>», «Mejor valoradas» / «Mejor valoradas series» y
 * «Novedades». Las etiquetas entran como funciones para probar la regla sin recursos.
 */
internal fun notflixEntityRowTitle(
    railType: TmdbEntityRailType,
    mediaType: TmdbEntityMediaType,
    entityName: String,
    popularLabel: (String) -> String,
    topRatedMoviesLabel: () -> String,
    topRatedSeriesLabel: () -> String,
    recentLabel: () -> String
): String = when (railType) {
    TmdbEntityRailType.POPULAR -> popularLabel(entityName)
    TmdbEntityRailType.TOP_RATED -> if (mediaType == TmdbEntityMediaType.TV) {
        topRatedSeriesLabel()
    } else {
        topRatedMoviesLabel()
    }
    TmdbEntityRailType.RECENT -> recentLabel()
}

/** Pide la página siguiente cuando el foco está a [threshold] elementos del final. */
internal fun notflixEntityShouldLoadMore(
    lastVisibleIndex: Int,
    totalItems: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    threshold: Int = NOTFLIX_ENTITY_LOAD_MORE_THRESHOLD
): Boolean {
    if (!hasMore || isLoading || totalItems <= 0 || lastVisibleIndex < 0) return false
    return lastVisibleIndex >= totalItems - threshold
}

// ─── Rama Notflix ───

/**
 * Rama Notflix de la página de estudio/cadena. No pide datos: usa el mismo
 * [TmdbEntityBrowseViewModel] y las mismas acciones que la pantalla oficial, que sigue dibujándose
 * igual cuando la apariencia es Nuvio.
 */
@Composable
internal fun NotflixEntityBrowseScreen(
    viewModel: TmdbEntityBrowseViewModel,
    uiState: TmdbEntityBrowseUiState,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    onBackPress: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit
) {
    val posterOptionsState by viewModel.posterOptions.state.collectAsState()

    BackHandler { onBackPress() }

    NotflixEntityBrowsePage(
        state = uiState,
        watchedMovieIds = watchedMovieIds,
        watchedSeriesIds = watchedSeriesIds,
        onRetry = viewModel::retry,
        onNavigateToDetail = { item -> onNavigateToDetail(item.id, item.apiType, null) },
        onLongPressPoster = { item -> viewModel.posterOptions.show(item, null) },
        onLoadMore = { mediaType, railType ->
            viewModel.loadMoreRail(mediaType = mediaType, railType = railType)
        }
    )

    PosterOptionsHost(
        state = posterOptionsState,
        controller = viewModel.posterOptions,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            onNavigateToDetail(id, type, addonBaseUrl.takeIf { it.isNotBlank() })
        }
    )
}

@Composable
internal fun NotflixEntityBrowsePage(
    state: TmdbEntityBrowseUiState,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    onRetry: () -> Unit,
    onNavigateToDetail: (MetaPreview) -> Unit,
    onLongPressPoster: (MetaPreview) -> Unit,
    onLoadMore: (TmdbEntityMediaType, TmdbEntityRailType) -> Unit
) {
    when (state) {
        is TmdbEntityBrowseUiState.Loading -> NotflixEntitySkeleton()
        is TmdbEntityBrowseUiState.Error -> NotflixEntityError(
            message = state.message,
            onRetry = onRetry
        )
        is TmdbEntityBrowseUiState.Success -> NotflixEntitySuccess(
            data = state.data,
            watchedMovieIds = watchedMovieIds,
            watchedSeriesIds = watchedSeriesIds,
            onNavigateToDetail = onNavigateToDetail,
            onLongPressPoster = onLongPressPoster,
            onLoadMore = onLoadMore
        )
    }
}

@Composable
private fun NotflixEntitySuccess(
    data: TmdbEntityBrowseData,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    onNavigateToDetail: (MetaPreview) -> Unit,
    onLongPressPoster: (MetaPreview) -> Unit,
    onLoadMore: (TmdbEntityMediaType, TmdbEntityRailType) -> Unit
) {
    val header = data.header
    val rails = remember(data.rails) { data.rails.filter { it.items.isNotEmpty() } }
    if (rails.isEmpty()) {
        NotflixEntityEmpty(entityName = header.name)
        return
    }

    val defaultType = remember(rails) { notflixEntityDefaultMediaType(rails) }
    var selectedTypeValue by rememberSaveable(header.id) {
        mutableStateOf(defaultType?.value)
    }
    val selectedType = remember(selectedTypeValue, defaultType) {
        TmdbEntityMediaType.entries.firstOrNull { it.value == selectedTypeValue } ?: defaultType
    }
    val bothTypes = remember(rails) { notflixEntityHasBothTypes(rails) }
    val visibleRails = remember(rails, selectedType) { notflixEntityRailsOf(rails, selectedType) }

    var focusedItem by remember(header.id) { mutableStateOf<MetaPreview?>(null) }
    LaunchedEffect(selectedType) { focusedItem = null }

    val defaultBackdrop = remember(visibleRails) {
        visibleRails.firstOrNull { it.railType == TmdbEntityRailType.POPULAR }
            ?.items?.firstOrNull { !it.backdropUrl.isNullOrBlank() }
            ?: visibleRails.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { !it.backdropUrl.isNullOrBlank() }
    }
    val backdropItem = focusedItem?.takeIf { !it.backdropUrl.isNullOrBlank() } ?: defaultBackdrop
    val secondaryLine = rememberNotflixEntitySecondaryLine(header)

    val popularLabel = stringResource(R.string.notflix_entity_popular)
    val topRatedMovies = stringResource(R.string.notflix_entity_top_rated_movies)
    val topRatedSeries = stringResource(R.string.notflix_entity_top_rated_series)
    val recentLabel = stringResource(R.string.notflix_entity_recent)
    val rowTitles = remember(
        visibleRails,
        header.name,
        popularLabel,
        topRatedMovies,
        topRatedSeries,
        recentLabel
    ) {
        visibleRails.associate { rail ->
            notflixEntityRailKey(rail) to notflixEntityRowTitle(
                railType = rail.railType,
                mediaType = rail.mediaType,
                entityName = header.name,
                popularLabel = { name -> String.format(popularLabel, name) },
                topRatedMoviesLabel = { topRatedMovies },
                topRatedSeriesLabel = { topRatedSeries },
                recentLabel = { recentLabel }
            )
        }
    }

    val listState = rememberLazyListState()
    val firstPosterRequester = remember(header.id) { FocusRequester() }
    val moviesChipRequester = remember(header.id) { FocusRequester() }
    val seriesChipRequester = remember(header.id) { FocusRequester() }
    val chipsRequester = if (selectedType == TmdbEntityMediaType.TV) {
        seriesChipRequester
    } else {
        moviesChipRequester
    }

    var initialFocusDone by rememberSaveable(header.id) { mutableStateOf(false) }
    var pendingRestoreItemId by rememberSaveable(header.id) { mutableStateOf<String?>(null) }
    var pendingRestoreRailKey by rememberSaveable(header.id) { mutableStateOf<String?>(null) }
    var restoreFocusToken by rememberSaveable(header.id) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, pendingRestoreItemId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingRestoreItemId != null) {
                restoreFocusToken += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // La fila que contiene el póster restaurado llega al viewport antes de pedir el foco.
    LaunchedEffect(restoreFocusToken, pendingRestoreRailKey, visibleRails) {
        if (restoreFocusToken <= 0 || pendingRestoreItemId == null) return@LaunchedEffect
        val railKey = pendingRestoreRailKey ?: return@LaunchedEffect
        val index = visibleRails.indexOfFirst { notflixEntityRailKey(it) == railKey }
        if (index < 0) return@LaunchedEffect
        // El índice 0 es la cabecera; la primera fila vive en el 1.
        runCatching { listState.scrollToItem(index + 1) }
    }

    // Foco inicial: el primer póster de la primera fila.
    LaunchedEffect(visibleRails, pendingRestoreItemId) {
        if (initialFocusDone || pendingRestoreItemId != null) return@LaunchedEffect
        if (visibleRails.firstOrNull()?.items?.isNotEmpty() != true) return@LaunchedEffect
        if (firstPosterRequester.requestFocusAfterFrames(frames = 2)) {
            initialFocusDone = true
        }
    }

    Box(Modifier.fillMaxSize().background(Spec.Background)) {
        NotflixEntityBackdrop(
            backdrop = backdropItem?.backdropUrl,
            backdropKey = backdropItem?.id
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("notflix-entity-page"),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "hero") {
                NotflixEntityHero(
                    header = header,
                    secondaryLine = secondaryLine,
                    showTypeChips = bothTypes,
                    selectedType = selectedType,
                    moviesRequester = moviesChipRequester,
                    seriesRequester = seriesChipRequester,
                    onSelectType = { mediaType ->
                        selectedTypeValue = mediaType.value
                        initialFocusDone = true
                    }
                )
            }

            itemsIndexed(
                items = visibleRails,
                key = { _, rail -> notflixEntityRailKey(rail) }
            ) { index, rail ->
                val railKey = notflixEntityRailKey(rail)
                NotflixEntityRowSection(
                    rail = rail,
                    title = rowTitles[railKey] ?: header.name,
                    initialFocusRequester = if (index == 0) firstPosterRequester else null,
                    upRequester = if (index == 0 && bothTypes) chipsRequester else null,
                    watched = if (rail.mediaType == TmdbEntityMediaType.TV) {
                        watchedSeriesIds
                    } else {
                        watchedMovieIds
                    },
                    pendingRestoreItemId = pendingRestoreItemId.takeIf {
                        pendingRestoreRailKey == railKey
                    },
                    restoreFocusToken = restoreFocusToken,
                    onRestoreHandled = {
                        pendingRestoreRailKey = null
                        pendingRestoreItemId = null
                    },
                    onItemFocused = { item -> focusedItem = item },
                    onClick = { item ->
                        pendingRestoreRailKey = railKey
                        pendingRestoreItemId = item.id
                        onNavigateToDetail(item)
                    },
                    onLongPress = onLongPressPoster,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun rememberNotflixEntitySecondaryLine(header: TmdbEntityHeader): String {
    val resources = LocalContext.current.resources
    return remember(header.kind, header.originCountry, header.secondaryLabel, resources) {
        notflixEntitySecondaryLine(
            kind = header.kind,
            originCountry = header.originCountry,
            secondaryLabel = header.secondaryLabel,
            kindLabel = { kind ->
                when (kind) {
                    TmdbEntityKind.COMPANY -> resources.getString(R.string.notflix_entity_kind_company)
                    TmdbEntityKind.NETWORK -> resources.getString(R.string.notflix_entity_kind_network)
                }
            },
            countryLabel = { code -> notflixEntityLocaleCountryName(code) }
        )
    }
}

/** Fondo de cine: backdrop del título enfocado, degradados y velo Background al 35 %. */
@Composable
private fun NotflixEntityBackdrop(backdrop: String?, backdropKey: String?) {
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    var shownUrl by remember { mutableStateOf<String?>(null) }
    var shownKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backdrop, backdropKey) {
        when {
            backdrop.isNullOrBlank() -> {
                shownUrl = null
                shownKey = null
            }
            reducedMotion || shownUrl == null || backdropKey == shownKey -> {
                shownUrl = backdrop
                shownKey = backdropKey
            }
            else -> {
                delay(NOTFLIX_HEADER_DELAY_MS)
                shownUrl = backdrop
                shownKey = backdropKey
            }
        }
    }

    val fade = remember(shownKey) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(shownKey) {
        if (reducedMotion) fade.snapTo(1f) else fade.animateTo(1f, tween(NOTFLIX_HEADER_FADE_MS))
    }

    Box(Modifier.fillMaxSize().testTag("notflix-entity-backdrop")) {
        val url = shownUrl
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd
            )
        } else {
            val glowRadius = with(LocalDensity.current) { 720.dp.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0f to Spec.Accent.copy(alpha = .08f),
                            1f to Color.Transparent,
                            center = androidx.compose.ui.geometry.Offset.Zero,
                            radius = glowRadius
                        )
                    )
            )
        }
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
        Box(Modifier.fillMaxSize().background(Spec.Background.copy(alpha = .35f)))
    }
}

@Composable
private fun NotflixEntityHero(
    header: TmdbEntityHeader,
    secondaryLine: String,
    showTypeChips: Boolean,
    selectedType: TmdbEntityMediaType?,
    moviesRequester: FocusRequester,
    seriesRequester: FocusRequester,
    onSelectType: (TmdbEntityMediaType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 18.dp)
            .testTag("notflix-entity-hero")
    ) {
        NotflixEntityLogo(header)

        if (secondaryLine.isNotBlank()) {
            Text(
                text = secondaryLine,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).testTag("notflix-entity-facts")
            )
        }

        header.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = Spec.Secondary.copy(alpha = .92f),
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(top = 10.dp)
                    .testTag("notflix-entity-description")
            )
        }

        if (showTypeChips) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NotflixChip(
                    label = stringResource(R.string.notflix_entity_type_movies),
                    selected = selectedType == TmdbEntityMediaType.MOVIE,
                    onClick = { onSelectType(TmdbEntityMediaType.MOVIE) },
                    modifier = Modifier
                        .focusRequester(moviesRequester)
                        .testTag("notflix-entity-type-movies")
                )
                NotflixChip(
                    label = stringResource(R.string.notflix_entity_type_series),
                    selected = selectedType == TmdbEntityMediaType.TV,
                    onClick = { onSelectType(TmdbEntityMediaType.TV) },
                    modifier = Modifier
                        .focusRequester(seriesRequester)
                        .testTag("notflix-entity-type-series")
                )
            }
        }
    }
}

@Composable
private fun NotflixEntityLogo(header: TmdbEntityHeader) {
    var logoFailed by remember(header.logo) { mutableStateOf(false) }
    val logo = header.logo
    if (!logo.isNullOrBlank() && !logoFailed) {
        AsyncImage(
            model = logo,
            contentDescription = header.name,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .height(110.dp)
                .testTag("notflix-entity-logo"),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            // Los logos de TMDB llegan casi siempre oscuros: se pintan en blanco para que se lean.
            colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
            onError = { logoFailed = true }
        )
    } else {
        Text(
            text = header.name,
            color = Spec.Text,
            fontFamily = Spec.BrandFont,
            fontSize = 64.sp,
            lineHeight = 68.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 620.dp).testTag("notflix-entity-name")
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NotflixEntityRowSection(
    rail: TmdbEntityRail,
    title: String,
    initialFocusRequester: FocusRequester?,
    upRequester: FocusRequester?,
    watched: Set<String>,
    pendingRestoreItemId: String?,
    restoreFocusToken: Int,
    onRestoreHandled: () -> Unit,
    onItemFocused: (MetaPreview?) -> Unit,
    onClick: (MetaPreview) -> Unit,
    onLongPress: (MetaPreview) -> Unit,
    onLoadMore: (TmdbEntityMediaType, TmdbEntityRailType) -> Unit
) {
    val railKey = notflixEntityRailKey(rail)
    val state = rememberLazyListState()
    val itemRequesters = remember(railKey) { mutableMapOf<String, FocusRequester>() }
    val fallbackRequester = remember(railKey) { FocusRequester() }
    val restoreFocusRequester = remember(railKey) { FocusRequester() }
    var restorePending by remember(railKey) { mutableStateOf(false) }
    var focusedItemId by remember(railKey) { mutableStateOf<String?>(null) }
    var lastLoadMoreRequestTotal by remember(railKey) { mutableIntStateOf(-1) }

    LaunchedEffect(pendingRestoreItemId, restoreFocusToken, rail.items) {
        if (restoreFocusToken <= 0 || pendingRestoreItemId.isNullOrBlank()) {
            restorePending = false
            return@LaunchedEffect
        }
        val targetIndex = rail.items.indexOfFirst { it.id == pendingRestoreItemId }
        if (targetIndex < 0) {
            restorePending = false
            return@LaunchedEffect
        }
        restorePending = true
        runCatching { state.scrollToItem(targetIndex) }
        val focused = restoreFocusRequester.requestFocusAfterFrames(frames = 2) ||
            restoreFocusRequester.requestFocusAfterFrames(frames = 3)
        if (focused) {
            // El destino ya podía tener el foco al volver de la ficha: sin cambio de foco no se
            // dispara onFocused, así que la limpieza no puede depender sólo de él. Si la petición
            // falla, el estado pendiente se conserva para reintentarlo en la próxima reanudación
            // (mismo semántico que la revisión de M47).
            restorePending = false
            onRestoreHandled()
        }
    }

    LaunchedEffect(railKey, state, rail.hasMore, rail.isLoading) {
        if (!rail.hasMore) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = state.layoutInfo
            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                val shouldLoad = notflixEntityShouldLoadMore(
                    lastVisibleIndex = lastVisible,
                    totalItems = total,
                    hasMore = rail.hasMore,
                    isLoading = rail.isLoading
                )
                if (!shouldLoad) {
                    if (lastVisible < total - NOTFLIX_ENTITY_LOAD_MORE_THRESHOLD) {
                        lastLoadMoreRequestTotal = -1
                    }
                    return@collect
                }
                if (lastLoadMoreRequestTotal != total) {
                    lastLoadMoreRequestTotal = total
                    onLoadMore(rail.mediaType, rail.railType)
                }
            }
    }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            text = title,
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Spec.GUTTER.dp, bottom = 6.dp)
                .testTag("notflix-entity-row-title-$railKey")
        )
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.hasFocus) onItemFocused(null) }
                .focusRestorer(
                    itemRequesters[focusedItemId]
                        ?: initialFocusRequester
                        ?: fallbackRequester
                )
                .testTag("notflix-entity-row-$railKey"),
            contentPadding = PaddingValues(horizontal = Spec.GUTTER.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)
        ) {
            itemsIndexed(rail.items, key = { _, item -> item.id }) { index, item ->
                val isRestoreTarget = item.id == pendingRestoreItemId && restorePending
                val requester = when {
                    isRestoreTarget -> restoreFocusRequester
                    index == 0 && initialFocusRequester != null -> initialFocusRequester
                    else -> itemRequesters.getOrPut(item.id) { FocusRequester() }
                }
                NotflixPosterCard(
                    item = item.toNotflixPosterSpec(watched = item.id in watched),
                    modifier = Modifier
                        .focusRequester(requester)
                        .then(
                            if (upRequester != null) {
                                Modifier.focusProperties { up = upRequester }
                            } else {
                                Modifier
                            }
                        ),
                    onFocused = {
                        focusedItemId = item.id
                        onItemFocused(item)
                        if (isRestoreTarget && restoreFocusToken > 0) {
                            restorePending = false
                            onRestoreHandled()
                        }
                    },
                    onClick = { onClick(item) },
                    onLongClick = { onLongPress(item) }
                )
            }

            if (rail.isLoading) {
                item(key = "__notflix_loading__") {
                    NotflixSkeletonBlock(
                        Modifier
                            .width(NotflixPosterMetrics.Width)
                            .height(NotflixPosterMetrics.Height)
                            .testTag("notflix-entity-loading-card")
                    )
                }
            }
        }
    }
}

@Composable
private fun NotflixEntitySkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 20.dp)
            .testTag("notflix-entity-skeleton")
    ) {
        NotflixSkeletonBlock(Modifier.width(320.dp).height(110.dp))
        Spacer(Modifier.height(18.dp))
        NotflixSkeletonBlock(Modifier.widthIn(max = 520.dp).fillMaxWidth(.5f).height(16.dp))
        Spacer(Modifier.height(10.dp))
        NotflixSkeletonBlock(Modifier.widthIn(max = 560.dp).fillMaxWidth(.42f).height(16.dp))
        Spacer(Modifier.height(22.dp))
        repeat(2) {
            NotflixSkeletonBlock(Modifier.width(190.dp).height(22.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)) {
                repeat(NotflixPosterMetrics.COLUMNS) {
                    NotflixSkeletonBlock(
                        Modifier
                            .width(NotflixPosterMetrics.Width)
                            .height(NotflixPosterMetrics.Height)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NotflixEntityError(message: String, onRetry: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocusAfterFrames() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(horizontal = Spec.GUTTER.dp)
            .testTag("notflix-entity-error"),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.notflix_entity_error_title),
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = message,
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        NotflixPrimaryButton(
            label = stringResource(R.string.notflix_retry),
            onClick = onRetry,
            modifier = Modifier.padding(top = 18.dp).focusRequester(requester),
            testTag = "notflix-entity-retry"
        )
    }
}

@Composable
private fun NotflixEntityEmpty(entityName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(horizontal = Spec.GUTTER.dp)
            .testTag("notflix-entity-empty"),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.notflix_entity_empty, entityName),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 28.sp
        )
    }
}
