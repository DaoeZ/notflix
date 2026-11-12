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
        compose.onNodeWithTag("choice").performClick()
        compose.onNodeWithTag("option-19").assertIsDisplayed().assertIsFocused()
    }

    @Test fun openChoiceSurvivesRecreationAndReturnsFocusAfterSelectionAndBack() {
        val restoration = StateRestorationTester(compose)
        val selected = mutableStateOf(1)
        var changes = 0
        restoration.setContent {
            Presentation { Choice(selected.value) { selected.value = it; changes++ } }
        }
        compose.onNodeWithTag("choice").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("option-1").assertIsFocused().performClick()
        compose.onNodeWithTag("choice").assertIsFocused().performClick()
        compose.onNodeWithTag("option-1").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("choice").assertIsFocused()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun changingProfileClosesTheOldChoiceWithoutSavingIt() {
        val profile = mutableStateOf(1)
        var changes = 0
        compose.setContent { Presentation { Choice(0, profile.value) { changes++ } } }
        compose.onNodeWithTag("choice").performClick()
        compose.runOnIdle { profile.value = 2 }
        compose.onNodeWithTag("option-0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun savedDialogFromAnotherProfileDoesNotReopenOnRestoration() {
        val restoration = StateRestorationTester(compose)
        var profile = 1
        restoration.setContent { Presentation { Choice(0, profile) {} } }
        compose.onNodeWithTag("choice").performClick()
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
}
