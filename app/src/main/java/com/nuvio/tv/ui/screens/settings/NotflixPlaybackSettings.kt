package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.tv.data.local.PlayerSettings

/** Projects the original blocks and dialogs; callbacks and conditional engine rules stay original. */
@Composable
internal fun NotflixPlaybackSettings(
    page: NotflixSettingsPage, settings: PlayerSettings, p2p: P2pSettingsUi, transparentLetterbox: Boolean,
    onUpdate: PlaybackSettingsUpdate, onOpenDialog: (PlaybackDialog) -> Unit,
    onMemorySettingChanged: () -> Unit, onClearTorrentCache: () -> Unit,
    playerButtonsProfileId: () -> Int, open: (NotflixSettingsPage) -> Unit
) {
    if (page == NotflixSettingsPage.BUFFER && !settings.usesExoPlayerEngine) {
        // An asynchronous settings read may invalidate a restored buffer page after first focus.
        LaunchedEffect(page) { open(NotflixSettingsPage.PLAYBACK_ADVANCED) }
        return
    }
    val basic = page == NotflixSettingsPage.PLAYBACK || page == NotflixSettingsPage.SUBTITLES
    val ids = remember(page) { NotflixSettingsCatalog.ids(page) }
    CompositionLocalProvider(LocalNotflixSettingsRows provides NotflixSettingsRowFilter(ids, hideLabels = basic)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                NotflixSettingsPage.PLAYBACK -> {
                    PlaybackAudioSection(settings, onUpdate, onOpenDialog)
                    PlaybackStreamSelectionSection(settings, onUpdate, onOpenDialog)
                    PlaybackUpNextSection(settings, onUpdate, onOpenDialog)
                    PlaybackPlayerSection(settings, onUpdate, onOpenDialog)
                    NotflixPlayerButtonsSetting(settings, playerButtonsProfileId) { buttons, profile ->
                        onUpdate { setNotflixPlayerButtons(buttons, profile) }
                    }
                }
                NotflixSettingsPage.SOURCES -> PlaybackStreamSelectionSection(settings, onUpdate, onOpenDialog)
                NotflixSettingsPage.AUDIO -> PlaybackAudioSection(settings, onUpdate, onOpenDialog)
                NotflixSettingsPage.UP_NEXT -> PlaybackUpNextSection(settings, onUpdate, onOpenDialog)
                NotflixSettingsPage.PLAYER -> PlaybackPlayerSection(settings, onUpdate, onOpenDialog)
                NotflixSettingsPage.INTERFACE -> PlaybackPlayerInterfaceSection(settings, onUpdate)
                NotflixSettingsPage.SKIP -> PlaybackSkipSegmentsSection(settings, onUpdate)
                NotflixSettingsPage.SUBTITLES, NotflixSettingsPage.SUBTITLES_ADVANCED ->
                    PlaybackSubtitlesSection(settings, onUpdate, onOpenDialog)
                NotflixSettingsPage.VIDEO -> PlaybackVideoSection(settings, transparentLetterbox, onUpdate, onOpenDialog)
                NotflixSettingsPage.BUFFER -> PlaybackBufferNetworkSection(settings, onUpdate, onMemorySettingChanged)
                NotflixSettingsPage.P2P -> PlaybackP2pSection(p2p, onUpdate, onOpenDialog, onClearTorrentCache)
                else -> Unit
            }
            notflixSettingsChildren(page).forEach { NotflixSettingsLink(it, open) }
        }
    }
}
