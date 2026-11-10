package com.nuvio.tv.ui.screens.ambient

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixWordmark
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

// ─── Tiempos y medidas del salvapantallas Notflix ───

/** Cada diapositiva dura lo mismo que su movimiento lento: 20 s. */
internal const val NOTFLIX_AMBIENT_SLIDE_MS = 20_000L

/** Fundido cruzado entre diapositivas. */
internal const val NOTFLIX_AMBIENT_CROSSFADE_MS = 1_200

internal const val NOTFLIX_AMBIENT_EMPTY_POLL_MS = 500L
internal const val NOTFLIX_AMBIENT_RETRY_MS = 15_000L

/** Cada minuto el bloque de la hora se mueve para no dejar marca en el panel. */
internal const val NOTFLIX_CLOCK_DRIFT_INTERVAL_MS = 60_000L
internal const val NOTFLIX_CLOCK_MAX_DRIFT_DP = 12

// ─── Reglas puras (probadas en JVM) ───

/** «21:47» en 24 h y «9:47» en 12 h; medianoche y mediodía son «12». */
internal fun notflixClockLabel(hour: Int, minute: Int, is24Hour: Boolean): String {
    val safeHour = hour.coerceIn(0, 23)
    val safeMinute = minute.coerceIn(0, 59)
    val minutes = if (safeMinute < 10) "0$safeMinute" else safeMinute.toString()
    return if (is24Hour) {
        val hours = if (safeHour < 10) "0$safeHour" else safeHour.toString()
        "$hours:$minutes"
    } else {
        val hours = if (safeHour % 12 == 0) 12 else safeHour % 12
        "$hours:$minutes"
    }
}

/** Patrón de fecha del diseño («sábado, 8 de noviembre»), sin año. */
internal fun notflixAmbientDatePattern(locale: Locale): String =
    when (locale.language.lowercase(Locale.ROOT)) {
        "es" -> "EEEE, d 'de' MMMM"
        "en" -> "EEEE, MMMM d"
        else -> "EEEE, d MMMM"
    }

internal fun notflixAmbientDateLabel(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(notflixAmbientDatePattern(locale), locale))

/** Desplazamiento del bloque de la hora, en dp y por eje. */
internal data class NotflixClockDrift(val xDp: Float, val yDp: Float) {
    companion object {
        val None = NotflixClockDrift(0f, 0f)
    }
}

/**
 * Determinista para la misma semilla y minuto. Cada eje queda dentro de ±12 dp, así que el bloque
 * nunca se aleja más de 12 dp de su esquina.
 */
internal fun notflixClockDrift(seed: Int, tick: Int): NotflixClockDrift {
    if (tick <= 0) return NotflixClockDrift.None
    val random = Random(seed.toLong() * 73_856_093L + tick.toLong() * 19_349_663L)
    val distance = random.nextInt(3, NOTFLIX_CLOCK_MAX_DRIFT_DP + 1).toFloat()
    val dx = distance * direction(random.nextInt(3))
    val dy = distance * direction(random.nextInt(3))
    return if (dx == 0f && dy == 0f) NotflixClockDrift(distance, 0f) else NotflixClockDrift(dx, dy)
}

private fun direction(value: Int): Float = when (value) {
    0 -> 0f
    1 -> 1f
    else -> -1f
}

/** «Película · 2019 · Drama»: lo que no existe se omite sin dejar separadores sueltos. */
internal fun notflixAmbientMeta(
    movieLabel: String?,
    seriesLabel: String?,
    kind: AmbientKind?,
    year: Int?,
    genres: List<String>
): String {
    val typeLabel = when (kind) {
        AmbientKind.MOVIE -> movieLabel
        AmbientKind.SERIES -> seriesLabel
        null -> null
    }
    return buildList {
        typeLabel?.takeIf { it.isNotBlank() }?.let(::add)
        year?.let { add(it.toString()) }
        genres.filter { it.isNotBlank() }.forEach(::add)
    }.joinToString(" · ")
}

// ─── Composición ───

private class NotflixAmbientLoaded(
    val slide: AmbientSlide,
    val backdrop: ImageBitmap,
    val logo: ImageBitmap?
)

/**
 * Salvapantallas Notflix: backdrop a pantalla completa con un movimiento lento de 20 s, fundido
 * cruzado de 1,2 s, logo o nombre abajo a la izquierda, hora y fecha arriba a la derecha y la
 * marca discreta abajo a la derecha. Con movimiento reducido no hay zoom ni desplazamiento.
 */
@Composable
fun NotflixAmbientSlideshow(
    slides: List<AmbientSlide>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val liveReducedMotion by rememberUpdatedState(reducedMotion)
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    var current by remember { mutableStateOf<NotflixAmbientLoaded?>(null) }
    var previous by remember { mutableStateOf<NotflixAmbientLoaded?>(null) }
    val crossfadeAlpha = remember { Animatable(0f) }
    val motionProgress = remember { Animatable(0f) }
    val latestSlides by rememberUpdatedState(slides)

    LaunchedEffect(Unit) {
        coroutineScope {
            var index = 0
            var misses = 0
            var pending: Deferred<NotflixAmbientLoaded?>? = null
            while (true) {
                val available = latestSlides
                if (available.isEmpty()) {
                    pending?.cancel()
                    pending = null
                    delay(NOTFLIX_AMBIENT_EMPTY_POLL_MS)
                    continue
                }
                val loaded = (
                    pending ?: async {
                        loadNotflixAmbientSlide(
                            context,
                            available[index % available.size],
                            screenWidthPx,
                            screenHeightPx
                        )
                    }
                    ).await()
                pending = null
                index += 1
                if (loaded == null) {
                    misses += 1
                    if (misses >= available.size) {
                        misses = 0
                        delay(NOTFLIX_AMBIENT_RETRY_MS)
                    }
                    continue
                }
                misses = 0
                previous = current
                current = loaded
                motionProgress.snapTo(0f)
                if (liveReducedMotion) {
                    crossfadeAlpha.snapTo(1f)
                } else {
                    crossfadeAlpha.snapTo(0f)
                    crossfadeAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(NOTFLIX_AMBIENT_CROSSFADE_MS, easing = FastOutSlowInEasing)
                    )
                }
                // El fundido ya terminó: la diapositiva anterior deja de ocupar memoria.
                previous = null
                // La siguiente se precarga durante la espera larga, con dos imágenes como máximo.
                val upcoming = latestSlides
                if (upcoming.isNotEmpty()) {
                    pending = async {
                        loadNotflixAmbientSlide(
                            context,
                            upcoming[index % upcoming.size],
                            screenWidthPx,
                            screenHeightPx
                        )
                    }
                }
                val motionJob = if (liveReducedMotion) {
                    null
                } else {
                    launch { motionProgress.animateTo(1f, tween(NOTFLIX_AMBIENT_SLIDE_MS.toInt(), easing = LinearEasing)) }
                }
                delay(NOTFLIX_AMBIENT_SLIDE_MS)
                motionJob?.cancel()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Spec.Background)
            .testTag("notflix-ambient-slideshow")
    ) {
        previous?.let { slide ->
            NotflixAmbientImage(
                loaded = slide,
                alpha = 1f,
                motionProgress = 1f,
                reducedMotion = reducedMotion
            )
        }
        current?.let { slide ->
            NotflixAmbientImage(
                loaded = slide,
                alpha = crossfadeAlpha.value,
                motionProgress = motionProgress.value,
                reducedMotion = reducedMotion
            )
        }
        // Negro desde abajo: transparente por encima del 55 % de la altura.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .55f to Color.Black.copy(alpha = .55f),
                        1f to Color.Black
                    )
                )
        )
        current?.let { slide ->
            NotflixAmbientCopy(slide = slide, alpha = crossfadeAlpha.value)
        }
        NotflixAmbientClock(reducedMotion = reducedMotion, modifier = Modifier.align(Alignment.TopEnd))
        NotflixWordmark(
            height = 18.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(48.dp)
                .graphicsLayer { alpha = .6f }
        )
    }
}

@Composable
private fun NotflixAmbientImage(
    loaded: NotflixAmbientLoaded,
    alpha: Float,
    motionProgress: Float,
    reducedMotion: Boolean
) {
    val pan = if (loaded.slide.id.hashCode() and 1 == 0) 1f else -1f
    Image(
        bitmap = loaded.backdrop,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                if (reducedMotion) {
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                } else {
                    val scale = 1f + .06f * motionProgress
                    scaleX = scale
                    scaleY = scale
                    translationX = pan * .02f * motionProgress * size.width
                }
            }
    )
}

@Composable
private fun NotflixAmbientCopy(slide: NotflixAmbientLoaded, alpha: Float) {
    val meta = notflixAmbientMeta(
        movieLabel = stringResource(R.string.notflix_ambient_type_movie),
        seriesLabel = stringResource(R.string.notflix_ambient_type_series),
        kind = slide.slide.kind,
        year = slide.slide.year,
        genres = slide.slide.genres
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, bottom = 48.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.Bottom
    ) {
        val logo = slide.logo
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = slide.slide.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .heightIn(max = 110.dp)
            )
        } else {
            Text(
                text = slide.slide.title,
                color = Spec.Text,
                fontFamily = Spec.BrandFont,
                fontSize = 56.sp,
                lineHeight = 60.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun NotflixAmbientClock(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val locale = remember { Locale.getDefault() }
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var tick by remember { mutableIntStateOf(0) }
    val driftSeed = remember { Random.nextInt() }

    LaunchedEffect(Unit) {
        while (true) {
            val moment = LocalDateTime.now()
            now = moment
            val untilNextMinute = 60_000L - moment.second * 1_000L - moment.nano / 1_000_000L
            delay(untilNextMinute.coerceAtLeast(1_000L))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NOTFLIX_CLOCK_DRIFT_INTERVAL_MS)
            tick += 1
        }
    }

    val drift = notflixClockDrift(seed = driftSeed, tick = tick)
    val driftSpec = if (reducedMotion) snap<Float>() else tween(800)
    val driftX by animateFloatAsState(drift.xDp, driftSpec, label = "notflix-clock-drift-x")
    val driftY by animateFloatAsState(drift.yDp, driftSpec, label = "notflix-clock-drift-y")
    val offsetX = with(density) { driftX.dp.toPx() }
    val offsetY = with(density) { driftY.dp.toPx() }

    Column(
        modifier = modifier
            .padding(48.dp)
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
            }
            .testTag("notflix-ambient-clock"),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = notflixClockLabel(now.hour, now.minute, is24Hour),
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = notflixAmbientDateLabel(now.toLocalDate(), locale),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private suspend fun loadNotflixAmbientSlide(
    context: Context,
    slide: AmbientSlide,
    widthPx: Int,
    heightPx: Int
): NotflixAmbientLoaded? = coroutineScope {
    val logo = slide.logoUrl?.let { url -> async { loadNotflixAmbientBitmap(context, url, null) } }
    val backdrop = loadNotflixAmbientBitmap(context, slide.backdropUrl, widthPx to heightPx) ?: run {
        logo?.cancel()
        return@coroutineScope null
    }
    NotflixAmbientLoaded(slide, backdrop, logo?.await())
}

private suspend fun loadNotflixAmbientBitmap(
    context: Context,
    url: String,
    targetSize: Pair<Int, Int>?
): ImageBitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .apply { targetSize?.let { (width, height) -> size(width, height) } }
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
}
