package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsSingleChoiceRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionAtTheEndIsComposedAndFocused() {
        compose.setContent { Presentation { Choice(selected = 19) {} } }
        click(compose.onNodeWithTag("choice"))
        compose.onNodeWithTag("option-19").assertIsDisplayed().assertIsFocused()
    }

    @Test fun openChoiceSurvivesRecreationAndReturnsFocusAfterSelectionAndBack() {
        val restoration = StateRestorationTester(compose)
        val selected = mutableStateOf(1)
        var changes = 0
        restoration.setContent {
            Presentation { Choice(selected.value) { selected.value = it; changes++ } }
        }
        click(compose.onNodeWithTag("choice"))
        restoration.emulateSavedInstanceStateRestore()
        click(compose.onNodeWithTag("option-1").assertIsFocused())
        click(compose.onNodeWithTag("choice").assertIsFocused())
        compose.onNodeWithTag("option-1").assertIsFocused()
        back()
        compose.onNodeWithTag("choice").assertIsFocused()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun changingProfileClosesTheOldChoiceWithoutSavingIt() {
        val profile = mutableStateOf(1)
        var changes = 0
        compose.setContent { Presentation { Choice(0, profile.value) { changes++ } } }
        click(compose.onNodeWithTag("choice"))
        compose.runOnIdle { profile.value = 2 }
        compose.onNodeWithTag("option-0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun savedDialogFromAnotherProfileDoesNotReopenOnRestoration() {
        val restoration = StateRestorationTester(compose)
        var profile = 1
        restoration.setContent { Presentation { Choice(0, profile) {} } }
        click(compose.onNodeWithTag("choice"))
        profile = 2 // Deliberately read only on recreation: restore the old dialog into a new binding.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("option-0").assertDoesNotExist()
    }

    @Test fun continueWatchingStyleUsesAListOnlyWithNotflix() {
        val notflix = mutableStateOf(true)
        compose.setContent {
            Presentation(notflix.value) {
                LayoutContinueWatchingSection(LayoutSettingsUiState(continueWatchingEnabled = true)) {}
            }
        }
        compose.onNodeWithTag("layout-cw-style").assertExists()
        compose.runOnIdle { notflix.value = false }
        compose.onNodeWithTag("layout-cw-style").assertDoesNotExist()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        listOf(com.nuvio.tv.R.string.layout_cw_card_style_card, com.nuvio.tv.R.string.layout_cw_card_style_wide,
            com.nuvio.tv.R.string.layout_cw_card_style_poster).forEach {
            compose.onNodeWithText(context.getString(it)).assertExists()
        }
    }

    @Composable private fun Choice(selected: Int, profile: Int = 1, onSelect: (Int) -> Unit) {
        SettingsSingleChoiceRow(
            title = "Choice",
            value = selected.toString(),
            options = (0..19).map { SettingsPickerOption(it, "Option $it") },
            selectedValue = selected,
            onOptionSelected = onSelect,
            stateKey = profile,
            modifier = Modifier.testTag("choice"),
            optionTestTag = { "option-$it" }
        )
    }

    @Composable private fun Presentation(notflix: Boolean = true, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = notflix, reduceHomeMotion = true)) {
            NotflixPresentationTheme(notflix) { Column { content() } }
        }
    }

    // tv-material Cards ignore injected touches and Back must reach the Dialog window, as on the remote.
    private fun click(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus); compose.waitForIdle()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); compose.waitForIdle()
    }

    private fun back() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle()
    }

}
