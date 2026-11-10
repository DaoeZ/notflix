package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M50: with the Notflix presentation a settings row draws the inverted focus, and a choice opens as
 * a right hand panel. With Nuvio both stay exactly as they are today.
 */
@RunWith(AndroidJUnit4::class)
class NotflixSettingsVariantTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setNotflixToggle() {
        composeRule.setContent {
            NotflixPresentationTheme(true) {
                CompositionLocalProvider(
                    LocalNotflixPresentation provides NotflixPresentation(
                        active = true,
                        navigation = null,
                        reduceHomeMotion = true
                    )
                ) {
                    SettingsGroupCard(title = "Playback") {
                        SettingsToggleRow(
                            title = "Autoplay next episode",
                            subtitle = "Starts the next one by itself",
                            checked = true,
                            onToggle = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setNuvioToggle() {
        composeRule.setContent {
            NotflixPresentationTheme(false) {
                CompositionLocalProvider(
                    LocalNotflixPresentation provides NotflixPresentation(active = false)
                ) {
                    SettingsGroupCard(title = "Playback") {
                        SettingsToggleRow(
                            title = "Autoplay next episode",
                            subtitle = "Starts the next one by itself",
                            checked = true,
                            onToggle = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setSingleChoice(notflix: Boolean) {
        composeRule.setContent {
            NotflixPresentationTheme(notflix) {
                CompositionLocalProvider(
                    LocalNotflixPresentation provides NotflixPresentation(active = notflix)
                ) {
                    SettingsSingleChoiceDialog(
                        title = "Language",
                        options = listOf(
                            SettingsPickerOption("en", "English", trailing = "EN"),
                            SettingsPickerOption("es", "Spanish", trailing = "ES")
                        ),
                        selectedValue = "en",
                        onOptionSelected = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun notflixFocusedRowDrawsTheInvertedFill() {
        setNotflixToggle()

        val row = composeRule.onNodeWithText("Autoplay next episode")
        row.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        val value = row.fetchSemanticsNode().config[NotflixVisualSpec.InvertedRowFocus]
        assertTrue("the focused Notflix row should report the inverted fill", value == true)
    }

    @Test
    fun nuvioRowNeverReportsTheNotflixFill() {
        setNuvioToggle()

        val row = composeRule.onNodeWithText("Autoplay next episode")
        row.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        val value = row.fetchSemanticsNode().config[NotflixVisualSpec.InvertedRowFocus]
        assertEquals(null, value)
    }

    @Test
    fun notflixChoiceOpensAsASidePanel() {
        setSingleChoice(notflix = true)

        composeRule.onNodeWithTag("notflix-side-panel").assertExists()
        composeRule.onNodeWithText("English · EN").assertIsDisplayed()
    }

    @Test
    fun nuvioChoiceKeepsTheCentredDialog() {
        setSingleChoice(notflix = false)

        composeRule.onNodeWithTag("notflix-side-panel").assertDoesNotExist()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("English").assertIsDisplayed()
    }
}
