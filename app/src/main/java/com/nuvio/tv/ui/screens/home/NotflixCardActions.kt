package com.nuvio.tv.ui.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.util.parseRuntimeMinutes
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import java.util.Date
import kotlinx.coroutines.delay

/** Official library/watched state for one title, read from HomeUiState; null means not known. */
data class NotflixCardStatus(
    val inLibrary: Boolean? = null, val libraryPending: Boolean = false,
    val watched: Boolean? = null, val watchedPending: Boolean = false
)

/** Official Home callbacks and state for the expanded card; Home owns every write. */
class NotflixCardActionsHost(
    val status: (MetaPreview) -> NotflixCardStatus,
    val manageLists: Boolean,
    val onExpanded: (MetaPreview) -> Unit,
    val onLibrary: (MetaPreview, String) -> Unit,
    val onWatched: (MetaPreview) -> Unit,
    val onHide: (MetaPreview) -> Unit = {}
)

internal enum class NotflixCardAction { PLAY, INFO, LIBRARY, WATCHED, SOUND, HIDE }

internal data class NotflixCardActionUi(
    val action: NotflixCardAction, val label: String, val state: String?, val enabled: Boolean, val icon: ImageVector
)

/** Virtual action cursor; Compose focus never leaves the card, so expansion and preview lease survive. */
internal data class NotflixCardCursor(val identity: String, val index: Int, val armed: Boolean = false, val artwork: Boolean = false)

internal enum class NotflixCardKey { UP, DOWN, LEFT, RIGHT, CENTER, OTHER }

internal sealed interface NotflixCardKeyResult {
    data object Pass : NotflixCardKeyResult
    data object Consumed : NotflixCardKeyResult
    data class Activate(val index: Int) : NotflixCardKeyResult
    data object ActivateArtwork : NotflixCardKeyResult
    data object ExitDown : NotflixCardKeyResult
}

/**
 * Pure remote-key policy. Down (fresh press) enters the actions of an expanded card; held Down keeps
 * moving rows. Inside: Left/Right walk, Up leaves to the card, Down leaves to the next row, Center
 * activates once on its own up after a fresh down, never on repeats or an inherited press.
 * When the trailer is already playing, Up from the actions focuses the artwork and Down returns to
 * the action that was focused.
 */
internal fun notflixCardKey(
    cursor: NotflixCardCursor?, identity: String, count: Int, key: NotflixCardKey,
    down: Boolean, repeat: Int, forward: Boolean, trailerPlaying: Boolean = false
): Pair<NotflixCardCursor?, NotflixCardKeyResult> {
    val active = cursor?.takeIf { it.identity == identity && count > 0 }
    if (active == null) {
        return if (count > 0 && key == NotflixCardKey.DOWN && down && repeat == 0)
            NotflixCardCursor(identity, 0) to NotflixCardKeyResult.Consumed
        else null to NotflixCardKeyResult.Pass
    }
    if (active.artwork) {
        return when (key) {
            NotflixCardKey.CENTER -> when {
                down && repeat == 0 -> active.copy(armed = true) to NotflixCardKeyResult.Consumed
                down -> active to NotflixCardKeyResult.Consumed
                active.armed -> active.copy(armed = false) to NotflixCardKeyResult.ActivateArtwork
                else -> active to NotflixCardKeyResult.Consumed
            }
            NotflixCardKey.DOWN -> if (down) {
                active.copy(artwork = false, armed = false) to NotflixCardKeyResult.Consumed
            } else active to NotflixCardKeyResult.Consumed
            // Left/Right leave the artwork and keep the normal row movement; Up leaves the row.
            NotflixCardKey.LEFT, NotflixCardKey.RIGHT ->
                if (down) null to NotflixCardKeyResult.Pass else active to NotflixCardKeyResult.Consumed
            NotflixCardKey.UP -> if (down) null to NotflixCardKeyResult.Pass else active to NotflixCardKeyResult.Consumed
            NotflixCardKey.OTHER -> active to NotflixCardKeyResult.Pass
        }
    }
    val index = active.index.coerceIn(0, count - 1)
    return when (key) {
        NotflixCardKey.CENTER -> when {
            down && repeat == 0 -> active.copy(index = index, armed = true) to NotflixCardKeyResult.Consumed
            down -> active to NotflixCardKeyResult.Consumed
            active.armed -> active.copy(index = index, armed = false) to NotflixCardKeyResult.Activate(index)
            else -> active to NotflixCardKeyResult.Consumed
        }
        NotflixCardKey.LEFT, NotflixCardKey.RIGHT -> {
            if (!down) active to NotflixCardKeyResult.Consumed
            else {
                val step = if ((key == NotflixCardKey.RIGHT) == forward) 1 else -1
                NotflixCardCursor(identity, (index + step).coerceIn(0, count - 1)) to NotflixCardKeyResult.Consumed
            }
        }
        NotflixCardKey.UP -> if (down && trailerPlaying) {
            active.copy(artwork = true, armed = false) to NotflixCardKeyResult.Consumed
        } else if (down) null to NotflixCardKeyResult.Consumed
        else active to NotflixCardKeyResult.Consumed
        NotflixCardKey.DOWN -> if (down) null to NotflixCardKeyResult.ExitDown else active to NotflixCardKeyResult.Consumed
        NotflixCardKey.OTHER -> active to NotflixCardKeyResult.Pass
    }
}

/**
 * M32: the virtual cursor is read lazily only by a card that really shows actions. A collapsed card
 * used to read it too, so moving inside one panel invalidated every visible card in the row.
 */
internal fun notflixCardCursorIndex(
    withActions: Boolean, cursor: () -> NotflixCardCursor?, identity: String, actionCount: Int
): Int? = if (!withActions || actionCount <= 0) null
    else cursor()?.takeIf { it.identity == identity && !it.artwork }?.index?.coerceIn(0, actionCount - 1)

/** Positive official runtime up to a day; anything else is omitted rather than guessed. */
internal fun notflixRuntimeMinutes(runtime: String?): Int? = parseRuntimeMinutes(runtime)?.takeIf { it in 1..1440 }

internal fun notflixEndsAt(nowMillis: Long, minutes: Int?): Long? = minutes?.let { nowMillis + it * 60_000L }

@Composable
internal fun notflixCardActions(meta: MetaPreview, host: NotflixCardActionsHost, sound: PreviewSoundUi?, canPlay: Boolean): List<NotflixCardActionUi> {
    val status = host.status(meta)
    val pending = stringResource(R.string.notflix_card_pending)
    return buildList {
        if (canPlay) add(NotflixCardActionUi(NotflixCardAction.PLAY, stringResource(R.string.notflix_billboard_play), null, true, Icons.Filled.PlayArrow))
        add(NotflixCardActionUi(NotflixCardAction.INFO, stringResource(R.string.notflix_billboard_info), null, true, Icons.Filled.Info))
        run {
            val inLibrary = status.inLibrary
            add(NotflixCardActionUi(NotflixCardAction.LIBRARY,
                when {
                    host.manageLists -> stringResource(R.string.library_manage_lists)
                    inLibrary == true -> stringResource(R.string.hero_remove_from_library)
                    else -> stringResource(R.string.hero_add_to_library)
                },
                when {
                    status.libraryPending -> pending
                    inLibrary == null -> stringResource(R.string.notflix_card_library_checking)
                    inLibrary -> stringResource(R.string.notflix_card_state_in_library)
                    else -> stringResource(R.string.notflix_card_state_not_in_library)
                },
                !status.libraryPending && (inLibrary != null || host.manageLists),
                if (inLibrary == true) Icons.Filled.Check else Icons.Filled.Add))
            val watchable = meta.apiType.lowercase() in setOf("movie", "series", "tv")
            if (watchable) {
                // Titles outside the observed catalog rows have no status yet; like Nuvio's poster menu,
                // treat them as unwatched so the action stays usable (promoter 08-11).
                val watched = status.watched ?: false
                add(NotflixCardActionUi(NotflixCardAction.WATCHED,
                    if (watched == true) stringResource(R.string.hero_mark_unwatched) else stringResource(R.string.hero_mark_watched),
                    when {
                        status.watchedPending -> pending
                        watched -> stringResource(R.string.notflix_card_state_watched)
                        else -> stringResource(R.string.notflix_card_state_unwatched)
                    },
                    !status.watchedPending,
                    if (watched == true) Icons.Filled.VisibilityOff else Icons.Filled.Visibility))
            }
        }
        when (sound) {
            null -> Unit
            PreviewSoundUi.Loading -> add(NotflixCardActionUi(NotflixCardAction.SOUND,
                stringResource(R.string.notflix_card_sound_on), stringResource(R.string.notflix_card_sound_loading), false, Icons.AutoMirrored.Filled.VolumeOff))
            is PreviewSoundUi.Ready -> add(NotflixCardActionUi(NotflixCardAction.SOUND,
                stringResource(if (sound.audible) R.string.notflix_card_sound_off else R.string.notflix_card_sound_on),
                stringResource(when {
                    sound.systemSilenced -> R.string.notflix_card_sound_system
                    sound.audible -> R.string.notflix_card_state_sound_on
                    else -> R.string.notflix_card_state_muted
                }),
                !sound.systemSilenced,
                if (sound.audible && !sound.systemSilenced) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff))
        }
        add(NotflixCardActionUi(
            NotflixCardAction.HIDE,
            stringResource(R.string.notflix_hide_title),
            null,
            true,
            Icons.Filled.VisibilityOff
        ))
    }
}

/** Expanded panel: actions row, then IMDb · year · runtime · ends-at, then genres. One minute clock per visible panel. */
@Composable
internal fun NotflixCardPanel(
    meta: MetaPreview, identity: String, actions: List<NotflixCardActionUi>, cursor: Int?, hasOptions: Boolean,
    noArtwork: Boolean, modifier: Modifier, onActivate: (Int) -> Unit, synopsisLines: Int = 0
) {
    val context = LocalContext.current
    val minutes = notflixRuntimeMinutes(meta.runtime)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(minutes) {
        if (minutes == null) return@LaunchedEffect
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            delay(60_000L - current % 60_000L)
        }
    }
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    val endsAt = notflixEndsAt(now, minutes)?.let { stringResource(R.string.player_ends_at, formatter.format(Date(it))) }
    val optionsDescription = stringResource(R.string.notflix_hold_options)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, action ->
                val selected = cursor == index
                Box(Modifier.size(28.dp)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Spec.Text else Spec.Secondary.copy(.55f), CircleShape)
                    .background(if (action.action == NotflixCardAction.PLAY) Spec.Text else Spec.Background.copy(.6f), CircleShape)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = action.label
                        action.state?.let { stateDescription = it }
                        this.selected = selected
                        if (!action.enabled) disabled()
                        onClick(action.label) { if (action.enabled) onActivate(index); action.enabled }
                    }.testTag("notflix-card-action-$identity-${action.action.name.lowercase()}"),
                    contentAlignment = Alignment.Center) {
                    Icon(action.icon, null, Modifier.size(18.dp),
                        tint = when {
                            action.action == NotflixCardAction.PLAY -> Spec.Background
                            action.enabled -> Spec.Text
                            else -> Spec.Secondary.copy(.5f)
                        })
                }
            }
            Spacer(Modifier.weight(1f))
            if (hasOptions) Text("⋯", color = Spec.Secondary, fontSize = 15.sp,
                modifier = Modifier.semantics { contentDescription = optionsDescription })
        }
        val focusedLabel = cursor?.let { actions.getOrNull(it.coerceIn(0, (actions.size - 1).coerceAtLeast(0))) }
        if (focusedLabel != null) Text(listOfNotNull(focusedLabel.label, focusedLabel.state?.takeIf { !focusedLabel.enabled || focusedLabel.action == NotflixCardAction.SOUND }).joinToString(" · "),
            color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("notflix-card-action-label-$identity"))
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            RatingBadge(meta.imdbRating)
            val facts = listOfNotNull(meta.releaseInfo?.takeIf { it.isNotBlank() },
                minutes?.let { stringResource(R.string.notflix_runtime_minutes, it) }).joinToString(" · ")
            // The end time never truncates on the narrow card; year/runtime yield space first.
            if (facts.isNotEmpty()) Text(facts, color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("notflix-card-facts-$identity"))
            if (endsAt != null) Text(endsAt, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 12.sp,
                maxLines = 1, softWrap = false, modifier = Modifier.testTag("notflix-card-ends-$identity"))
        }
        val genres = meta.genres.take(3).joinToString(" · ")
        if (genres.isNotBlank()) Text(genres, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Same fallback as before M25: with neither genres nor artwork the synopsis is the useful information.
        else if (noArtwork && synopsisLines == 0) meta.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Spec.Secondary,
            fontFamily = Spec.BodyFont, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        // Landscape side panel (M29) has room for the synopsis under the expanded box.
        if (synopsisLines > 0) meta.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Spec.Secondary,
            fontFamily = Spec.BodyFont, fontSize = 11.sp, lineHeight = 14.sp, maxLines = synopsisLines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("notflix-card-synopsis-$identity")) }
    }
}
