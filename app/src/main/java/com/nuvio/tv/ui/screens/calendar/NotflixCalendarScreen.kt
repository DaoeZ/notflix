package com.nuvio.tv.ui.screens.calendar

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.data.repository.CalendarDay
import com.nuvio.tv.data.repository.CalendarEntry
import com.nuvio.tv.data.repository.CalendarNuvioShow
import com.nuvio.tv.data.repository.CalendarSource
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.ui.components.SkeletonBar
import com.nuvio.tv.ui.components.rememberShimmerBrush
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CARD_WIDTH = 150.dp
private val CARD_HEIGHT = 266.dp
private val POSTER_HEIGHT = 200.dp

/**
 * Calendario Notflix: próximos estrenos de las series que sigue el perfil. Trakt si hay cuenta;
 * si no, los próximos episodios que ya calcula Nuvio.
 */
@Composable
fun NotflixCalendarScreen(
    nuvioUpcoming: List<CalendarNuvioShow>,
    onNavigateToDetail: (contentId: String, contentType: String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Al entrar de nuevo en la pantalla se refresca; dentro de la misma visita solo se recarga
    // cuando cambia la fuente de Nuvio (evita repetir la petición a Trakt).
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(nuvioUpcoming) {
        viewModel.load(nuvioUpcoming, force = firstLoad)
        firstLoad = false
    }

    val firstCardFocus = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.error, state.days) {
        if (!state.loading && !state.error && state.days.isNotEmpty() && !focusRequested) {
            focusRequested = true
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    val sourceLabel = stringResource(
        when (state.source) {
            CalendarSource.TRAKT -> R.string.notflix_calendar_source_trakt
            CalendarSource.NUVIO -> R.string.notflix_calendar_source_nuvio
        }
    )
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().background(Spec.Background)) {
        Column(Modifier.padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 18.dp)) {
            Text(
                text = stringResource(R.string.notflix_calendar_title),
                color = Spec.Text,
                fontFamily = Spec.BrandFont,
                fontSize = 40.sp,
                lineHeight = 46.sp
            )
            Text(
                text = sourceLabel,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.error -> CalendarError(onRetry = viewModel::retry)
            state.loading -> CalendarSkeleton()
            state.days.isEmpty() -> CalendarEmpty()
            else -> CalendarDays(
                days = state.days,
                posters = state.posters,
                today = today,
                firstCardFocus = firstCardFocus,
                onNeedPoster = viewModel::ensurePoster,
                onClick = onNavigateToDetail
            )
        }
    }
}

@Composable
private fun CalendarDays(
    days: List<CalendarDay>,
    posters: Map<String, String>,
    today: LocalDate,
    firstCardFocus: FocusRequester,
    onNeedPoster: (CalendarEntry) -> Unit,
    onClick: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 44.dp)
    ) {
        itemsIndexed(days, key = { _, day -> day.date.toEpochDay() }) { dayIndex, day ->
            Column(Modifier.padding(bottom = 18.dp)) {
                Text(
                    text = dayLabel(day.date, today),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(7.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(day.entries) { entryIndex, entry ->
                        CalendarCard(
                            entry = entry,
                            poster = entry.poster?.takeIf { it.isNotBlank() } ?: posters[entry.contentId],
                            focusRequester = if (dayIndex == 0 && entryIndex == 0) firstCardFocus else null,
                            onNeedPoster = { onNeedPoster(entry) },
                            onClick = { onClick(entry.contentId, entry.contentType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    entry: CalendarEntry,
    poster: String?,
    focusRequester: FocusRequester?,
    onNeedPoster: () -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(entry.contentId, poster) {
        if (poster.isNullOrBlank()) onNeedPoster()
    }
    var focused by remember { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(Spec.FOCUS_MS),
        label = "notflix-calendar-focus"
    )
    val episodeCode = if (entry.season != null && entry.episode != null) {
        stringResource(R.string.notflix_calendar_episode, entry.season, entry.episode)
    } else {
        null
    }
    val episodeLine = listOfNotNull(episodeCode, entry.episodeTitle).joinToString(" · ")
    val locale = LocalConfiguration.current.locales[0]
    val time = if (entry.hasTime) {
        remember(entry.airInstant, locale) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .format(entry.airInstant.atZone(ZoneId.systemDefault()))
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(5.dp))
            .background(Spec.Surface)
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, RoundedCornerShape(5.dp))
            .clickable(role = Role.Button, onClick = { confirmSound(); onClick() })
            .semantics { contentDescription = entry.seriesName }
            .padding(5.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(POSTER_HEIGHT)
                .clip(RoundedCornerShape(3.dp))
                .background(Spec.Background)
        ) {
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = entry.seriesName,
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = entry.seriesName,
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = episodeLine,
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (time != null) {
            Text(
                text = time,
                color = Spec.Accent,
                fontFamily = Spec.BodyFont,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return stringResource(R.string.notflix_calendar_today)
    if (date == today.plusDays(1)) return stringResource(R.string.notflix_calendar_tomorrow)
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(locale)
            .format(date)
            .replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(locale) else first.toString()
            }
    }
}

@Composable
private fun CalendarSkeleton() {
    val brush = rememberShimmerBrush()
    Column(Modifier.fillMaxSize().padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp)) {
        repeat(3) {
            SkeletonBar(width = 150.dp, height = 16.dp, brush = brush)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) {
                    SkeletonBar(
                        width = CARD_WIDTH,
                        height = CARD_HEIGHT,
                        brush = brush,
                        cornerRadius = 5.dp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CalendarEmpty() {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spec.GUTTER.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.notflix_calendar_empty),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun CalendarError(onRetry: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spec.GUTTER.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.notflix_calendar_error),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = 2.dp,
                    color = if (focused) Spec.Text else Spec.Secondary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.notflix_retry),
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 14.sp
            )
        }
    }
}
