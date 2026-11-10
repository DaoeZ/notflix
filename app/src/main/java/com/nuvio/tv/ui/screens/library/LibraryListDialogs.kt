package com.nuvio.tv.ui.screens.library

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibraryListPrivacy
import kotlinx.coroutines.delay
import com.nuvio.tv.domain.model.localizedTitle
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import com.nuvio.tv.ui.appearance.notflix.NotflixFlatButton
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TrackingListManagementCapabilities
import com.nuvio.tv.ui.screens.ambient.AmbientDialogEffect

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ManageListsDialog(
    tabs: List<LibraryListTab>,
    capabilities: TrackingListManagementCapabilities,
    selectedKey: String?,
    errorMessage: String?,
    pending: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    notflix: Boolean = false
) {
    val personalTabs = remember(tabs) { tabs.filter { it.type == LibraryListTab.Type.PERSONAL } }
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(personalTabs.size, pending) {
        if (pending) return@LaunchedEffect
        val target = firstFocusRequester
        val focused = runCatching { target.requestFocus() }.isSuccess
        if (!focused) {
            delay(16)
            runCatching { target.requestFocus() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        AmbientDialogEffect(onDismiss)
        Box(
            modifier = Modifier
                .width(620.dp)
                .background(
                    if (notflix) NotflixVisualSpec.Surface else NuvioTheme.colors.BackgroundElevated,
                    RoundedCornerShape(if (notflix) 4.dp else NuvioTheme.radii.xl)
                )
                .padding(NuvioTheme.spacing.xl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.library_manage_lists),
                    style = if (notflix) MaterialTheme.typography.titleLarge.copy(
                        fontFamily = NotflixVisualSpec.BrandFont,
                        fontSize = 30.sp
                    ) else MaterialTheme.typography.titleLarge,
                    color = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.TextPrimary
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notflix) NotflixVisualSpec.Accent else Color(0xFFFFB6B6)
                    )
                }

                if (personalTabs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_no_lists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notflix) NotflixVisualSpec.Secondary else NuvioTheme.extendedColors.textSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(personalTabs, key = { it.key }) { tab ->
                            val selected = tab.key == selectedKey
                            val focusModifier = if (tab.key == personalTabs.firstOrNull()?.key) {
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(firstFocusRequester)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                            if (notflix) {
                                NotflixFlatButton(
                                    label = tab.localizedTitle(),
                                    onClick = { onSelect(tab.key) },
                                    modifier = focusModifier,
                                    trailing = if (selected) "✓" else "",
                                    primary = selected,
                                    enabled = !pending
                                )
                            } else {
                                Button(
                                    onClick = { onSelect(tab.key) },
                                    enabled = !pending,
                                    modifier = focusModifier,
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (selected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                                        contentColor = NuvioTheme.colors.TextPrimary
                                    )
                                ) {
                                    Text(
                                        text = tab.localizedTitle(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryDialogButton(
                        notflix = notflix,
                        label = stringResource(R.string.library_list_create),
                        onClick = onCreate,
                        modifier = if (personalTabs.isEmpty()) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        enabled = !pending,
                        primary = true
                    )
                    LibraryDialogButton(
                        notflix = notflix,
                        label = stringResource(R.string.library_list_edit),
                        onClick = onEdit,
                        enabled = !pending && selectedKey != null
                    )
                    if (capabilities.supportsReordering) {
                        LibraryDialogButton(
                            notflix = notflix,
                            label = stringResource(R.string.library_list_move_up),
                            onClick = onMoveUp,
                            enabled = !pending && selectedKey != null
                        )
                        LibraryDialogButton(
                            notflix = notflix,
                            label = stringResource(R.string.library_list_move_down),
                            onClick = onMoveDown,
                            enabled = !pending && selectedKey != null
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryDialogButton(
                        notflix = notflix,
                        label = stringResource(R.string.library_list_delete),
                        onClick = onDelete,
                        enabled = !pending && selectedKey != null,
                        danger = true
                    )
                    LibraryDialogButton(
                        notflix = notflix,
                        label = stringResource(R.string.library_list_close),
                        onClick = onDismiss,
                        enabled = !pending
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ListEditorDialog(
    state: LibraryListEditorState,
    capabilities: TrackingListManagementCapabilities,
    pending: Boolean,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onPrivacyChanged: (LibraryListPrivacy) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    notflix: Boolean = false
) {
    val nameFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var nameEditing by remember { mutableStateOf(false) }
    var descriptionEditing by remember { mutableStateOf(false) }

    fun isSelectKey(keyCode: Int): Boolean {
        return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onCancel,
        title = if (state.mode == LibraryListEditorState.Mode.CREATE) stringResource(R.string.library_list_create_dialog_title) else stringResource(R.string.library_list_edit_dialog_title),
        width = 560.dp,
        containerBrush = if (notflix) androidx.compose.ui.graphics.SolidColor(NotflixVisualSpec.Surface) else null,
        containerBorderWidth = if (notflix) 1.dp else NuvioTheme.spacing.hairline,
        containerCornerRadius = if (notflix) 4.dp else NuvioTheme.radii.xl
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocusRequester)
                .onFocusChanged {
                    if (!it.isFocused) {
                        nameEditing = false
                    }
                }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action == AndroidKeyEvent.ACTION_DOWN && isSelectKey(native.keyCode)) {
                        nameEditing = true
                        descriptionEditing = false
                        keyboardController?.show()
                    }
                    false
                },
            enabled = !pending,
            readOnly = !nameEditing,
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    nameEditing = false
                    keyboardController?.hide()
                }
            ),
            label = { androidx.compose.material3.Text(stringResource(R.string.library_list_name_label)) },
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                textDirection = TextDirection.Content
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.TextPrimary,
                unfocusedTextColor = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.TextPrimary,
                focusedContainerColor = if (notflix) NotflixVisualSpec.Surface else NuvioTheme.colors.BackgroundCard,
                unfocusedContainerColor = if (notflix) NotflixVisualSpec.Surface else NuvioTheme.colors.BackgroundCard,
                focusedBorderColor = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.FocusRing,
                unfocusedBorderColor = if (notflix) NotflixVisualSpec.Secondary.copy(alpha = .4f) else NuvioTheme.colors.Border,
                focusedLabelColor = if (notflix) NotflixVisualSpec.Secondary else NuvioTheme.colors.TextSecondary,
                unfocusedLabelColor = if (notflix) NotflixVisualSpec.Secondary.copy(alpha = .7f) else NuvioTheme.colors.TextTertiary,
                cursorColor = if (notflix) NotflixVisualSpec.Accent else NuvioTheme.colors.FocusRing,
            )
        )

        if (capabilities.supportsDescription) {
            androidx.compose.material3.OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(descriptionFocusRequester)
                    .onFocusChanged {
                        if (!it.isFocused) {
                            descriptionEditing = false
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action == AndroidKeyEvent.ACTION_DOWN && isSelectKey(native.keyCode)) {
                            descriptionEditing = true
                            nameEditing = false
                            keyboardController?.show()
                        }
                        false
                    },
                enabled = !pending,
                readOnly = !descriptionEditing,
                minLines = 3,
                maxLines = 5,
                label = { androidx.compose.material3.Text(stringResource(R.string.library_list_description_label)) },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    textDirection = TextDirection.Content
                ),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.TextPrimary,
                    unfocusedTextColor = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = if (notflix) NotflixVisualSpec.Surface else NuvioTheme.colors.BackgroundCard,
                    unfocusedContainerColor = if (notflix) NotflixVisualSpec.Surface else NuvioTheme.colors.BackgroundCard,
                    focusedBorderColor = if (notflix) NotflixVisualSpec.Text else NuvioTheme.colors.FocusRing,
                    unfocusedBorderColor = if (notflix) NotflixVisualSpec.Secondary.copy(alpha = .4f) else NuvioTheme.colors.Border,
                    focusedLabelColor = if (notflix) NotflixVisualSpec.Secondary else NuvioTheme.colors.TextSecondary,
                    unfocusedLabelColor = if (notflix) NotflixVisualSpec.Secondary.copy(alpha = .7f) else NuvioTheme.colors.TextTertiary,
                    cursorColor = if (notflix) NotflixVisualSpec.Accent else NuvioTheme.colors.FocusRing,
                )
            )
        }

        Text(
            text = stringResource(R.string.library_list_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = if (notflix) NotflixVisualSpec.Secondary else NuvioTheme.extendedColors.textSecondary
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(capabilities.privacyOptions, key = { it.name }) { privacy ->
                val selected = privacy == state.privacy
                if (notflix) {
                    NotflixFlatButton(
                        label = privacy.apiValue.replaceFirstChar { it.uppercase() },
                        onClick = { onPrivacyChanged(privacy) },
                        trailing = if (selected) "✓" else "",
                        primary = selected,
                        enabled = !pending
                    )
                } else {
                    Button(
                        onClick = { onPrivacyChanged(privacy) },
                        enabled = !pending,
                        colors = ButtonDefaults.colors(
                            containerColor = if (selected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                            contentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text(privacy.apiValue.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        LibraryDialogButton(
            notflix = notflix,
            label = if (pending) stringResource(R.string.action_saving) else stringResource(R.string.action_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !pending,
            primary = true
        )
    }
}

/** One dialog action: Nuvio keeps the tv Button, Notflix uses the flat white action. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryDialogButton(
    notflix: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false
) {
    if (notflix) {
        NotflixFlatButton(
            label = label,
            onClick = onClick,
            modifier = modifier,
            trailing = "",
            primary = primary && enabled,
            enabled = enabled
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.colors(
                containerColor = if (danger) Color(0xFF4A2323) else NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(label)
        }
    }
}
