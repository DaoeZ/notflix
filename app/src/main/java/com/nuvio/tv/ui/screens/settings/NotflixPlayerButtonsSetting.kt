package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.domain.model.NotflixPlayerAction
import com.nuvio.tv.domain.model.NotflixPlayerActions
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.notflix.*
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.player.notflixPlayerActionLabel

/** One settings row shared by Essential and Advanced; the existing ViewModel owns every write. */
@Composable
internal fun NotflixPlayerButtonsSetting(settings: PlayerSettings, profileId: () -> Int,
    onSave: (List<String>, Int) -> Unit) {
    if (!LocalNotflixPresentation.current.active) return
    var open by rememberSaveable { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(open) { if (!open && opened) requester.requestFocusAfterFrames(2) }
    SettingsActionRow(title = stringResource(R.string.notflix_player_buttons), subtitle = null,
        settingId = "NotflixPlayerButtonsSetting.buttons",
        value = (settings.notflixPlayerButtons.size + 1).toString(),
        modifier = Modifier.focusRequester(requester).testTag("notflix-player-buttons-setting"),
        onClick = { editingProfile = profileId(); opened = true; open = true })
    if (open) NotflixPlayerButtonsEditor(settings.notflixPlayerButtons, onSave = { ids ->
        onSave(ids, editingProfile); open = false
    })
}

private val PlayerButtonsSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

@Composable
internal fun NotflixPlayerButtonsEditor(selected: List<String>, onSave: (List<String>) -> Unit) {
    var ordered by rememberSaveable(stateSaver = PlayerButtonsSaver) { mutableStateOf(NotflixPlayerActions.normalize(selected)) }
    val list = rememberLazyListState()
    var pending by remember { mutableStateOf<String?>(null) }
    val orderRequester = remember { FocusRequester() }
    val firstRequester = remember { FocusRequester() }
    val labels = NotflixPlayerAction.entries.associateWith { notflixPlayerActionLabel(it) }
    LaunchedEffect(Unit) { firstRequester.requestFocusAfterFrames(2) }
    LaunchedEffect(pending) {
        val id = pending ?: return@LaunchedEffect
        val index = ordered.indexOf(id)
        if (index >= 0) {
            list.scrollToItem(NotflixPlayerActions.configurable.size + 2 + index)
            orderRequester.requestFocusAfterFrames(2)
        } else {
            list.scrollToItem(0); firstRequester.requestFocusAfterFrames(2)
        }
        pending = null
    }
    NotflixSidePanel(title = stringResource(R.string.notflix_player_buttons), width = 600.dp,
        onDismiss = { onSave(ordered) }) {
        Text(stringResource(R.string.notflix_player_buttons_help, NotflixPlayerActions.MAX_SELECTED),
            color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 14.sp)
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
            .testTag("notflix-player-buttons-editor"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "reset") {
                NotflixPanelChoiceRow(NotflixPanelChoice("reset", stringResource(R.string.subtitle_reset_defaults),
                    testTag = "notflix-player-buttons-reset"), Modifier.focusRequester(firstRequester),
                    onClick = { ordered = NotflixPlayerActions.defaults })
            }
            items(NotflixPlayerActions.configurable, key = { it.id }) { action ->
                val marked = action.id in ordered
                NotflixPanelChoiceRow(NotflixPanelChoice(action.id, labels.getValue(action), selected = marked,
                    enabled = marked || ordered.size < NotflixPlayerActions.MAX_SELECTED,
                    testTag = "notflix-player-buttons-select-${action.id}"),
                    onClick = { ordered = NotflixPlayerActions.toggle(ordered, action.id) })
            }
            item(key = "order") { NotflixPanelGroupTitle(stringResource(R.string.notflix_player_row_order)) }
            itemsIndexed(ordered, key = { _, id -> "order:$id" }) { index, id ->
                val action = NotflixPlayerActions.configurable.first { it.id == id }
                PriorityEntryRow(label = labels.getValue(action), canMoveUp = index > 0, canMoveDown = index < ordered.lastIndex,
                    onUp = { ordered = NotflixPlayerActions.move(ordered, id, -1); pending = id },
                    onDown = { ordered = NotflixPlayerActions.move(ordered, id, 1); pending = id },
                    onRemove = { pending = ordered.getOrNull(index + 1) ?: ordered.getOrNull(index - 1) ?: ""
                        ordered = ordered - id }, modifier = Modifier.testTag("notflix-player-buttons-order-$id"),
                    removeFocusRequester = if (pending == id) orderRequester else null)
            }
            item(key = "more") {
                Text(labels.getValue(NotflixPlayerAction.MORE), color = Spec.Secondary,
                    fontFamily = Spec.BodyFont, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp)
                        .testTag("notflix-player-buttons-fixed-more"))
            }
        }
    }
}
