package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.domain.model.NotflixPlayerActions
import com.nuvio.tv.ui.appearance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixPlayerButtonsSettingTest {
    @get:Rule val compose = createComposeRule()
    private var settings by mutableStateOf(PlayerSettings())
    private var profile = 1
    private val saves = mutableListOf<Pair<List<String>, Int>>()
    @Composable private fun Content(active: Boolean = true) {
        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = active, reduceHomeMotion = true)) {
            NotflixPresentationTheme(active) {
                Column { NotflixPlayerButtonsSetting(settings, { profile }) { ids, id ->
                    saves += ids to id; if (profile == id) settings = settings.copy(notflixPlayerButtons = ids)
                } }
            }
        }
    }
    private fun open() { compose.onNodeWithTag("notflix-player-buttons-setting").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) } }
    private fun closeFromReset() {
        compose.onNodeWithTag("notflix-player-buttons-editor").performScrollToNode(hasTestTag("notflix-player-buttons-reset"))
        compose.onNodeWithTag("notflix-player-buttons-reset").requestFocus()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-player-buttons-setting").assertIsFocused()
    }

    @Test fun marksUpToFiveReordersWithTheExistingButtonsAndBackSavesAndRestoresFocus() {
        compose.setContent { Content() }; open()
        compose.onNodeWithTag("notflix-player-buttons-select-sources").performClick()
        compose.onNodeWithTag("notflix-player-buttons-editor").performScrollToNode(hasTestTag("notflix-player-buttons-select-engine"))
        compose.onNodeWithTag("notflix-player-buttons-select-engine").assertIsNotEnabled()
        compose.onNodeWithTag("notflix-player-buttons-editor").performScrollToNode(hasTestTag("notflix-player-buttons-order-sources"))
        val up = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.autoplay_priority_up)
        compose.onNode(hasAnyAncestor(hasTestTag("notflix-player-buttons-order-sources")) and hasText(up) and hasClickAction()).performClick()
        closeFromReset()
        compose.runOnIdle { assertEquals(listOf("audio_subtitles", "episodes", "next_episode", "sources", "speed"), saves.single().first) }
    }

    @Test fun draftAndOriginalProfileSurviveRecreationAndResetRestoresThePromoterDefault() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }; open()
        compose.onNodeWithTag("notflix-player-buttons-select-sources").performClick()
        restoration.emulateSavedInstanceStateRestore()
        profile = 2
        closeFromReset()
        compose.runOnIdle { assertEquals(1, saves.single().second); assertTrue("sources" in saves.single().first) }
        profile = 1
        open()
        compose.onNodeWithTag("notflix-player-buttons-reset").performClick()
        closeFromReset()
        compose.runOnIdle { assertEquals(NotflixPlayerActions.defaults, saves.last().first) }
    }

    @Test fun nuvioHasNoNewSettingAndDoesNotWriteAPreference() {
        compose.setContent { Content(active = false) }
        compose.onNodeWithTag("notflix-player-buttons-setting").assertDoesNotExist()
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
    }
}
