package com.nuvio.tv.ui.screens.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.data.remote.supabase.AvatarCatalogItem
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.NotflixWordmark
import com.nuvio.tv.ui.components.AvatarPickerGrid
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.screens.ambient.AmbientDialogEffect

/*
 * M52: Notflix presentation of every profile menu. Only the drawing lives here; the profile state
 * machine, PIN, deletion, settings copy and sync stay in ProfileSelectionScreen/ViewModel.
 */

/** Fixed order of the long-press menu; «Eliminar» is always the last row and the primary has none. */
internal enum class NotflixProfileMenuAction {
    Edit,
    CopySettings,
    SetPin,
    ChangePin,
    RemovePin,
    Delete
}

internal fun notflixProfileMenuActions(
    isPrimary: Boolean,
    pinEnabled: Boolean,
    canCopySettings: Boolean
): List<NotflixProfileMenuAction> = buildList {
    add(NotflixProfileMenuAction.Edit)
    if (canCopySettings) add(NotflixProfileMenuAction.CopySettings)
    add(if (pinEnabled) NotflixProfileMenuAction.ChangePin else NotflixProfileMenuAction.SetPin)
    if (pinEnabled) add(NotflixProfileMenuAction.RemovePin)
    if (!isPrimary) add(NotflixProfileMenuAction.Delete)
}

internal enum class NotflixDeleteConfirmAction { Delete, Cancel }

internal fun notflixDeleteConfirmActions(): List<NotflixDeleteConfirmAction> =
    listOf(NotflixDeleteConfirmAction.Delete, NotflixDeleteConfirmAction.Cancel)

/** Commission: the delete confirmation opens with focus on Cancelar. */
internal fun notflixDeleteConfirmInitialFocusIndex(): Int =
    notflixDeleteConfirmActions().indexOf(NotflixDeleteConfirmAction.Cancel)

/** KIDS is the all-ages preset; clearing it removes the limit. */
internal fun notflixAgeLimitAfterKidsToggle(kids: Boolean): AgeLevel? =
    if (kids) NOTFLIX_KIDS_AGE_LEVEL else null

internal fun notflixIsKidsAgeLimit(level: AgeLevel?): Boolean = level == NOTFLIX_KIDS_AGE_LEVEL

private val NotflixPanelShape = RoundedCornerShape(2.dp)

private fun notflixIsSelectKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

/**
 * Centred Notflix panel over a 70 % black veil. 520 dp by default, Surface fill, 2 dp radius.
 * Back closes it through [onDismiss]; [suppressFirstSelectKeyUp] swallows the key-up that follows
 * the long press that opened the panel so it cannot activate the focused row.
 */
@Composable
internal fun NotflixProfilePanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 520.dp,
    suppressFirstSelectKeyUp: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var ready by remember { mutableStateOf(!suppressFirstSelectKeyUp) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AmbientDialogEffect(onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (notflixIsSelectKey(native.keyCode) || native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                        if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                            ready = true
                        }
                        if (!ready) return@onPreviewKeyEvent true
                    }
                    false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = modifier
                    .width(width)
                    .clip(NotflixPanelShape)
                    .background(Spec.Surface)
                    .border(1.dp, Spec.Divider, NotflixPanelShape)
                    .padding(horizontal = 28.dp, vertical = 24.dp)
                    .testTag("notflix-profile-panel"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                content()
            }
        }
    }
}

/**
 * Flat panel row: 56 dp, focus inverted (Text fill, Background text) with a 2 dp Text border and
 * the same 3 dp gap as [NotflixChoice]. [accent] paints the label Accent for destructive actions;
 * [trailing] draws the current-choice tick.
 */
@Composable
internal fun NotflixPanelRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    trailing: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, NotflixPanelShape)
            .padding(3.dp)
            .background(
                if (focused) Spec.Text else if (selected) Spec.Surface else Color.Transparent,
                NotflixPanelShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                color = when {
                    focused -> Spec.Background
                    accent -> Spec.Accent
                    enabled -> Spec.Text
                    else -> Spec.Secondary
                },
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = if (accent || selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    color = if (focused) Spec.Background else Spec.Accent,
                    fontFamily = Spec.BodyFont,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/** Long-press menu: avatar and name on top, then the flat actions; nothing scrolls. */
@Composable
internal fun NotflixProfileMenuDialog(
    profile: UserProfile,
    avatarUrl: String?,
    pinEnabled: Boolean,
    canCopySettings: Boolean,
    suppressFirstSelectKeyUp: Boolean,
    onDismiss: () -> Unit,
    onAction: (NotflixProfileMenuAction) -> Unit
) {
    val actions = remember(profile.isPrimary, pinEnabled, canCopySettings) {
        notflixProfileMenuActions(
            isPrimary = profile.isPrimary,
            pinEnabled = pinEnabled,
            canCopySettings = canCopySettings
        )
    }
    val requesters = remember(actions) { actions.associateWith { FocusRequester() } }
    LaunchedEffect(actions) {
        repeat(2) { withFrameNanos { } }
        runCatching { requesters[actions.first()]?.requestFocus() }
    }
    NotflixProfilePanel(
        title = stringResource(R.string.profile_selection_options_title),
        onDismiss = onDismiss,
        suppressFirstSelectKeyUp = suppressFirstSelectKeyUp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileAvatarCircle(
                name = profile.name,
                colorHex = profile.avatarColorHex,
                size = 64.dp,
                avatarImageUrl = avatarUrl
            )
            Text(
                text = profile.name,
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        actions.forEach { action ->
            NotflixPanelRow(
                label = action.label(),
                onClick = { onAction(action) },
                modifier = Modifier.focusRequester(requesters.getValue(action)),
                accent = action == NotflixProfileMenuAction.Delete
            )
        }
    }
}

@Composable
private fun NotflixProfileMenuAction.label(): String = when (this) {
    NotflixProfileMenuAction.Edit -> stringResource(R.string.profile_edit_label)
    NotflixProfileMenuAction.CopySettings -> stringResource(R.string.profile_copy_settings_label)
    NotflixProfileMenuAction.SetPin -> stringResource(R.string.profile_pin_set)
    NotflixProfileMenuAction.ChangePin -> stringResource(R.string.profile_pin_change)
    NotflixProfileMenuAction.RemovePin -> stringResource(R.string.profile_pin_remove)
    NotflixProfileMenuAction.Delete -> stringResource(R.string.profile_delete)
}

/** Delete confirmation: current copy, «Eliminar perfil» in Accent and focus starting on Cancelar. */
@Composable
internal fun NotflixDeleteProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val actions = remember { notflixDeleteConfirmActions() }
    val initialFocus = remember(actions) { actions[notflixDeleteConfirmInitialFocusIndex()] }
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { initialFocusRequester.requestFocus() }
    }
    NotflixProfilePanel(
        title = stringResource(R.string.profile_delete_confirm_title),
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.profile_delete_confirm_subtitle),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.forEach { action ->
                NotflixChoice(
                    selected = false,
                    onClick = when (action) {
                        NotflixDeleteConfirmAction.Delete -> onConfirm
                        NotflixDeleteConfirmAction.Cancel -> onDismiss
                    },
                    modifier = if (action == initialFocus) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    }
                ) {
                    Text(
                        text = stringResource(
                            when (action) {
                                NotflixDeleteConfirmAction.Delete -> R.string.profile_delete_btn
                                NotflixDeleteConfirmAction.Cancel -> R.string.profile_cancel
                            }
                        ),
                        color = if (action == NotflixDeleteConfirmAction.Delete) Spec.Accent else Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 15.sp,
                        fontWeight = if (action == NotflixDeleteConfirmAction.Delete) {
                            FontWeight.Bold
                        } else {
                            FontWeight.SemiBold
                        }
                    )
                }
            }
        }
    }
}

/**
 * Notflix «Editar perfil»: same composition as [NotflixCreateProfileOverlay] (preview on the left,
 * fields on the right): name, KIDS, age row, colours and the existing avatar grid with its
 * categories as compact Notflix choices. Save/Cancel behave exactly like «Añadir perfil».
 */
@Composable
internal fun NotflixEditProfileOverlay(
    profile: UserProfile,
    avatarCatalog: List<AvatarCatalogItem>,
    storedAgeLimit: AgeLevel?,
    isSaving: Boolean,
    currentAvatarUrl: String?,
    privateAvatars: List<NotflixAvatar> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (UserProfile, AgeLevel?) -> Unit
) {
    BackHandler(onBack = onDismiss)
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var colorHex by remember(profile.id, profile.avatarColorHex) { mutableStateOf(profile.avatarColorHex) }
    var avatarId by remember(profile.id, profile.avatarId) { mutableStateOf(profile.avatarId) }
    var avatarUrlChoice by remember(profile.id, profile.avatarUrl) { mutableStateOf(profile.avatarUrl) }
    var avatarCleared by remember(profile.id) { mutableStateOf(false) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    var ageLimit by remember(profile.id) { mutableStateOf(storedAgeLimit) }
    var focusedAvatarName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(storedAgeLimit) { ageLimit = storedAgeLimit }
    val kids = notflixIsKidsAgeLimit(ageLimit)
    val selectedAvatar = remember(avatarCatalog, avatarId) { avatarCatalog.find { it.id == avatarId } }
    val previewAvatarImageUrl = selectedAvatar?.imageUrl
        ?: avatarUrlChoice
        ?: currentAvatarUrl.takeIf { !avatarCleared && avatarId == profile.avatarId }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { nameFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .notflixFocusTrap()
            .testTag("notflix-edit-profile")
    ) {
        NotflixWordmark(22.dp, Modifier.padding(start = Spec.GUTTER.dp, top = 20.dp))
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(parseProfileColor(colorHex)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    previewAvatarImageUrl != null -> AsyncImage(
                        model = previewAvatarImageUrl,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    kids -> KidsLogo(40.sp, kColor = Color.White)
                    else -> Text(
                        text = name.trim().take(1).uppercase().ifEmpty { "?" },
                        color = Color.White,
                        fontFamily = Spec.BrandFont,
                        fontSize = 72.sp
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.profile_edit_header),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                ProfileNameField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    focusRequester = nameFocus,
                    notflixSkin = true
                )
                NotflixKidsToggle(
                    kids = kids,
                    onClick = { ageLimit = notflixAgeLimitAfterKidsToggle(!kids) },
                    modifier = Modifier.testTag("notflix-edit-kids")
                )
                Text(
                    text = stringResource(R.string.notflix_profile_age_label),
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 13.sp
                )
                NotflixAgeLimitRow(ageLimit = ageLimit, onAgeLimitChange = { ageLimit = it })
                Text(
                    text = stringResource(R.string.notflix_profile_color_label),
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 13.sp
                )
                NotflixProfileColorRow(colorHex = colorHex, onColorChange = { colorHex = it })
                Text(
                    text = stringResource(R.string.profile_choose_avatar),
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 13.sp
                )
                if (avatarCatalog.isNotEmpty()) {
                    AvatarPickerGrid(
                        avatars = avatarCatalog,
                        selectedAvatarId = avatarId,
                        onAvatarSelected = { avatar ->
                            avatarUrlChoice = null
                            avatarCleared = false
                            if (avatarId == avatar.id) {
                                avatarId = null
                                colorHex = profile.avatarColorHex
                            } else {
                                avatarId = avatar.id
                                avatar.bgColor?.let { colorHex = it }
                            }
                        },
                        onAvatarFocused = { avatar -> focusedAvatarName = avatar?.displayName },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        notflixSkin = true
                    )
                    Text(
                        text = focusedAvatarName ?: stringResource(R.string.profile_avatar_focus_hint),
                        color = if (focusedAvatarName != null) Spec.Text else Spec.Secondary,
                        fontFamily = Spec.BodyFont,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                NotflixChoosePhotoChoice(
                    onClick = { showPhotoPicker = true },
                    modifier = Modifier.testTag("notflix-edit-photo")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotflixChoice(
                        selected = true,
                        enabled = name.isNotBlank() && !isSaving,
                        onClick = {
                            onSave(
                                profile.copy(
                                    name = name,
                                    avatarColorHex = colorHex,
                                    avatarId = avatarId,
                                    avatarUrl = avatarUrlChoice
                                ),
                                ageLimit
                            )
                        },
                        modifier = Modifier.testTag("notflix-edit-save")
                    ) {
                        Text(
                            text = stringResource(
                                if (isSaving) R.string.profile_saving else R.string.notflix_profile_save
                            ),
                            color = Spec.Background,
                            fontFamily = Spec.BodyFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    NotflixChoice(selected = false, onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.notflix_profile_cancel),
                            color = Spec.Text,
                            fontFamily = Spec.BodyFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        if (showPhotoPicker) {
            NotflixAvatarPicker(
                privateAvatars = privateAvatars,
                selectedUrl = avatarUrlChoice,
                onDismiss = { showPhotoPicker = false },
                onSelect = { picked ->
                    avatarId = null
                    avatarUrlChoice = picked
                    avatarCleared = picked == null
                    showPhotoPicker = false
                }
            )
        }
    }
}

/** «Copiar ajustes» with Notflix presentation: same panel, source profiles as flat rows. */
@Composable
internal fun NotflixCopyProfileSettingsDialog(
    profiles: List<UserProfile>,
    targetProfile: UserProfile,
    avatarImageUrl: (UserProfile) -> String?,
    isCopying: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCopy: (sourceProfileId: Int, copyProviderCredentials: Boolean) -> Unit
) {
    val sourceProfiles = remember(profiles, targetProfile.id) {
        profiles.filterNot { it.id == targetProfile.id }
    }
    val defaultSourceId = remember(sourceProfiles, targetProfile.id) {
        sourceProfiles.firstOrNull { it.isPrimary }?.id ?: sourceProfiles.firstOrNull()?.id
    }
    var selectedSourceId by remember(sourceProfiles, targetProfile.id) { mutableStateOf(defaultSourceId) }
    var copyProviderCredentials by remember(targetProfile.id) { mutableStateOf(false) }
    val firstSourceFocus = remember(targetProfile.id) { FocusRequester() }
    LaunchedEffect(targetProfile.id) {
        repeat(2) { withFrameNanos { } }
        runCatching { firstSourceFocus.requestFocus() }
    }
    NotflixProfilePanel(
        title = stringResource(R.string.profile_copy_settings_title, targetProfile.name),
        onDismiss = { if (!isCopying) onDismiss() }
    ) {
        Text(
            text = stringResource(R.string.profile_copy_settings_subtitle),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            text = stringResource(R.string.profile_copy_settings_from),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        sourceProfiles.forEach { profile ->
            NotflixCopySourceRow(
                profile = profile,
                avatarUrl = avatarImageUrl(profile),
                selected = profile.id == selectedSourceId,
                enabled = !isCopying,
                modifier = if (profile.id == defaultSourceId) {
                    Modifier.focusRequester(firstSourceFocus)
                } else {
                    Modifier
                },
                onClick = { selectedSourceId = profile.id }
            )
        }
        Spacer(Modifier.height(6.dp))
        NotflixCheckboxRow(
            label = stringResource(
                if (copyProviderCredentials) {
                    R.string.profile_copy_settings_credentials_on
                } else {
                    R.string.profile_copy_settings_credentials_off
                }
            ),
            checked = copyProviderCredentials,
            enabled = !isCopying,
            onClick = { copyProviderCredentials = !copyProviderCredentials }
        )
        Text(
            text = stringResource(R.string.profile_copy_settings_details),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Spec.Accent,
                fontFamily = Spec.BodyFont,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NotflixChoice(selected = false, enabled = !isCopying, onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.profile_cancel),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            NotflixChoice(
                selected = true,
                enabled = selectedSourceId != null && !isCopying,
                onClick = {
                    selectedSourceId?.let { sourceProfileId ->
                        onCopy(sourceProfileId, copyProviderCredentials)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        if (isCopying) R.string.profile_copy_settings_copying
                        else R.string.profile_copy_settings_action
                    ),
                    color = Spec.Background,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NotflixCopySourceRow(
    profile: UserProfile,
    avatarUrl: String?,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, NotflixPanelShape)
            .padding(3.dp)
            .background(
                if (focused) Spec.Text else Color.Transparent,
                NotflixPanelShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileAvatarCircle(
            name = profile.name,
            colorHex = profile.avatarColorHex,
            size = 32.dp,
            avatarImageUrl = avatarUrl,
            imageCrossfade = false
        )
        Text(
            text = profile.copySourceLabel(),
            color = if (focused) Spec.Background else Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text(
                text = "✓",
                color = if (focused) Spec.Background else Spec.Accent,
                fontFamily = Spec.BodyFont,
                fontSize = 16.sp
            )
        }
    }
}

/** Flat 48 dp row with a square Notflix checkbox, used for the API-keys copy option. */
@Composable
private fun NotflixCheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, NotflixPanelShape)
            .padding(3.dp)
            .background(if (focused) Spec.Text else Color.Transparent, NotflixPanelShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (checked) Spec.Accent else Spec.Secondary, RoundedCornerShape(3.dp))
                .background(
                    if (checked) Spec.Accent else Color.Transparent,
                    RoundedCornerShape(3.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = label,
            color = if (focused) Spec.Background else Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Notflix PIN screen: Background, BrandFont heading, four 2 dp underlines that turn Accent as they
 * fill and a flat 64x64 numeric pad with inverted focus. Errors use Accent.
 */
@Composable
internal fun NotflixPinOverlayContent(
    heading: String,
    support: String,
    isError: Boolean,
    isWorking: Boolean,
    value: String,
    showForgotHint: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
) {
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { firstKeyFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .testTag("notflix-pin"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = heading,
                color = Spec.Text,
                fontFamily = Spec.BrandFont,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 900.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(2.dp)
                            .background(
                                if (index < value.length) Spec.Accent else Spec.Secondary.copy(alpha = 0.5f)
                            )
                    )
                }
            }
            Text(
                text = support,
                color = if (isError) Spec.Accent else Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp)
            )
            if (showForgotHint) {
                Text(
                    text = stringResource(R.string.profile_pin_overlay_forgot_hint),
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
            NotflixPinPad(
                enabled = !isWorking,
                canDelete = !isWorking && value.isNotEmpty(),
                firstKeyFocus = firstKeyFocus,
                onDigit = onDigit,
                onDelete = onDelete
            )
            Text(
                text = stringResource(R.string.profile_pin_overlay_back_hint),
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NotflixPinPad(
    enabled: Boolean,
    canDelete: Boolean,
    firstKeyFocus: FocusRequester,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    NotflixPinKey(
                        label = label,
                        enabled = enabled,
                        modifier = if (label == "1") Modifier.focusRequester(firstKeyFocus) else Modifier,
                        onClick = { onDigit(label[0]) }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotflixPinKey(label = "⌫", enabled = canDelete, onClick = onDelete)
            NotflixPinKey(label = "0", enabled = enabled, onClick = { onDigit('0') })
            Spacer(Modifier.size(64.dp))
        }
    }
}

@Composable
private fun NotflixPinKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(64.dp)
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, NotflixPanelShape)
            .padding(3.dp)
            .background(if (focused) Spec.Text else Spec.Surface, NotflixPanelShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                focused -> Spec.Background
                enabled -> Spec.Text
                else -> Spec.Secondary.copy(alpha = 0.4f)
            },
            fontFamily = Spec.BodyFont,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Shared Notflix «KIDS» preset row, used by «Añadir perfil» and «Editar perfil». */
@Composable
internal fun NotflixKidsToggle(
    kids: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NotflixChoice(selected = false, onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, if (kids) Spec.Accent else Spec.Secondary, RoundedCornerShape(3.dp))
                    .background(if (kids) Spec.Accent else Color.Transparent, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (kids) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            KidsLogo(24.sp)
            Column {
                Text(
                    text = stringResource(R.string.notflix_profile_kids_label),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.notflix_profile_kids_hint),
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** Shared age row: none, TP and the four age levels the profile can be limited to. */
@Composable
internal fun NotflixAgeLimitRow(
    ageLimit: AgeLevel?,
    onAgeLimitChange: (AgeLevel?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            null to stringResource(R.string.profile_age_limit_none),
            AgeLevel.TP to "TP",
            AgeLevel.A7 to "7",
            AgeLevel.A12 to "12",
            AgeLevel.A16 to "16",
            AgeLevel.A18 to "18"
        ).forEach { (level, label) ->
            NotflixChoice(selected = ageLimit == level, onClick = { onAgeLimitChange(level) }, compact = true) {
                Text(
                    text = label,
                    color = if (ageLimit == level) Spec.Background else Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Shared six-colour row of the Notflix profiles. */
@Composable
internal fun NotflixProfileColorRow(
    colorHex: String,
    onColorChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NotflixProfileColors.forEach { hex ->
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .size(34.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .border(
                        3.dp,
                        if (focused) Spec.Text else if (hex == colorHex) Spec.Secondary else Color.Transparent,
                        CircleShape
                    )
                    .padding(5.dp)
                    .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                    .clickable(role = Role.RadioButton) { onColorChange(hex) }
            )
        }
    }
}

/** Panel copy block; kept internal so ProfileSettingsContent can reuse the same typography. */
@Composable
internal fun NotflixPanelBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Spec.Secondary,
        fontFamily = Spec.BodyFont,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = modifier
    )
}

/** Scrollable list container inside a Notflix panel (hidden titles may not fit on one screen). */
@Composable
internal fun NotflixPanelScrollColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}
