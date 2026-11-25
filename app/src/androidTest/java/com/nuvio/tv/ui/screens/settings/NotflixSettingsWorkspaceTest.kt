package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.ui.appearance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixSettingsWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private var primary by mutableStateOf(true)
    private var request by mutableStateOf<String?>(null)
    private var profile = 1
    private var bodies = 0
    private var toggles = 0

    @Composable private fun Content(compact: Boolean = false, kids: Boolean = false, builtInHeader: Boolean = true) {
        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true)) {
            NotflixPresentationTheme(true) {
                NotflixSettingsProfileScope(profile) {
                Box(Modifier.requiredSize(if (compact) 640.dp else 960.dp, if (compact) 360.dp else 540.dp)) {
                    NotflixSettingsWorkspace(primary, true, kids, builtInHeader, request) { page, open ->
                        SideEffect { bodies++ }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            SettingsActionRow("First", null, settingId = "first", modifier = Modifier.testTag("first"), onClick = {})
                            SettingsToggleRow("Second", null, checked = false, settingId = "second",
                                modifier = Modifier.testTag("second"), onToggle = { toggles++ })
                            notflixSettingsChildren(page, primary = primary).forEach { NotflixSettingsLink(it, open) }
                        }
                    }
                }
                }
            }
        }
    }
    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun press(tag: String, key: Key) { node(tag).performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    private fun enterPlayback() {
        node("notflix-settings-section-playback").requestFocus()
        press("notflix-settings-section-playback", Key.DirectionRight)
        node("first").assertIsFocused()
    }

    @Test fun sevenMenuItemsFitWithoutScrollingAt1080p() { compose.setContent { Content() }; assertMenuFits() }
    @Test fun sevenMenuItemsFitWithoutScrollingAt720p() { compose.setContent { Content(compact = true, builtInHeader = false) }; assertMenuFits() }
    private fun assertMenuFits() {
        val menu = node("notflix-settings-menu").fetchSemanticsNode().boundsInRoot
        val bounds = NotflixSettingsSection.entries.map { section ->
            val item = node("notflix-settings-section-${section.name.lowercase()}")
            item.assertIsDisplayed(); item.fetchSemanticsNode().boundsInRoot.also {
                assertTrue(it.top >= menu.top && it.bottom <= menu.bottom)
                assertTrue(it.height > 24)
            }
        }
        assertEquals(bounds.map { it.top }.sorted(), bounds.map { it.top })
        node("notflix-settings-menu").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollBy))
    }

    @Test fun menuUsesUpDownAndRightEntersTheFirstRowAndLeftReturnsToItsSection() {
        compose.setContent { Content() }
        node("notflix-settings-section-profile").assertIsFocused()
        press("notflix-settings-section-profile", Key.DirectionDown)
        node("notflix-settings-section-playback").assertIsFocused()
        press("notflix-settings-section-playback", Key.DirectionRight)
        node("first").assertIsFocused()
        press("first", Key.DirectionUp)
        node("first").assertIsFocused() // a vertical boundary must not jump sideways to the menu
        press("first", Key.DirectionDown)
        node("second").assertIsFocused()
        press("second", Key.DirectionLeft)
        node("notflix-settings-section-playback").assertIsFocused()
        press("notflix-settings-section-playback", Key.DirectionRight)
        node("second").assertIsFocused()
    }

    @Test fun advancedBackRestoresItsExactRowAndRecreationKeepsThePageAndFocus() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Content() }; enterPlayback()
        node("notflix-settings-open-playback_advanced").requestFocus()
        press("notflix-settings-open-playback_advanced", Key.DirectionCenter)
        node("first").assertIsFocused()
        node("second").requestFocus()
        restore.emulateSavedInstanceStateRestore()
        node("second").assertIsFocused()
        back()
        node("notflix-settings-open-playback_advanced").assertIsFocused()
        back()
        node("notflix-settings-section-playback").assertIsFocused()
    }

    @Test fun movingFocusDoesNotRecomposeThePageAndOkIsOneAction() {
        compose.setContent { Content() }; enterPlayback()
        val before = bodies
        press("first", Key.DirectionDown)
        node("second").assertIsFocused()
        assertEquals(before, bodies)
        node("second").performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(1200); keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, toggles) }
    }

    @Test fun kidsEntryAndRepeatedDirectRequestsSurviveRecreationWithoutStealingLaterFocus() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Content(kids = true) }
        node("first").assertIsFocused()
        node("notflix-settings-section-accounts").assertIsSelected()
        back(); node("notflix-settings-open-tmdb_key").assertIsFocused()
        compose.runOnIdle { request = "1:ABOUT" }
        node("notflix-settings-section-system").assertIsSelected()
        node("first").assertIsFocused()
        node("second").requestFocus()
        restore.emulateSavedInstanceStateRestore()
        node("second").assertIsFocused()
        compose.runOnIdle { request = "2:ABOUT" }
        node("second").assertIsFocused() // same page preserves its last row
        back(); node("notflix-settings-open-about").assertIsFocused()
    }

    @Test fun losingPrimaryPermissionClosesTheProtectedLeaf() {
        compose.runOnIdle { request = "1:PROFILES" }
        compose.setContent { Content() }
        node("first").assertIsFocused()
        compose.runOnIdle { primary = false }
        node("notflix-settings-open-profiles").assertDoesNotExist()
        node("notflix-settings-open-party").assertExists()
        back(); node("notflix-settings-section-profile").assertIsFocused()
    }

    @Test fun restoringIntoAnotherProfileDiscardsTheOldPageAndFocus() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Content() }; enterPlayback()
        node("notflix-settings-open-playback_advanced").requestFocus()
        press("notflix-settings-open-playback_advanced", Key.DirectionCenter)
        node("second").requestFocus()
        profile = 2
        restore.emulateSavedInstanceStateRestore()
        node("notflix-settings-section-profile").assertIsFocused()
        node("notflix-settings-section-profile").assertIsSelected()
        compose.runOnIdle { assertEquals(0, toggles) }
    }

    @Test fun actualSubtitleBasicBlockHasEightRowsAndAdvancedLast() {
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true), LocalNotflixSettingsFrame provides true,
                LocalNotflixSettingsFocus provides remember { NotflixSettingsFocus {} }) {
                NotflixPresentationTheme(true) {
                    Box(Modifier.requiredSize(960.dp, 1600.dp)) {
                        NotflixPlaybackSettings(NotflixSettingsPage.SUBTITLES, PlayerSettings(), P2pSettingsUi(), false,
                            {}, {}, {}, {}, { 1 }, {})
                    }
                }
            }
        }
        compose.onAllNodes(SemanticsMatcher("identified setting") { it.config.contains(NotflixSettingsRowId) }).assertCountEquals(9)
        NotflixSettingsCatalog.subtitleBasic.forEach { id ->
            compose.onNode(SemanticsMatcher.expectValue(NotflixSettingsRowId, id)).assertExists()
        }
        node("notflix-settings-open-subtitles_advanced").assertExists()
    }
}
