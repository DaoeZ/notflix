package com.nuvio.tv.ui.screens.cast

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PersonDetail
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.notflix.NOTFLIX_HEADER_DELAY_MS
import com.nuvio.tv.ui.appearance.notflix.NOTFLIX_HEADER_FADE_MS
import com.nuvio.tv.ui.appearance.notflix.NotflixFlatButton
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterCard
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterMetrics
import com.nuvio.tv.ui.appearance.notflix.NotflixPrimaryButton
import com.nuvio.tv.ui.appearance.notflix.NotflixSidePanel
import com.nuvio.tv.ui.appearance.notflix.NotflixSkeletonBlock
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsController
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

// ─── Reglas puras de la página de persona (probadas en JVM) ───

/** Departamento de TMDB traducido a la línea de datos. Desconocido se omite. */
internal enum class NotflixPersonRole { ACTING, DIRECTING, WRITING, PRODUCTION }

internal fun notflixPersonRole(knownFor: String?): NotflixPersonRole? =
    when (knownFor?.trim()?.lowercase()) {
        "acting", "actors", "actress" -> NotflixPersonRole.ACTING
        "directing", "director" -> NotflixPersonRole.DIRECTING
        "writing", "writer", "screenplay" -> NotflixPersonRole.WRITING
        "production", "producer" -> NotflixPersonRole.PRODUCTION
        else -> null
    }

private val NOTFLIX_ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val NOTFLIX_DAY_FIRST_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d-M-yyyy")
private val NOTFLIX_YEAR = Regex("""\d{4}""")

/** TMDB entrega `yyyy-MM-dd`; la pantalla oficial también acepta `dd-MM-yyyy`. */
internal fun notflixParsePersonDate(raw: String?): LocalDate? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { LocalDate.parse(value, NOTFLIX_ISO_DATE) }
        .recoverCatching { LocalDate.parse(value, NOTFLIX_DAY_FIRST_DATE) }
        .getOrNull()
}

/** Edad cumplida a [today], en la zona del dispositivo (la fecha se pasa desde la pantalla). */
internal fun notflixPersonAge(birthday: String?, today: LocalDate): Int? {
    val birth = notflixParsePersonDate(birthday) ?: return null
    if (today.isBefore(birth)) return null
    return Period.between(birth, today).years
}

/**
 * Línea de datos de la persona: departamento, edad o años de vida y lugar de nacimiento, unidos
 * por « · ». Cada dato ausente se omite sin dejar separadores sueltos. Las etiquetas llegan como
 * funciones para que la regla sea pura y se pueda probar sin recursos.
 */
internal fun notflixPersonFactParts(
    knownFor: String?,
    birthday: String?,
    deathday: String?,
    placeOfBirth: String?,
    roleLabel: (NotflixPersonRole) -> String,
    ageLabel: (Int) -> String,
    lifespanLabel: (String, String, String) -> String,
    today: LocalDate = LocalDate.now()
): List<String> = buildList {
    notflixPersonRole(knownFor)?.let { role -> add(roleLabel(role)) }

    val birth = notflixParsePersonDate(birthday)
    val death = notflixParsePersonDate(deathday)
    when {
        birth != null && death != null && !death.isBefore(birth) -> {
            val ageAtDeath = Period.between(birth, death).years
            add(lifespanLabel(birth.year.toString(), death.year.toString(), ageLabel(ageAtDeath)))
        }
        birth != null -> notflixPersonAge(birthday, today)?.let { add(ageLabel(it)) }
    }

    placeOfBirth?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
}

internal fun notflixPersonDataLine(parts: List<String>): String =
    parts.filter { it.isNotBlank() }.joinToString(" · ")

/** Año de estreno de un crédito, si lo trae. */
internal fun notflixReleaseYear(releaseInfo: String?): Int? =
    NOTFLIX_YEAR.find(releaseInfo.orEmpty())?.value?.toIntOrNull()

/** Películas y series por año descendente; las que no tienen año, al final. */
internal fun notflixCreditsNewestFirst(credits: List<MetaPreview>): List<MetaPreview> =
    credits.sortedWith(
        compareByDescending<MetaPreview> { notflixReleaseYear(it.releaseInfo) ?: Int.MIN_VALUE }
    )

/**
 * «Conocido por»: los [limit] créditos más destacados mezclando películas y series. Los datos ya
 * llegan ordenados por nota de TMDB (`voteAverage`) y `voteCount` no se mapea, así que se mezcla
 * por nota descendente conservando el orden recibido en caso de empate.
 */
internal fun notflixKnownForCredits(
    movies: List<MetaPreview>,
    series: List<MetaPreview>,
    limit: Int = 12
): List<MetaPreview> =
    (movies + series)
        .distinctBy { it.id }
        .sortedByDescending { it.imdbRating ?: 0f }
        .take(limit)

/** Iniciales para el hueco sin foto: hasta dos letras en BrandFont. */
internal fun notflixPersonInitials(name: String): String =
    name.trim()
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { word -> word.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "?" }

// ─── Rama Notflix ───

/**
 * Rama Notflix de la página de persona. No pide datos: usa el mismo [CastDetailViewModel] y las
 * mismas acciones que la pantalla oficial, que sigue dibujándose igual cuando la apariencia es Nuvio.
 */
@Composable
internal fun NotflixCastDetailScreen(
    viewModel: CastDetailViewModel,
    uiState: CastDetailUiState,
    onBackPress: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit
) {
    val posterOptionsState by viewModel.posterOptions.state.collectAsState()

    BackHandler { onBackPress() }

    NotflixCastDetailPage(
        state = uiState,
        onRetry = viewModel::retry,
        onNavigateToDetail = { item -> onNavigateToDetail(item.id, item.apiType, null) },
        onLongPressPoster = { item -> viewModel.posterOptions.show(item, null) }
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
internal fun NotflixCastDetailPage(
    state: CastDetailUiState,
    onRetry: () -> Unit,
    onNavigateToDetail: (MetaPreview) -> Unit,
    onLongPressPoster: (MetaPreview) -> Unit
) {
    when (state) {
        is CastDetailUiState.Loading -> NotflixCastLoading()
        is CastDetailUiState.Error -> NotflixCastError(message = state.message, onRetry = onRetry)
        is CastDetailUiState.Success -> NotflixCastSuccess(
            person = state.personDetail,
            onNavigateToDetail = onNavigateToDetail,
            onLongPressPoster = onLongPressPoster
        )
    }
}

private data class NotflixCastRow(
    val key: String,
    val title: String,
    val credits: List<MetaPreview>
)

@Composable
private fun NotflixCastSuccess(
    person: PersonDetail,
    onNavigateToDetail: (MetaPreview) -> Unit,
    onLongPressPoster: (MetaPreview) -> Unit
) {
    val biography = person.biography?.trim()?.takeIf { it.isNotBlank() }
    val knownForCredits = remember(person.movieCredits, person.tvCredits) {
        notflixKnownForCredits(person.movieCredits, person.tvCredits)
    }
    val movies = remember(person.movieCredits) { notflixCreditsNewestFirst(person.movieCredits) }
    val series = remember(person.tvCredits) { notflixCreditsNewestFirst(person.tvCredits) }
    val directedBy = notflixPersonRole(person.knownFor) == NotflixPersonRole.DIRECTING

    val knownForTitle = if (directedBy) {
        stringResource(R.string.notflix_person_directed_by, person.name)
    } else {
        stringResource(R.string.notflix_person_known_for)
    }
    val moviesTitle = stringResource(R.string.notflix_person_movies, movies.size)
    val seriesTitle = stringResource(R.string.notflix_person_series, series.size)

    val rows = remember(knownForCredits, movies, series, knownForTitle, moviesTitle, seriesTitle) {
        buildList {
            if (knownForCredits.isNotEmpty()) add(NotflixCastRow("known", knownForTitle, knownForCredits))
            if (movies.isNotEmpty()) add(NotflixCastRow("movies", moviesTitle, movies))
            if (series.isNotEmpty()) add(NotflixCastRow("series", seriesTitle, series))
        }
    }

    val dataLine = rememberNotflixPersonDataLine(person)

    val readBioRequester = remember { FocusRequester() }
    val firstPosterRequester = remember { FocusRequester() }
    var biographyOverflows by remember(person.tmdbId) { mutableStateOf(false) }
    var biographyMeasured by remember(person.tmdbId) { mutableStateOf(biography == null) }
    var initialFocusDone by remember(person.tmdbId) { mutableStateOf(false) }
    var showBiography by rememberSaveable(person.tmdbId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var focusedCredit by remember(person.tmdbId) { mutableStateOf<MetaPreview?>(null) }
    val defaultBackdrop = remember(knownForCredits) {
        knownForCredits.firstOrNull { !it.backdropUrl.isNullOrBlank() }
    }
    val backdropCredit = focusedCredit?.takeIf { !it.backdropUrl.isNullOrBlank() } ?: defaultBackdrop

    val listState = rememberLazyListState()
    var focusedRow by remember(person.tmdbId) { mutableIntStateOf(-1) }
    var pendingRestoreRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestoreCreditId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreToken by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, pendingRestoreCreditId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingRestoreCreditId != null) {
                restoreToken += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Foco inicial: «Leer biografía» si existe; si no, el primer póster de la primera fila.
    LaunchedEffect(biographyMeasured, biographyOverflows, rows.size) {
        if (initialFocusDone || !biographyMeasured) return@LaunchedEffect
        val target = when {
            biographyOverflows -> readBioRequester
            rows.isNotEmpty() -> firstPosterRequester
            else -> null
        }
        if (target != null && target.requestFocusAfterFrames()) initialFocusDone = true
    }

    // Al bajar a una fila que no es la primera, la página deja esa fila en el tercio superior.
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    LaunchedEffect(focusedRow) {
        val row = focusedRow
        if (row <= 1) return@LaunchedEffect
        // El desplazamiento que provoca el foco se aplica en el siguiente trazado: se mide después.
        repeat(2) { withFrameNanos { } }
        val viewport = listState.layoutInfo.viewportSize.height
        if (viewport <= 0) return@LaunchedEffect
        val desired = viewport / 3f
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }
        if (info == null) {
            listState.scrollToItem(row)
        } else {
            val delta = info.offset - desired
            if (abs(delta) > 1f) {
                if (reducedMotion) listState.scrollBy(delta) else listState.animateScrollBy(delta)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Spec.Background)) {
        NotflixCastBackdrop(
            backdrop = backdropCredit?.backdropUrl,
            backdropKey = backdropCredit?.id
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("notflix-cast-page"),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "person") {
                NotflixPersonHeader(
                    person = person,
                    dataLine = dataLine,
                    biography = biography,
                    showReadButton = biographyOverflows,
                    readBioRequester = readBioRequester,
                    onReadBiography = { showBiography = true },
                    onBiographyLayout = { overflows ->
                        biographyOverflows = overflows
                        biographyMeasured = true
                    }
                )
            }

            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                NotflixCastRowSection(
                    row = row,
                    columnIndex = index + 1,
                    initialFocusRequester = if (index == 0) firstPosterRequester else null,
                    upRequester = if (biographyOverflows && index == 0) readBioRequester else null,
                    pendingRestoreCreditId = pendingRestoreCreditId.takeIf {
                        pendingRestoreRowKey == row.key
                    },
                    restoreToken = restoreToken,
                    // Igual que la pantalla oficial (onRestoreFocusHandled): el foco restaurado no
                    // se vuelve a pedir en cada ON_RESUME posterior.
                    onRestoreHandled = {
                        pendingRestoreRowKey = null
                        pendingRestoreCreditId = null
                    },
                    onCreditFocused = { focusedCredit = it },
                    onRowFocused = { rowIndex ->
                        if (focusedRow != rowIndex) focusedRow = rowIndex
                    },
                    onClick = onNavigateToDetail,
                    onLongPress = onLongPressPoster,
                    onOpened = { credit ->
                        pendingRestoreRowKey = row.key
                        pendingRestoreCreditId = credit.id
                    }
                )
            }

            if (rows.isEmpty()) {
                item(key = "empty") { NotflixCastEmpty() }
            }
        }
    }

    if (showBiography && biography != null) {
        NotflixSidePanel(
            title = person.name,
            width = 520.dp,
            onDismiss = {
                showBiography = false
                scope.launch { readBioRequester.requestFocusAfterFrames() }
            }
        ) {
            NotflixBiographyPanel(text = biography)
        }
    }
}

@Composable
private fun rememberNotflixPersonDataLine(person: PersonDetail): String {
    val resources = LocalContext.current.resources
    return notflixPersonDataLine(
        notflixPersonFactParts(
            knownFor = person.knownFor,
            birthday = person.birthday,
            deathday = person.deathday,
            placeOfBirth = person.placeOfBirth,
            roleLabel = { role -> resources.getString(role.labelRes()) },
            ageLabel = { years ->
                resources.getQuantityString(R.plurals.notflix_person_age, years, years)
            },
            lifespanLabel = { birthYear, deathYear, age ->
                resources.getString(R.string.notflix_person_lifespan, birthYear, deathYear, age)
            }
        )
    )
}

private fun NotflixPersonRole.labelRes(): Int = when (this) {
    NotflixPersonRole.ACTING -> R.string.notflix_person_role_acting
    NotflixPersonRole.DIRECTING -> R.string.notflix_person_role_directing
    NotflixPersonRole.WRITING -> R.string.notflix_person_role_writing
    NotflixPersonRole.PRODUCTION -> R.string.notflix_person_role_production
}

/** Fondo de cine: backdrop del título enfocado a la derecha, negro a la izquierda y abajo. */
@Composable
private fun NotflixCastBackdrop(backdrop: String?, backdropKey: String?) {
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

    Box(Modifier.fillMaxSize().testTag("notflix-cast-backdrop")) {
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
    }
}

@Composable
private fun NotflixPersonHeader(
    person: PersonDetail,
    dataLine: String,
    biography: String?,
    showReadButton: Boolean,
    readBioRequester: FocusRequester,
    onReadBiography: () -> Unit,
    onBiographyLayout: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        NotflixPersonPhoto(person)

        Spacer(Modifier.width(24.dp))

        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                text = person.name,
                color = Spec.Text,
                fontFamily = Spec.BrandFont,
                fontSize = 64.sp,
                lineHeight = 66.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("notflix-cast-name")
            )
            if (dataLine.isNotBlank()) {
                Text(
                    text = dataLine,
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).testTag("notflix-cast-facts")
                )
            }
            if (biography != null) {
                Text(
                    text = biography,
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> onBiographyLayout(result.hasVisualOverflow) },
                    modifier = Modifier.widthIn(max = 520.dp).padding(top = 10.dp)
                )
            }
            if (showReadButton && biography != null) {
                NotflixFlatButton(
                    label = stringResource(R.string.notflix_person_read_biography),
                    onClick = onReadBiography,
                    modifier = Modifier.padding(top = 12.dp).focusRequester(readBioRequester),
                    trailing = "",
                    testTag = "notflix-cast-read-bio"
                )
            }
        }
    }
}

@Composable
private fun NotflixPersonPhoto(person: PersonDetail) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .width(168.dp)
            .height(252.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Spec.Surface)
            .testTag("notflix-cast-photo"),
        contentAlignment = Alignment.Center
    ) {
        val photo = person.profilePhoto
        if (!photo.isNullOrBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = person.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = notflixPersonInitials(person.name),
                color = Spec.Secondary,
                fontFamily = Spec.BrandFont,
                fontSize = 64.sp,
                lineHeight = 68.sp
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun NotflixCastRowSection(
    row: NotflixCastRow,
    columnIndex: Int,
    initialFocusRequester: FocusRequester?,
    upRequester: FocusRequester?,
    pendingRestoreCreditId: String?,
    restoreToken: Int,
    onRestoreHandled: () -> Unit,
    onCreditFocused: (MetaPreview?) -> Unit,
    onRowFocused: (Int) -> Unit,
    onClick: (MetaPreview) -> Unit,
    onLongPress: (MetaPreview) -> Unit,
    onOpened: (MetaPreview) -> Unit
) {
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val keys = remember(row.credits) { row.credits.map { it.id } }
    val requesters = remember(keys, initialFocusRequester) {
        keys.mapIndexed { index, key ->
            key to if (index == 0 && initialFocusRequester != null) {
                initialFocusRequester
            } else {
                FocusRequester()
            }
        }.toMap()
    }
    var focusedKey by remember(row.key) { mutableStateOf<String?>(null) }
    val fallbackRequester = remember(row.key) { FocusRequester() }

    LaunchedEffect(restoreToken) {
        val key = pendingRestoreCreditId ?: return@LaunchedEffect
        val index = keys.indexOf(key)
        if (restoreToken <= 0 || index < 0) return@LaunchedEffect
        state.scrollToItem(index)
        if (requesters[key]?.requestFocusAfterFrames() == true) onRestoreHandled()
    }

    // Al enfocar un póster, la fila se desplaza para dejar dos tarjetas de margen a la derecha.
    LaunchedEffect(focusedKey) {
        val key = focusedKey ?: return@LaunchedEffect
        val layout = state.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return@LaunchedEffect
        val margin = with(density) {
            ((NotflixPosterMetrics.Width + NotflixPosterMetrics.Spacing) * 2).roundToPx()
        }
        val overflowRight = info.offset + info.size + margin - layout.viewportEndOffset
        val overflowLeft = info.offset - layout.viewportStartOffset
        when {
            overflowRight > 0 -> state.animateScrollBy(overflowRight.toFloat())
            overflowLeft < 0 -> state.animateScrollBy(overflowLeft.toFloat())
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            text = row.title,
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Spec.GUTTER.dp, bottom = 6.dp)
                .testTag("notflix-cast-row-title-${row.key}")
        )
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.hasFocus) onCreditFocused(null) }
                .focusRestorer(requesters[focusedKey] ?: requesters[keys.firstOrNull()] ?: fallbackRequester)
                .testTag("notflix-cast-row-${row.key}"),
            contentPadding = PaddingValues(horizontal = Spec.GUTTER.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(NotflixPosterMetrics.Spacing)
        ) {
            itemsIndexed(row.credits, key = { _, item -> item.id }) { index, item ->
                val requester = requesters[item.id] ?: remember(item.id) { FocusRequester() }
                NotflixPosterCard(
                    item = item.toNotflixPosterSpec(),
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
                        focusedKey = item.id
                        onCreditFocused(item)
                        onRowFocused(columnIndex)
                    },
                    onClick = {
                        onOpened(item)
                        onClick(item)
                    },
                    onLongClick = { onLongPress(item) }
                )
            }
        }
    }
}

@Composable
private fun NotflixBiographyPanel(text: String) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }

    LaunchedEffect(Unit) { requester.requestFocusAfterFrames(frames = 2) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .focusRequester(requester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> if (scroll.value < scroll.maxValue) {
                        scope.launch { scroll.animateScrollBy(180f) }
                        true
                    } else {
                        false
                    }
                    Key.DirectionUp -> if (scroll.value > 0) {
                        scope.launch { scroll.animateScrollBy(-180f) }
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            }
            .focusable()
            .verticalScroll(scroll)
            .testTag("notflix-cast-biography")
    ) {
        Text(
            text = text,
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
    }
}

@Composable
private fun NotflixCastLoading() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 20.dp)
            .testTag("notflix-cast-skeleton")
    ) {
        Row {
            NotflixSkeletonBlock(Modifier.width(168.dp).height(252.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                repeat(3) { index ->
                    NotflixSkeletonBlock(
                        Modifier
                            .fillMaxWidth(if (index == 0) .42f else if (index == 1) .30f else .60f)
                            .height(if (index == 0) 42.dp else 14.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        repeat(2) {
            NotflixSkeletonBlock(Modifier.width(150.dp).height(22.dp))
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
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun NotflixCastError(message: String, onRetry: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocusAfterFrames() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .padding(horizontal = Spec.GUTTER.dp)
            .testTag("notflix-cast-error"),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.cast_detail_error),
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
            testTag = "notflix-cast-retry"
        )
    }
}

@Composable
private fun NotflixCastEmpty() {
    Text(
        text = stringResource(R.string.notflix_person_empty),
        color = Spec.Secondary,
        fontFamily = Spec.BodyFont,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        modifier = Modifier
            .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 26.dp)
            .testTag("notflix-cast-empty")
    )
}
