package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

internal enum class QuickPickFilter {
    SURPRISE,
    SHORT_MOVIE,
    TOP_RATED,
    MY_LIST_UNWATCHED,
    ANIMATION
}

internal fun filterQuickPickCandidates(
    pool: List<MetaPreview>,
    myList: List<MetaPreview>,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    filter: QuickPickFilter,
    isKids: Boolean
): List<MetaPreview> {
    val base = when (filter) {
        QuickPickFilter.MY_LIST_UNWATCHED -> myList
        else -> pool
    }.asSequence()
        .distinctBy { "${it.apiType.lowercase()}:${it.id.lowercase()}" }
        .filterNot { item ->
            val isSeries = item.apiType.equals("series", true) || item.apiType.equals("tv", true)
            if (isSeries) item.id in watchedSeriesIds else item.id in watchedMovieIds
        }

    return when (filter) {
        QuickPickFilter.SURPRISE -> base.toList()
        QuickPickFilter.SHORT_MOVIE -> {
            val maxMin = if (isKids) 90 else 100
            val withRuntime = base.filter { item ->
                val mins = parseRuntimeMinutes(item.runtime)
                mins != null && mins in 30..maxMin
            }.toList()
            if (withRuntime.isNotEmpty()) withRuntime else base.filter { it.apiType.equals("movie", true) }.toList()
        }
        QuickPickFilter.TOP_RATED -> {
            val rated = base.filter { (it.imdbRating ?: 0f) >= 7.5f }.sortedByDescending { it.imdbRating ?: 0f }.toList()
            if (rated.isNotEmpty()) rated else base.sortedByDescending { it.imdbRating ?: 0f }.toList()
        }
        QuickPickFilter.MY_LIST_UNWATCHED -> base.toList()
        QuickPickFilter.ANIMATION -> {
            val anim = base.filter { item ->
                item.genres.any { g -> g.contains("anim", ignoreCase = true) || g.contains("famil", ignoreCase = true) }
            }.toList()
            if (anim.isNotEmpty()) anim else base.toList()
        }
    }
}

internal fun parseRuntimeMinutes(runtime: String?): Int? =
    com.nuvio.tv.core.util.parseRuntimeMinutes(runtime)

/**
 * Zero-network D-pad Quick Pick overlay for TV (works for both standard and Kids profiles).
 */
@Composable
internal fun NotflixQuickPickDialog(
    pool: List<MetaPreview>,
    myList: List<MetaPreview>,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    isKids: Boolean,
    onDismiss: () -> Unit,
    onPlay: ((MetaPreview) -> Unit)?,
    onDetails: (MetaPreview) -> Unit
) {
    val filters = remember(isKids) {
        if (isKids) {
            listOf(
                QuickPickFilter.SURPRISE,
                QuickPickFilter.SHORT_MOVIE,
                QuickPickFilter.ANIMATION,
                QuickPickFilter.MY_LIST_UNWATCHED
            )
        } else {
            listOf(
                QuickPickFilter.SURPRISE,
                QuickPickFilter.SHORT_MOVIE,
                QuickPickFilter.TOP_RATED,
                QuickPickFilter.MY_LIST_UNWATCHED
            )
        }
    }
    var selectedFilter by remember { mutableStateOf(QuickPickFilter.SURPRISE) }
    var pickSeed by remember { mutableIntStateOf(0) }
    val candidates = remember(pool, myList, watchedMovieIds, watchedSeriesIds, selectedFilter, isKids) {
        filterQuickPickCandidates(pool, myList, watchedMovieIds, watchedSeriesIds, selectedFilter, isKids)
    }
    val orderedCandidates = remember(candidates) {
        if (candidates.size <= 1) candidates
        else candidates.indices.sortedBy { idx -> ((idx * 2654435761L) xor candidates[idx].id.hashCode().toLong()) }
            .map { candidates[it] }
    }
    val picked = remember(orderedCandidates, pickSeed) {
        if (orderedCandidates.isEmpty()) null else orderedCandidates[Math.floorMod(pickSeed, orderedCandidates.size)]
    }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 520.dp, max = 760.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Spec.Background)
                .border(1.dp, Spec.Divider, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(if (isKids) R.string.quick_pick_kids_title else R.string.quick_pick_title),
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEachIndexed { idx, filter ->
                    val active = selectedFilter == filter
                    val label = when (filter) {
                        QuickPickFilter.SURPRISE -> stringResource(R.string.quick_pick_surprise)
                        QuickPickFilter.SHORT_MOVIE -> stringResource(if (isKids) R.string.quick_pick_bedtime else R.string.quick_pick_short)
                        QuickPickFilter.TOP_RATED -> stringResource(R.string.quick_pick_top_rated)
                        QuickPickFilter.MY_LIST_UNWATCHED -> stringResource(R.string.quick_pick_my_list)
                        QuickPickFilter.ANIMATION -> stringResource(R.string.quick_pick_animation)
                    }
                    QuickPickChip(
                        label = label,
                        selected = active,
                        modifier = if (idx == 0) Modifier.focusRequester(initialFocus) else Modifier,
                        onClick = {
                            selectedFilter = filter
                            pickSeed++
                        }
                    )
                }
            }

            if (picked == null) {
                Text(
                    text = stringResource(R.string.quick_pick_empty),
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = picked.poster ?: picked.background,
                        contentDescription = picked.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 136.dp, height = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Spec.Surface)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = picked.name,
                            color = Spec.Text,
                            fontFamily = Spec.BodyFont,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metaLine = listOfNotNull(
                            picked.releaseInfo?.takeIf { it.isNotBlank() }?.take(4),
                            picked.runtime?.takeIf { it.isNotBlank() },
                            picked.imdbRating?.takeIf { it > 0f }?.let { "★ ${"%.1f".format(it)}" }
                        ).joinToString(" · ")
                        if (metaLine.isNotEmpty()) {
                            Text(metaLine, color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 13.sp)
                        }
                        picked.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                text = desc,
                                color = Spec.Secondary,
                                fontFamily = Spec.BodyFont,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (onPlay != null) {
                                QuickPickChip(
                                    label = stringResource(R.string.quick_pick_play),
                                    selected = true,
                                    accent = true,
                                    onClick = { onPlay(picked) }
                                )
                            }
                            QuickPickChip(
                                label = stringResource(R.string.quick_pick_details),
                                selected = false,
                                onClick = { onDetails(picked) }
                            )
                            QuickPickChip(
                                label = stringResource(R.string.quick_pick_another),
                                selected = false,
                                onClick = { pickSeed++ }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Spec.Text
        accent -> Spec.Accent
        selected -> Spec.Text.copy(alpha = 0.22f)
        else -> Spec.Surface
    }
    val fg = when {
        focused -> Spec.Background
        else -> Spec.Text
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.5.dp, if (focused) Spec.Text else Spec.Divider, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = Spec.BodyFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
