package com.nuvio.tv.ui.screens.addon

import com.nuvio.tv.ui.theme.NuvioPrimitives
import com.nuvio.tv.ui.theme.NuvioTheme

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.util.contentTextDirection
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import com.nuvio.tv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

private sealed interface AddonExperienceModeState {
    data object Loading : AddonExperienceModeState
    data class Loaded(val mode: ExperienceMode?) : AddonExperienceModeState
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AddonManagerScreen(
    viewModel: AddonManagerViewModel = hiltViewModel(),
    showBuiltInHeader: Boolean = true,
    onBackPress: () -> Unit = {},
    onNavigateToCatalogOrder: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    catalogOrderReturnToken: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()
    val experienceModeState by remember(viewModel) {
        viewModel.experienceMode.map<ExperienceMode?, AddonExperienceModeState> {
            AddonExperienceModeState.Loaded(it)
        }
    }.collectAsState(initial = AddonExperienceModeState.Loading)
    val experienceMode = (experienceModeState as? AddonExperienceModeState.Loaded)?.mode
    if (experienceModeState is AddonExperienceModeState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuvioTheme.colors.Background),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
        return
    }
    val webConfigMode = viewModel.webConfigMode(experienceMode ?: ExperienceMode.ADVANCED)
    AddonManagerContent(
        uiState = uiState,
        experienceMode = experienceMode,
        isReadOnly = viewModel.isReadOnly,
        webConfigMode = webConfigMode,
        showBuiltInHeader = showBuiltInHeader,
        catalogOrderReturnToken = catalogOrderReturnToken,
        onBackPress = onBackPress,
        onNavigateToCatalogOrder = onNavigateToCatalogOrder,
        onNavigateToCollections = onNavigateToCollections,
        actions = AddonManagerActions(
            onInstallUrlChange = viewModel::onInstallUrlChange,
            installAddon = viewModel::installAddon,
            clearTransientMessage = viewModel::clearTransientMessage,
            stopQrMode = viewModel::stopQrMode,
            startQrMode = { viewModel.startQrMode(webConfigMode) },
            requestAddonSyncNow = viewModel::requestAddonSyncNow,
            moveAddonUp = viewModel::moveAddonUp,
            moveAddonDown = viewModel::moveAddonDown,
            setAddonEnabled = viewModel::setAddonEnabled,
            removeAddon = viewModel::removeAddon,
            confirmPendingChange = viewModel::confirmPendingChange,
            rejectPendingChange = viewModel::rejectPendingChange
        )
    )
}

internal data class AddonManagerActions(
    val onInstallUrlChange: (String) -> Unit = {},
    val installAddon: () -> Unit = {},
    val clearTransientMessage: () -> Unit = {},
    val stopQrMode: () -> Unit = {},
    val startQrMode: () -> Unit = {},
    val requestAddonSyncNow: () -> Unit = {},
    val moveAddonUp: (String) -> Unit = {},
    val moveAddonDown: (String) -> Unit = {},
    val setAddonEnabled: (String, Boolean) -> Unit = { _, _ -> },
    val removeAddon: (String) -> Unit = {},
    val confirmPendingChange: () -> Unit = {},
    val rejectPendingChange: () -> Unit = {}
)

/** The production screen content; callers own data and side effects. */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AddonManagerContent(
    uiState: AddonManagerUiState,
    experienceMode: ExperienceMode?,
    isReadOnly: Boolean,
    webConfigMode: com.nuvio.tv.core.server.AddonWebConfigMode,
    actions: AddonManagerActions,
    showBuiltInHeader: Boolean = true,
    catalogOrderReturnToken: Int = 0,
    onBackPress: () -> Unit = {},
    onNavigateToCatalogOrder: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {}
) {
    val isEssential = experienceMode == ExperienceMode.ESSENTIAL
    val firstAddonToggleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val listState = rememberLazyListState()
    val surfaceFocusRequester = remember { FocusRequester() }
    val catalogOrderFocusRequester = remember { FocusRequester() }
    val manageFromPhoneFocusRequester = remember { FocusRequester() }
    var handledCatalogOrderReturnToken by rememberSaveable { mutableIntStateOf(0) }
    val installButtonFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val deleteDialogFocusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }
    var addonUrlPendingDeletion by remember { mutableStateOf<String?>(null) }
    val hasHomeVisibleCatalogs = remember(uiState.installedAddons) {
        uiState.installedAddons.any { addon ->
            addon.enabled && addon.catalogs.any { catalog -> !catalog.isSearchOnlyCatalog() }
        }
    }
    val canOrderCatalogs = !isReadOnly && !isEssential && hasHomeVisibleCatalogs
    val manageFromPhoneSubtitle = if (webConfigMode == com.nuvio.tv.core.server.AddonWebConfigMode.COLLECTIONS_ONLY) {
        stringResource(R.string.addon_manage_collections_from_phone_subtitle)
    } else {
        stringResource(R.string.addon_manage_from_phone_subtitle)
    }
    val qrInstruction = if (webConfigMode == com.nuvio.tv.core.server.AddonWebConfigMode.COLLECTIONS_ONLY) {
        stringResource(R.string.addon_qr_collections_scan_instruction)
    } else {
        stringResource(R.string.addon_qr_scan_instruction)
    }

    BackHandler { onBackPress() }

    val defaultRefreshAddonsSubtitle = stringResource(R.string.addon_refresh_default_subtitle)
    val refreshedAddonsSubtitle = stringResource(R.string.addon_refresh_done_subtitle)
    var refreshAddonsSubtitle by remember(defaultRefreshAddonsSubtitle) {
        mutableStateOf(defaultRefreshAddonsSubtitle)
    }

    LaunchedEffect(refreshAddonsSubtitle) {
        if (refreshAddonsSubtitle != defaultRefreshAddonsSubtitle) {
            delay(5_000)
            refreshAddonsSubtitle = defaultRefreshAddonsSubtitle
        }
    }

    // When isEditing changes to true, focus the text field and show keyboard
    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Initial entry and ordinary resume keep the official URL focus. A return from
    // CatalogOrder is handled separately, once its content is ready and resumed.
    LaunchedEffect(lifecycleState, uiState.isQrModeActive, uiState.pendingChange, addonUrlPendingDeletion, catalogOrderReturnToken) {
        if (lifecycleState == Lifecycle.State.RESUMED &&
            !uiState.isQrModeActive && uiState.pendingChange == null && !isEditing &&
            addonUrlPendingDeletion == null && catalogOrderReturnToken == handledCatalogOrderReturnToken
        ) {
            repeat(2) { withFrameNanos { } }
            runCatching { surfaceFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(lifecycleState, uiState.isQrModeActive, uiState.pendingChange, addonUrlPendingDeletion, catalogOrderReturnToken, uiState.isLoading, canOrderCatalogs, isReadOnly) {
        if (catalogOrderReturnToken != handledCatalogOrderReturnToken &&
            lifecycleState == Lifecycle.State.RESUMED && !uiState.isLoading &&
            !uiState.isQrModeActive && uiState.pendingChange == null && !isEditing && addonUrlPendingDeletion == null
        ) {
            // Header + install/read-only notice + phone entry precede the catalog entry.
            val targetIndex = if (canOrderCatalogs) 3 else if (isReadOnly) 2 else 1
            listState.scrollToItem(targetIndex)
            repeat(2) { withFrameNanos { } }
            val target = if (canOrderCatalogs) catalogOrderFocusRequester
                else if (isReadOnly) manageFromPhoneFocusRequester else surfaceFocusRequester
            if (target.requestFocus()) handledCatalogOrderReturnToken = catalogOrderReturnToken
        }
    }

    LaunchedEffect(addonUrlPendingDeletion) {
        if (addonUrlPendingDeletion != null) {
            repeat(2) { withFrameNanos { } }
            runCatching { deleteDialogFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(uiState.transientMessage) {
        if (uiState.transientMessage != null) {
            delay(3200)
            actions.clearTransientMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose { actions.stopQrMode() }
    }

    val addonListScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.addon_title),
                    style = if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
                        MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = com.nuvio.tv.ui.appearance.NotflixVisualSpec.BrandFont,
                            fontSize = 48.sp,
                            lineHeight = 52.sp
                        )
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = if (showBuiltInHeader) {
                        if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
                            com.nuvio.tv.ui.appearance.NotflixVisualSpec.Text
                        } else {
                            NuvioTheme.colors.TextPrimary
                        }
                    } else {
                        Color.Transparent
                    }
                )
            }

            if (isReadOnly) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3A5C)),
                        shape = RoundedCornerShape(NuvioTheme.radii.md)
                    ) {
                        Text(
                            text = stringResource(R.string.addon_readonly_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextSecondary,
                            modifier = androidx.compose.ui.Modifier.padding(NuvioTheme.spacing.lg)
                        )
                    }
                }
            }

            if (!isReadOnly) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
                                Color.Transparent
                            } else {
                                NuvioTheme.colors.BackgroundCard
                            }
                        ),
                        shape = RoundedCornerShape(
                            if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
                                2.dp
                            } else {
                                NuvioTheme.radii.md
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.addon_install_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                                color = NuvioTheme.colors.TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Surface always stays in the tree for stable D-pad focus
                                Surface(
                                    onClick = { isEditing = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(surfaceFocusRequester),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = NuvioTheme.colors.BackgroundElevated,
                                        focusedContainerColor = NuvioTheme.colors.BackgroundElevated
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(
                                            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                                        ),
                                        focusedBorder = Border(
                                            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                                        )
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                ) {
                                    Box(modifier = Modifier.padding(NuvioTheme.spacing.md)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        BasicTextField(
                                            value = uiState.installUrl,
                                            onValueChange = actions.onInstallUrlChange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(textFieldFocusRequester)
                                                .onFocusChanged {
                                                    if (!it.isFocused && isEditing) {
                                                        isEditing = false
                                                        keyboardController?.hide()
                                                    }
                                                },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    actions.installAddon()
                                                    isEditing = false
                                                    keyboardController?.hide()
                                                    installButtonFocusRequester.requestFocus()
                                                }
                                            ),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = NuvioTheme.colors.TextPrimary,
                                            textDirection = TextDirection.Content
                                            ),
                                            cursorBrush = SolidColor(if (isEditing) NuvioTheme.colors.Primary else Color.Transparent),
                                            decorationBox = { innerTextField ->
                                                if (uiState.installUrl.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.addon_install_placeholder),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = NuvioTheme.colors.TextTertiary
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        actions.installAddon()
                                        isEditing = false
                                        keyboardController?.hide()
                                        installButtonFocusRequester.requestFocus()
                                    },
                                    enabled = !uiState.isInstalling,
                                    modifier = Modifier
                                        .focusRequester(installButtonFocusRequester)
                                        .semantics { role = Role.Button },
                                    colors = ButtonDefaults.colors(
                                        containerColor = NuvioTheme.colors.BackgroundCard,
                                        contentColor = NuvioTheme.colors.TextPrimary,
                                        focusedContainerColor = NuvioTheme.colors.FocusBackground,
                                        focusedContentColor = NuvioTheme.colors.Primary
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                                ) {
                                    Text(text = if (uiState.isInstalling) stringResource(R.string.addon_installing) else stringResource(R.string.addon_install_btn))
                                }
                            }

                            AnimatedVisibility(visible = uiState.error != null) {
                                Text(
                                    text = uiState.error.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NuvioTheme.colors.Error,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }

            }

            item {
                ManageFromPhoneCard(
                    subtitle = manageFromPhoneSubtitle,
                    onClick = { actions.startQrMode() },
                    modifier = Modifier.focusRequester(manageFromPhoneFocusRequester)
                )
            }

            if (canOrderCatalogs) {
                item {
                    CatalogOrderEntryCard(
                        onClick = onNavigateToCatalogOrder,
                        modifier = Modifier.focusRequester(catalogOrderFocusRequester)
                    )
                }
            }

            if (!isEssential) {
                item {
                    CollectionsEntryCard(onClick = onNavigateToCollections)
                }
            }

            item {
                RefreshAddonsEntryCard(
                    subtitle = refreshAddonsSubtitle,
                    onClick = {
                        actions.requestAddonSyncNow()
                        refreshAddonsSubtitle = refreshedAddonsSubtitle
                    },
                    modifier = Modifier.focusProperties {
                        down = firstAddonToggleFocusRequester
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.addon_installed_section),
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
                    if (uiState.isLoading && uiState.installedAddons.isEmpty()) {
                        LoadingIndicator(modifier = Modifier.height(NuvioTheme.spacing.xl))
                    }
                }
            }

            if (uiState.installedAddons.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.addon_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.installedAddons,
                    key = { _, addon -> addon.baseUrl }
                ) { index, addon ->
                    val bringIntoViewRequester = remember { BringIntoViewRequester() }
                    AddonCard(
                        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
                        addon = addon,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.installedAddons.lastIndex,
                        onMoveUp = {
                            actions.moveAddonUp(addon.baseUrl)
                            addonListScope.launch {
                                kotlinx.coroutines.delay(50)
                                bringIntoViewRequester.bringIntoView()
                            }
                        },
                        onMoveDown = {
                            actions.moveAddonDown(addon.baseUrl)
                            addonListScope.launch {
                                kotlinx.coroutines.delay(50)
                                bringIntoViewRequester.bringIntoView()
                            }
                        },
                        onRemove = { addonUrlPendingDeletion = addon.baseUrl },
                        onEnabledChange = { enabled -> actions.setAddonEnabled(addon.baseUrl, enabled) },
                        isReadOnly = isReadOnly,
                        showReorder = !isEssential,
                        toggleFocusRequester = if (index == 0) firstAddonToggleFocusRequester else null
                    )
                }
            }
        }

        // QR Code overlay — Popup renders above the entire screen
        if (uiState.isQrModeActive) {
            Popup(properties = PopupProperties(focusable = true)) {
                QrCodeOverlay(
                    qrBitmap = uiState.qrCodeBitmap,
                    serverUrl = uiState.serverUrl,
                    instruction = qrInstruction,
                    onClose = actions.stopQrMode,
                    hasPendingChange = uiState.pendingChange != null
                )
            }
        }

        // Confirmation dialog overlay
        if (uiState.pendingChange != null) {
            Popup(
                onDismissRequest = actions.rejectPendingChange,
                properties = PopupProperties(focusable = true, dismissOnClickOutside = false)
            ) {
                uiState.pendingChange?.let { pending ->
                    ConfirmAddonChangesDialog(
                        pendingChange = pending,
                        onConfirm = actions.confirmPendingChange,
                        onReject = actions.rejectPendingChange
                    )
                }
            }
        }

        addonUrlPendingDeletion?.let { addonUrl ->
            NuvioDialog(
                onDismiss = { addonUrlPendingDeletion = null },
                title = stringResource(R.string.addon_delete_confirm_title),
                subtitle = stringResource(R.string.addon_delete_confirm_message),
                suppressFirstKeyUp = false
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md, Alignment.End)
                ) {
                    Button(
                        onClick = { addonUrlPendingDeletion = null },
                        modifier = Modifier
                            .focusRequester(deleteDialogFocusRequester)
                            .semantics { role = Role.Button }
                    ) {
                        Text(stringResource(R.string.addon_delete_no))
                    }
                    Button(
                        onClick = {
                            actions.removeAddon(addonUrl)
                            addonUrlPendingDeletion = null
                        },
                        modifier = Modifier.semantics { role = Role.Button }
                    ) {
                        Text(stringResource(R.string.addon_delete_yes))
                    }
                }
            }
        }

        AddonMessageOverlay(
            message = uiState.transientMessage,
            isError = uiState.transientMessageIsError
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AddonMessageOverlay(
    message: String?,
    isError: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(NuvioTheme.spacing.xl),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val visibleMessage = message ?: return@AnimatedVisibility
            Surface(
                onClick = { },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isError) {
                        NuvioPrimitives.red600.copy(alpha = 0.92f)
                    } else {
                        NuvioPrimitives.green700.copy(alpha = 0.92f)
                    }
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = NuvioTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = visibleMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ManageFromPhoneCard(
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
        NotflixAddonEntryRow(
            title = stringResource(R.string.addon_manage_from_phone_title),
            subtitle = subtitle,
            icon = Icons.Default.QrCode2,
            onClick = onClick,
            modifier = modifier,
            trailingIcon = Icons.Default.PhoneAndroid
        )
        return
    }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { role = Role.Button },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isFocused) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.lg))
                Column {
                    Text(
                        text = stringResource(R.string.addon_manage_from_phone_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogOrderEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
        NotflixAddonEntryRow(
            title = stringResource(R.string.addon_reorder_title),
            subtitle = stringResource(R.string.addon_reorder_subtitle),
            icon = Icons.Default.Reorder,
            onClick = onClick,
            modifier = modifier
        )
        return
    }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { role = Role.Button },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Reorder,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isFocused) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.lg))
                Column {
                    Text(
                        text = stringResource(R.string.addon_reorder_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.addon_reorder_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CollectionsEntryCard(onClick: () -> Unit) {
    if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
        NotflixAddonEntryRow(
            title = stringResource(R.string.collections_card_title),
            subtitle = stringResource(R.string.collections_card_subtitle),
            icon = Icons.Default.FolderOpen,
            onClick = onClick
        )
        return
    }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { role = Role.Button },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isFocused) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.lg))
                Column {
                    Text(
                        text = stringResource(R.string.collections_card_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.collections_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RefreshAddonsEntryCard(
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
        NotflixAddonEntryRow(
            title = stringResource(R.string.addon_refresh_action),
            subtitle = subtitle,
            icon = Icons.Default.Refresh,
            onClick = onClick,
            modifier = modifier,
            trailingIcon = Icons.Default.Refresh
        )
        return
    }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { role = Role.Button },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isFocused) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.lg))
                Column {
                    Text(
                        text = stringResource(R.string.addon_refresh_action),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun QrCodeOverlay(
    qrBitmap: Bitmap?,
    serverUrl: String?,
    instruction: String,
    onClose: () -> Unit,
    hasPendingChange: Boolean = false,
    qrSize: Dp = 220.dp
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(hasPendingChange) {
        if (!hasPendingChange) {
            focusRequester.requestFocus()
        }
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))

            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_qr_code),
                    modifier = Modifier.size(qrSize),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

            if (serverUrl != null) {
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))

            Surface(
                onClick = onClose,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .semantics { role = Role.Button },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = NuvioTheme.colors.Surface,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                        shape = RoundedCornerShape(50)
                    )
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xl, vertical = NuvioTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = NuvioTheme.colors.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                    Text(
                        text = stringResource(R.string.addon_qr_close),
                        color = NuvioTheme.colors.TextPrimary,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ConfirmAddonChangesDialog(
    pendingChange: PendingChangeInfo,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler { onReject() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = { },
            modifier = Modifier
                .width(560.dp)
                .heightIn(max = 640.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioTheme.colors.SurfaceVariant
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.xl))
        ) {
            Column(
                modifier = Modifier.padding(NuvioTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.addon_confirm_title),
                    style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
                    color = NuvioTheme.colors.TextPrimary,
                )

                Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))

                Text(
                    text = stringResource(R.string.addon_confirm_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary
                )

                Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(
                            color = NuvioTheme.colors.Surface,
                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NuvioTheme.spacing.md)
                            .verticalScroll(scrollState)
                    ) {
                        if (pendingChange.addedUrls.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.addon_confirm_added),
                                style = MaterialTheme.typography.titleSmall,
                                color = NuvioTheme.colors.Success,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = NuvioTheme.spacing.xs)
                            )
                            pendingChange.addedUrls.forEach { url ->
                                val displayName = pendingChange.addedNames[url] ?: url
                                Text(
                                    text = "+ $displayName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NuvioTheme.colors.Success,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.xxs)
                                )
                            }
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                        }

                        if (pendingChange.removedUrls.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.addon_confirm_removed),
                                style = MaterialTheme.typography.titleSmall,
                                color = NuvioTheme.colors.Error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = NuvioTheme.spacing.xs)
                            )
                            pendingChange.removedUrls.forEach { url ->
                                val displayName = pendingChange.removedNames[url] ?: url
                                Text(
                                    text = "- $displayName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NuvioTheme.colors.Error,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.xxs)
                                )
                            }
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                        }

                        if (pendingChange.catalogsReordered) {
                            Text(
                                text = stringResource(R.string.addon_confirm_catalog_reordered),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            )
                        }

                        if (pendingChange.disabledCatalogNames.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.addon_confirm_catalogs_disabled),
                                style = MaterialTheme.typography.titleSmall,
                                color = NuvioTheme.colors.Error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = NuvioTheme.spacing.xs)
                            )
                            pendingChange.disabledCatalogNames.forEach { name ->
                                Text(
                                    text = "- $name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NuvioTheme.colors.Error,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.xxs)
                                )
                            }
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                        }

                        if (pendingChange.enabledCatalogNames.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.addon_confirm_catalogs_enabled),
                                style = MaterialTheme.typography.titleSmall,
                                color = NuvioTheme.colors.Success,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = NuvioTheme.spacing.xs)
                            )
                            pendingChange.enabledCatalogNames.forEach { name ->
                                Text(
                                    text = "+ $name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NuvioTheme.colors.Success,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.xxs)
                                )
                            }
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                        }

                        if (pendingChange.collectionsChanged) {
                            Text(
                                text = stringResource(R.string.addon_pending_collections_updated),
                                style = MaterialTheme.typography.titleSmall,
                                color = NuvioTheme.colors.TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = NuvioTheme.spacing.xs)
                            )
                            Text(
                                text = stringResource(R.string.addon_pending_collections_replace, pendingChange.proposedCollectionCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = NuvioTheme.colors.TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.xxs)
                            )
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                        }

                        if (
                            pendingChange.addedUrls.isEmpty() &&
                            pendingChange.removedUrls.isEmpty() &&
                            !pendingChange.catalogsReordered &&
                            pendingChange.disabledCatalogNames.isEmpty() &&
                            pendingChange.enabledCatalogNames.isEmpty() &&
                            !pendingChange.collectionsChanged
                        ) {
                            Text(
                                text = stringResource(R.string.addon_confirm_no_changes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextSecondary
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.addon_confirm_total_addons, pendingChange.proposedUrls.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.addon_confirm_total_catalogs, pendingChange.proposedCatalogOrderKeys.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))

                if (pendingChange.isApplying) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
                    ) {
                        Surface(
                            onClick = onReject,
                            modifier = Modifier.semantics { role = Role.Button },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = NuvioTheme.colors.Surface,
                                focusedContainerColor = NuvioTheme.colors.FocusBackground
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                    shape = RoundedCornerShape(50)
                                )
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xl, vertical = NuvioTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = NuvioTheme.colors.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                                Text(
                                    text = stringResource(R.string.addon_confirm_reject),
                                    color = NuvioTheme.colors.TextPrimary,
                                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                                )
                            }
                        }

                        Surface(
                            onClick = onConfirm,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .semantics { role = Role.Button },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = NuvioTheme.colors.Secondary,
                                focusedContainerColor = NuvioTheme.colors.SecondaryVariant
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                    shape = RoundedCornerShape(50)
                                )
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))
                        ) {
                            Text(
                                text = stringResource(R.string.addon_confirm_confirm),
                                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xl, vertical = NuvioTheme.spacing.md),
                                color = NuvioTheme.colors.OnSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AddonCard(
    modifier: Modifier = Modifier,
    addon: Addon,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    isReadOnly: Boolean = false,
    showReorder: Boolean = true,
    toggleFocusRequester: FocusRequester? = null
) {
    // M50: with Notflix the add-on is a flat row separated by a 1 dp line, not a rounded card.
    val notflix = com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active
    val rowShape = if (notflix) RoundedCornerShape(2.dp) else RoundedCornerShape(NuvioTheme.radii.md)
    val container = if (notflix) Color.Transparent else NuvioTheme.colors.BackgroundCard
    if (isReadOnly) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { },
                modifier = modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = container,
                    focusedContainerColor = container
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = if (notflix) {
                        Border.None
                    } else {
                        Border(
                            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                            shape = rowShape
                        )
                    }
                ),
                shape = ClickableSurfaceDefaults.shape(rowShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                AddonCardContent(addon = addon, isReadOnly = true)
            }
            if (notflix) NotflixAddonDivider()
        }
    } else {
        val internalToggleFocusRequester = remember { FocusRequester() }
        val effectiveToggleFocusRequester = toggleFocusRequester ?: internalToggleFocusRequester

        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .focusProperties {
                        enter = { effectiveToggleFocusRequester }
                    },
                colors = CardDefaults.cardColors(containerColor = container),
                shape = rowShape
            ) {
                AddonCardContent(
                    addon = addon,
                    isReadOnly = false,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onRemove = onRemove,
                    onEnabledChange = onEnabledChange,
                    showReorder = showReorder,
                    toggleFocusRequester = effectiveToggleFocusRequester
                )
            }
            if (notflix) NotflixAddonDivider()
        }
    }
}

@Composable
private fun NotflixAddonDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(com.nuvio.tv.ui.appearance.NotflixVisualSpec.Divider)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonCardContent(
    addon: Addon,
    isReadOnly: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemove: () -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
    showReorder: Boolean = true,
    toggleFocusRequester: FocusRequester? = null
) {
    val notflix = com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active
    val spec = com.nuvio.tv.ui.appearance.NotflixVisualSpec
    val primaryText = if (notflix) spec.Text else NuvioTheme.colors.TextPrimary
    val secondaryText = if (notflix) spec.Secondary else NuvioTheme.colors.TextSecondary
    val tertiaryText = if (notflix) spec.Secondary else NuvioTheme.colors.TextTertiary
    Column(
        modifier = Modifier.padding(
            horizontal = if (notflix) 16.dp else 20.dp,
            vertical = if (notflix) 12.dp else 20.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (notflix && !addon.logo.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = addon.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = addon.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textDirection = TextDirection.Content
                    ),
                    color = primaryText,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (addon.version.isNotBlank()) {
                        Text(
                            text = "v${addon.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText
                        )
                    }
                    if (!addon.enabled) {
                        Text(
                            text = stringResource(R.string.addons_badge_disabled),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText
                        )
                    }
                }
            }
            if (!isReadOnly) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onEnabledChange(!addon.enabled) },
                        modifier = Modifier
                            .focusRequester(toggleFocusRequester ?: remember { FocusRequester() })
                            .semantics {
                                role = Role.Switch
                                toggleableState = if (addon.enabled) ToggleableState.On else ToggleableState.Off
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = if (notflix) {
                                spec.Text
                            } else {
                                NuvioTheme.colors.FocusBackground
                            }
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border.None,
                            focusedBorder = if (notflix) {
                                Border.None
                            } else {
                                Border(
                                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                                )
                            }
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Switch(
                                checked = addon.enabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = if (notflix) Color.White else NuvioTheme.colors.Secondary,
                                    checkedTrackColor = if (notflix) {
                                        spec.Accent
                                    } else {
                                        NuvioTheme.colors.Secondary.copy(alpha = 0.3f)
                                    }
                                )
                            )
                        }
                    }
                    if (showReorder) {
                        Button(
                            onClick = onMoveUp,
                            enabled = canMoveUp,
                            modifier = Modifier.semantics { role = Role.Button },
                            colors = ButtonDefaults.colors(
                                containerColor = if (notflix) Color.Transparent else NuvioTheme.colors.BackgroundCard,
                                contentColor = if (notflix) spec.Secondary else NuvioTheme.colors.TextSecondary,
                                focusedContainerColor = if (notflix) {
                                    spec.Text
                                } else {
                                    NuvioTheme.colors.FocusBackground
                                },
                                focusedContentColor = if (notflix) spec.Background else NuvioTheme.colors.Primary
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.cd_move_up))
                        }
                        Button(
                            onClick = onMoveDown,
                            enabled = canMoveDown,
                            modifier = Modifier.semantics { role = Role.Button },
                            colors = ButtonDefaults.colors(
                                containerColor = if (notflix) Color.Transparent else NuvioTheme.colors.BackgroundCard,
                                contentColor = if (notflix) spec.Secondary else NuvioTheme.colors.TextSecondary,
                                focusedContainerColor = if (notflix) {
                                    spec.Text
                                } else {
                                    NuvioTheme.colors.FocusBackground
                                },
                                focusedContentColor = if (notflix) spec.Background else NuvioTheme.colors.Primary
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.cd_move_down))
                        }
                    }
                    Button(
                        onClick = onRemove,
                        modifier = Modifier.semantics { role = Role.Button },
                        colors = ButtonDefaults.colors(
                            containerColor = if (notflix) Color.Transparent else NuvioTheme.colors.BackgroundCard,
                            contentColor = if (notflix) spec.Secondary else NuvioTheme.colors.TextSecondary,
                            focusedContainerColor = if (notflix) {
                                spec.Text
                            } else {
                                NuvioTheme.colors.FocusBackground
                            },
                            focusedContentColor = if (notflix) {
                                spec.Background
                            } else {
                                NuvioTheme.colors.Error
                            }
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                    ) {
                        Text(text = stringResource(R.string.addon_remove))
                    }
                }
            }
        }

        if (!addon.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
            Text(
                text = addon.description ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = (addon.description ?: "").contentTextDirection()
                ),
                color = secondaryText
            )
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
        Text(
            text = addon.baseUrl,
            style = MaterialTheme.typography.bodySmall.copy(
                textDirection = addon.baseUrl.contentTextDirection()
            ),
            color = tertiaryText
        )

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
        Text(
            text = stringResource(R.string.addon_catalogs_types, addon.catalogs.size, addon.rawTypes.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            color = tertiaryText
        )
    }
}

/**
 * M50: the add-on manager entry cards as Notflix rows: no card, 1 dp divider, white inverted focus
 * and the icon of the action at the left.
 */
@Composable
private fun NotflixAddonEntryRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
) {
    val spec = com.nuvio.tv.ui.appearance.NotflixVisualSpec
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(
                    color = if (isFocused) spec.Text else Color.Transparent,
                    shape = RoundedCornerShape(2.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isFocused) spec.Background else spec.Secondary
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDirection = TextDirection.Content
                        ),
                        color = if (isFocused) spec.Background else spec.Text,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) spec.InvertedSecondary else spec.Secondary
                    )
                }
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isFocused) spec.Background else spec.Secondary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(spec.Divider)
        )
    }
}
