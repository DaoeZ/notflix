package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.ui.appearance.*
import org.junit.Rule
import org.junit.Test

class NotflixSettingsEngineNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateEngineSettingsCloseAnUnsupportedRestoredPageWithoutLosingFocusOrMakingABackLoop() {
        var settings by mutableStateOf(PlayerSettings())
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true)) {
                NotflixPresentationTheme(true) {
                    NotflixSettingsWorkspace(true, true, false, true, requestedDestination = "1:BUFFER") { page, open ->
                        if (page == NotflixSettingsPage.BUFFER)
                            NotflixPlaybackSettings(page, settings, P2pSettingsUi(), false, {}, {}, {}, {}, { 1 }, open)
                        else NotflixSettingsLinks(notflixSettingsChildren(page).filter {
                            it != NotflixSettingsPage.BUFFER || settings.usesExoPlayerEngine
                        }, open)
                    }
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(NotflixSettingsRowId,
            "PlaybackBufferNetworkSettings.playback_net_nuvio_performance_mode")).assertIsFocused()
        compose.runOnIdle { settings = settings.copy(internalPlayerEngine = InternalPlayerEngine.MVP_PLAYER) }
        compose.onNodeWithTag("notflix-settings-open-buffer").assertDoesNotExist()
        compose.onNodeWithTag("notflix-settings-open-sources").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-settings-open-playback_advanced").assertIsFocused()
    }
}
