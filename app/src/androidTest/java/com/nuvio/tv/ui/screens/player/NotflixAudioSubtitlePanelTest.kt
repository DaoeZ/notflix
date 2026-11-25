package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.ui.appearance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixAudioSubtitlePanelTest {
    @get:Rule val compose = createComposeRule()
    private var audio by mutableStateOf(true)
    private val events = mutableListOf<String>()
    private fun show(active: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = active, reduceHomeMotion = true)) {
                NotflixPresentationTheme(active) { NotflixPlayerTypography {
                    Box {
                        AudioSelectionOverlay(audio, listOf(TrackInfo(0, "Audio", "en")), 0, 0, 0, false, 0, false, false,
                            {}, {}, {}, {}, {}, {}, onSwitchToSubtitles = { events += "subtitles"; audio = false })
                        SubtitleSelectionOverlay(!audio, listOf(TrackInfo(0, "Subtitle", "es")), 0, emptyList(), null,
                            SubtitleStyleSettings(), subtitleDelayMs = 0, installedSubtitleAddonOrder = emptyList(), isLoadingAddons = false,
                            onInternalTrackSelected = {}, onAddonSubtitleSelected = {}, onDisableSubtitles = {}, onEvent = {}, onDismiss = {},
                            onSwitchToAudio = { events += "audio"; audio = true })
                    }
                } }
            }
        }
    }

    @Test fun theActualSelectorsSwitchWithLeftRightAndOkAndKeepTheirTracks() {
        show()
        compose.onNodeWithTag("notflix-player-tab-audio").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("notflix-player-tab-subtitles").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("notflix-player-tab-subtitles").assertIsSelected().requestFocus().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("notflix-player-tab-audio").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("notflix-player-tab-audio").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf("subtitles", "audio"), events) }
    }

    @Test fun nuvioKeepsTheExistingSelectorWithoutCombinedTabs() {
        show(active = false)
        compose.onNodeWithTag("notflix-player-tab-audio").assertDoesNotExist()
        compose.onNodeWithTag("notflix-player-tab-subtitles").assertDoesNotExist()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun speedPanelShowsOneSuffixAndUsesTheExistingRatesWithTheRemote() {
        val rates = mutableListOf<Float>()
        var dismissals = 0
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true)) {
                NotflixPresentationTheme(true) { NotflixPlayerTypography {
                    SpeedSelectionDialog(1f, onSpeedSelected = { rates += it }, onDismiss = { dismissals++ })
                } }
            }
        }
        compose.onNodeWithText("1×").assert(hasText("✓")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("1.25×").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("1x×").assertDoesNotExist()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1.25f), rates); assertEquals(1, dismissals) }
    }

    @Test fun lateRowAvailabilityCannotStealFocusFromTheActualSpeedPanel() {
        val s = PlayerFixtureState()
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true)) {
                NotflixPresentationTheme(true) {
                    Box {
                        PlayerFixture(s)
                        if (s.ui.showSpeedDialog) SpeedSelectionDialog(1f, {},
                            onDismiss = { s.ui = s.ui.copy(showSpeedDialog = false) })
                    }
                }
            }
        }
        compose.onNodeWithTag("notflix-player-action-speed").requestFocus()
        compose.runOnIdle { s.ui = s.ui.copy(showSpeedDialog = true) }
        compose.onNodeWithText("1×").assertIsFocused()
        compose.runOnIdle { s.ui = s.ui.copy(notflixPlayerButtons = emptyList()) }
        compose.onNodeWithText("1×").assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
        compose.runOnIdle { assertTrue(s.events.isEmpty()) }
    }
}
