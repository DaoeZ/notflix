package com.nuvio.tv.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.updater.NotflixUpdateSource
import com.nuvio.tv.updater.UpdateChannel
import com.nuvio.tv.updater.UpdateViewModel

@Composable
internal fun UpdateChannelSettings(
    initialFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    // M54: a Notflix build without a published repository has nothing to check at all, so it offers
    // no channel, banner, auto-install or check row and never looks at the NuvioTV releases.
    if (notflixSettingsActive() && NotflixUpdateSource.notflixRepository() == null) {
        NotflixUpdatesUnavailableRow()
        return
    }
    val context = LocalContext.current
    val viewModel: UpdateViewModel = hiltViewModel(context as ComponentActivity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChannelDialog by rememberSaveable { mutableStateOf(false) }
    val channelName = when (state.updateChannel) {
        UpdateChannel.STABLE -> stringResource(R.string.update_channel_stable)
        UpdateChannel.BETA -> stringResource(R.string.update_channel_beta)
    }

    SettingsActionRow(
        title = stringResource(R.string.about_update_channel_title),
        subtitle = stringResource(R.string.about_update_channel_subtitle),
        value = channelName,
        onClick = { showChannelDialog = true },
        modifier = modifier.then(
            if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester)
            else Modifier
        )
    )

    SettingsToggleRow(
        title = stringResource(R.string.about_update_banner_title),
        subtitle = stringResource(R.string.about_update_banner_subtitle),
        checked = state.updateBannerEnabled,
        onToggle = {
            viewModel.setUpdateBannerEnabled(!state.updateBannerEnabled)
        }
    )

    if (state.autoInstallSupported) {
        SettingsToggleRow(
            title = stringResource(R.string.about_update_auto_install_title),
            subtitle = stringResource(R.string.about_update_auto_install_subtitle),
            checked = state.autoInstallEnabled,
            onToggle = {
                viewModel.setAutoInstallEnabled(!state.autoInstallEnabled)
            }
        )
        if (state.autoInstallEnabled) {
            SettingsToggleRow(
                title = stringResource(R.string.about_update_auto_install_notice_title),
                subtitle = stringResource(R.string.about_update_auto_install_notice_subtitle),
                checked = state.autoInstallNoticeEnabled,
                onToggle = {
                    viewModel.setAutoInstallNoticeEnabled(!state.autoInstallNoticeEnabled)
                }
            )
        }
    }

    SettingsActionRow(
        title = stringResource(R.string.about_check_updates),
        subtitle = stringResource(R.string.about_check_updates_subtitle),
        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        onClick = {
            viewModel.checkForUpdates(force = true, showNoUpdateFeedback = true)
        }
    )

    if (showChannelDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.about_update_channel_title),
            subtitle = stringResource(R.string.update_channel_dialog_subtitle),
            options = listOf(
                SettingsPickerOption(
                    value = UpdateChannel.STABLE,
                    title = stringResource(R.string.update_channel_stable),
                    description = stringResource(R.string.update_channel_stable_description)
                ),
                SettingsPickerOption(
                    value = UpdateChannel.BETA,
                    title = stringResource(R.string.update_channel_beta),
                    description = stringResource(R.string.update_channel_beta_description)
                )
            ),
            selectedValue = state.updateChannel,
            onOptionSelected = { channel ->
                viewModel.setUpdateChannel(channel)
                showChannelDialog = false
            },
            onDismiss = { showChannelDialog = false },
            width = 500.dp,
            maxHeight = 320.dp
        )
    }
}

/**
 * M54: static Notflix row. It only says where updates will come from once the public repository
 * exists, so it carries no focus target and no button that could reach NuvioTV.
 */
@Composable
private fun NotflixUpdatesUnavailableRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Spec.Surface, RoundedCornerShape(2.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.notflix_settings_updates_unavailable),
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
