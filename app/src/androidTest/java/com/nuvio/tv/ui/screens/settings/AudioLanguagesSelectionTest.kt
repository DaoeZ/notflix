package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import com.nuvio.tv.data.local.AUDIO_LANGUAGES
import com.nuvio.tv.data.local.StreamAutoPlayPriorityConfig
import com.nuvio.tv.data.local.StreamAutoPlayPriority
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AudioLanguagesSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedLanguageAtTheEndOfTheLazyListReceivesFocus() {
        val last = AUDIO_LANGUAGES.last().code
        compose.setContent {
            AudioLanguagesSelectionDialog(listOf(AUDIO_LANGUAGES.last().code), {}, {})
        }
        compose.onNodeWithTag("autoplay-audio-language-$last").assertIsDisplayed().assertIsFocused()
    }

    @Test fun unsavedCheckboxSelectionSurvivesStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val first = AUDIO_LANGUAGES.first()
        var saved: List<String>? = null
        restoration.setContent {
            AudioLanguagesSelectionDialog(emptyList(), { saved = it }, {})
        }
        compose.onNodeWithText(first.nativeName).performClick()
        restoration.emulateSavedInstanceStateRestore()
        val anyLabel = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(com.nuvio.tv.R.string.autoplay_priority_audio_languages_any)
        compose.onNodeWithText(anyLabel).performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle { assertEquals(listOf(first.code), saved) }
    }

    @Test fun nuvioKeepsTheSpanishToggleAndHasNoNewLanguageRow() {
        compose.setContent { PriorityDialog(notflix = false, config = StreamAutoPlayPriorityConfig(), onSave = {}) }
        // Match the legacy title through resources, independent of the device language.
        val title = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getString(com.nuvio.tv.R.string.autoplay_priority_spanish_only_title)
        compose.onNodeWithTag("autoplay-priority-entries").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithTag("autoplay-priority-audio-languages").assertDoesNotExist()
    }

    @Test fun closingTheLanguagePickerRestoresFocusAndTheWorkingConfigSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        val last = AUDIO_LANGUAGES.last()
        var saved: StreamAutoPlayPriorityConfig? = null
        val config = StreamAutoPlayPriorityConfig(audioLanguages = listOf(last.code))
        restoration.setContent { PriorityDialog(true, config) { saved = it } }
        compose.onNodeWithTag("autoplay-priority-entries")
            .performScrollToNode(hasTestTag("autoplay-priority-audio-languages"))
        compose.onNodeWithTag("autoplay-priority-audio-languages").performClick()
        compose.onNodeWithTag("autoplay-audio-language-${last.code}").assertIsFocused().performClick()
        compose.onNodeWithTag("autoplay-audio-language-${last.code}").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("autoplay-priority-audio-languages").assertIsFocused()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("autoplay-priority-audio-languages").performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle { assertEquals(emptyList<String>(), saved?.audioLanguages) }
    }

    @Test fun priorityPickerSurvivesRecreationAndRestoresFocusAfterSelectionAndBack() {
        val restoration = StateRestorationTester(compose)
        var saved: StreamAutoPlayPriorityConfig? = null
        restoration.setContent {
            PriorityDialog(true, StreamAutoPlayPriorityConfig(audioLanguages = listOf("it", "es"))) { saved = it }
        }
        compose.onNodeWithTag("autoplay-priority-entries")
            .performScrollToNode(hasTestTag("autoplay-priority-priority"))
        compose.onNodeWithTag("autoplay-priority-priority").performClick()
        restoration.emulateSavedInstanceStateRestore()
        val language = resource(com.nuvio.tv.R.string.autoplay_priority_priority_language)
        compose.onNodeWithText(language).performClick()
        compose.onNodeWithTag("autoplay-priority-priority").assertIsFocused().performClick()
        compose.onNodeWithText(language).performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("autoplay-priority-priority").assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle { assertEquals(StreamAutoPlayPriority.IDIOMA, saved?.priority) }
    }

    @Test fun noLanguagesHidesPriority() {
        compose.setContent { PriorityDialog(true, StreamAutoPlayPriorityConfig(), {}) }
        compose.onNodeWithTag("autoplay-priority-entries")
            .performScrollToNode(hasTestTag("autoplay-priority-audio-languages"))
        compose.onNodeWithTag("autoplay-priority-priority").assertDoesNotExist()
    }

    @Test fun reorderingAndRemovingKeepFocusAndSaveTheChosenOrder() {
        val restoration = StateRestorationTester(compose)
        var saved: List<String>? = null
        restoration.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true)) {
                NotflixPresentationTheme(true) {
                    AudioLanguagesSelectionDialog(listOf("it", "es", "en"), { saved = it }, {})
                }
            }
        }
        compose.onNodeWithTag("autoplay-audio-language-entries")
            .performScrollToNode(hasTestTag("autoplay-audio-order-it"))
        orderButton("it", com.nuvio.tv.R.string.autoplay_priority_down).performClick()
        orderButton("it", com.nuvio.tv.R.string.autoplay_priority_remove).assertIsFocused()
        compose.onNodeWithTag("autoplay-audio-language-entries")
            .performScrollToNode(hasTestTag("autoplay-audio-order-en"))
        orderButton("en", com.nuvio.tv.R.string.autoplay_priority_up).performClick()
        orderButton("en", com.nuvio.tv.R.string.autoplay_priority_remove).assertIsFocused().performClick()
        orderButton("it", com.nuvio.tv.R.string.autoplay_priority_remove).assertIsFocused()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("autoplay-audio-language-it").performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle { assertEquals(listOf("es", "it"), saved) }
    }

    private fun orderButton(code: String, label: Int) = compose.onNode(
        hasText(resource(label)) and hasAnyAncestor(hasTestTag("autoplay-audio-order-$code"))
    )

    private fun resource(id: Int): String = androidx.test.platform.app.InstrumentationRegistry
        .getInstrumentation().targetContext.getString(id)

    @Composable
    private fun PriorityDialog(notflix: Boolean, config: StreamAutoPlayPriorityConfig,
        onSave: (StreamAutoPlayPriorityConfig) -> Unit) {
        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = notflix)) {
            NotflixPresentationTheme(notflix) {
                StreamAutoPlayPriorityDialog(config, emptyList(), onSave, {})
            }
        }
    }
}
