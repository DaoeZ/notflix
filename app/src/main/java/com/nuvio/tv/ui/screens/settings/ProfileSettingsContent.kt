@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.requiresPrimaryPin
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.profile.NotflixPanelBodyText
import com.nuvio.tv.ui.screens.profile.NotflixPanelRow
import com.nuvio.tv.ui.screens.profile.NotflixPanelScrollColumn
import com.nuvio.tv.ui.screens.profile.NotflixProfilePanel
import com.nuvio.tv.ui.screens.profile.ProfilePinOverlay
import com.nuvio.tv.ui.screens.profile.ProfilePinOverlayState
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun ProfileSettingsContent(
    onManageProfiles: () -> Unit,
    initialFocusRequester: FocusRequester? = null,
    viewModel: ProfileSettingsViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val ageLimit by viewModel.activeAgeLimit.collectAsState()
    val hiddenTitles by viewModel.hiddenTitles.collectAsState()
    val primaryPinEnabled by viewModel.primaryPinEnabled.collectAsState()
    val isPinWorking by viewModel.isPinOperationInProgress.collectAsState()
    var showAgeDialog by remember { mutableStateOf(false) }
    var showHiddenDialog by remember { mutableStateOf(false) }
    var pendingLimit by remember { mutableStateOf<AgeLevel?>(null) }
    var hasPendingLimit by remember { mutableStateOf(false) }
    var pinOverlayState by remember { mutableStateOf<ProfilePinOverlayState?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val notflixSkin = notflixSettingsActive()
    val pinInvalid = stringResource(R.string.profile_pin_invalid)
    val pinVerifyError = stringResource(R.string.profile_pin_verify_error)

    val primaryProfile = profiles.firstOrNull { it.isPrimary }
    val currentLabel = ageLimitLabel(ageLimit)
    val confirmSound = rememberNotflixConfirmSound()
    // M52: one age-limit action shared by the Nuvio dialog and its Notflix panel.
    val selectAgeLimit: (level: AgeLevel?, selectedNow: Boolean) -> Unit = { level, selectedNow ->
        confirmSound()
        if (selectedNow) {
            showAgeDialog = false
        } else if (requiresPrimaryPin(
                primaryHasPin = primaryPinEnabled,
                changingAgeLimit = true,
                leavingLimitedProfile = false
            )
        ) {
            if (primaryProfile != null) {
                pendingLimit = level
                hasPendingLimit = true
                showAgeDialog = false
                pinError = null
                pinOverlayState = ProfilePinOverlayState.Unlock(primaryProfile)
            }
        } else {
            viewModel.setAgeLimit(level)
            showAgeDialog = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            SettingsDetailHeader(
                title = stringResource(R.string.profile_title),
                subtitle = stringResource(R.string.profile_subtitle)
            )
            SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
                SettingsActionRow(
                    title = stringResource(R.string.profile_manage_button),
                    subtitle = null,
                    onClick = onManageProfiles,
                    modifier = if (initialFocusRequester != null) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    }
                )
                SettingsActionRow(
                    title = stringResource(R.string.profile_age_limit_label),
                    subtitle = stringResource(R.string.profile_age_limit_subtitle),
                    value = currentLabel,
                    onClick = { showAgeDialog = true }
                )
                SettingsActionRow(
                    title = stringResource(R.string.profile_hidden_titles, hiddenTitles.size),
                    subtitle = null,
                    onClick = { showHiddenDialog = true }
                )
            }
        }

        if (showAgeDialog) {
            val options = listOf(
                null to stringResource(R.string.profile_age_limit_none),
                AgeLevel.TP to stringResource(R.string.profile_age_limit_tp),
                AgeLevel.A7 to stringResource(R.string.profile_age_limit_7),
                AgeLevel.A12 to stringResource(R.string.profile_age_limit_12),
                AgeLevel.A16 to stringResource(R.string.profile_age_limit_16),
                AgeLevel.A18 to stringResource(R.string.profile_age_limit_18)
            )
            if (notflixSkin) {
                NotflixProfilePanel(
                    title = stringResource(R.string.profile_age_limit_title),
                    onDismiss = { showAgeDialog = false }
                ) {
                    if (!primaryPinEnabled) {
                        NotflixPanelBodyText(
                            text = stringResource(R.string.profile_age_limit_warning),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    options.forEach { (level, label) ->
                        val selected = ageLimit == level
                        NotflixPanelRow(
                            label = label,
                            selected = selected,
                            trailing = if (selected) "✓" else null,
                            onClick = { selectAgeLimit(level, selected) }
                        )
                    }
                }
            } else {
                NuvioDialog(
                    onDismiss = { showAgeDialog = false },
                    title = stringResource(R.string.profile_age_limit_title),
                    width = 380.dp
                ) {
                    if (!primaryPinEnabled) {
                        Text(
                            text = stringResource(R.string.profile_age_limit_warning),
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                    options.forEach { (level, label) ->
                        val selected = ageLimit == level
                        Button(
                            onClick = { selectAgeLimit(level, selected) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = NuvioTheme.colors.BackgroundCard,
                                contentColor = NuvioTheme.colors.TextPrimary
                            )
                        ) {
                            Text(if (selected) "✓ $label" else label)
                        }
                    }
                }
            }
        }

        if (showHiddenDialog) {
            if (notflixSkin) {
                NotflixProfilePanel(
                    title = stringResource(R.string.profile_hidden_titles, hiddenTitles.size),
                    onDismiss = { showHiddenDialog = false },
                    width = 600.dp
                ) {
                    if (hiddenTitles.isEmpty()) {
                        NotflixPanelBodyText(stringResource(R.string.profile_hidden_titles_empty))
                    } else {
                        NotflixPanelScrollColumn {
                            hiddenTitles.forEach { entry ->
                                NotflixPanelRow(
                                    label = entry.title,
                                    trailing = stringResource(R.string.profile_hidden_title_restore),
                                    onClick = { confirmSound(); viewModel.unhideTitle(entry.key) }
                                )
                            }
                        }
                    }
                }
            } else {
                NuvioDialog(
                    onDismiss = { showHiddenDialog = false },
                    title = stringResource(R.string.profile_hidden_titles, hiddenTitles.size),
                    width = 460.dp
                ) {
                    if (hiddenTitles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_hidden_titles_empty),
                            color = NuvioTheme.colors.TextSecondary
                        )
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hiddenTitles.forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = entry.title,
                                        modifier = Modifier.weight(1f),
                                        color = NuvioTheme.colors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Button(
                                        onClick = { confirmSound(); viewModel.unhideTitle(entry.key) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = NuvioTheme.colors.BackgroundCard,
                                            contentColor = NuvioTheme.colors.TextPrimary
                                        )
                                    ) {
                                        Text(stringResource(R.string.profile_hidden_title_restore))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        pinOverlayState?.let { state ->
            ProfilePinOverlay(
                state = state,
                isWorking = isPinWorking,
                errorMessage = pinError,
                onClearError = { pinError = null },
                notflixSkin = notflixSkin,
                onDismiss = {
                    pinOverlayState = null
                    pinError = null
                    pendingLimit = null
                    hasPendingLimit = false
                },
                onSubmit = { pin ->
                    viewModel.verifyPrimaryPin(pin) { result ->
                        result.onSuccess { verify ->
                            if (verify.unlocked) {
                                pinError = null
                                pinOverlayState = null
                                if (hasPendingLimit) {
                                    viewModel.setAgeLimit(pendingLimit)
                                    hasPendingLimit = false
                                    pendingLimit = null
                                }
                            } else {
                                pinError = if (verify.retryAfterSeconds > 0) {
                                    context.getString(R.string.profile_pin_locked, verify.retryAfterSeconds)
                                } else {
                                    pinInvalid
                                }
                            }
                        }.onFailure {
                            pinError = pinVerifyError
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ageLimitLabel(level: AgeLevel?): String = when (level) {
    null -> stringResource(R.string.profile_age_limit_none)
    AgeLevel.TP -> stringResource(R.string.profile_age_limit_tp)
    AgeLevel.A7 -> stringResource(R.string.profile_age_limit_7)
    AgeLevel.A12 -> stringResource(R.string.profile_age_limit_12)
    AgeLevel.A16 -> stringResource(R.string.profile_age_limit_16)
    AgeLevel.A18 -> stringResource(R.string.profile_age_limit_18)
    AgeLevel.X -> stringResource(R.string.profile_age_limit_x)
}
