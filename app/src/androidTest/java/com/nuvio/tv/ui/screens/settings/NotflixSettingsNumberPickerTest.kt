package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.ui.appearance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixSettingsNumberPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun anExistingValueAtTheEndOfALongListIsVisibleAndFocusedWithoutChangingIt() {
        var writes = 0
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true),
                LocalNotflixSettingsFrame provides true, LocalNotflixSettingsFocus provides remember { NotflixSettingsFocus {} }) {
                NotflixPresentationTheme(true) {
                    Column {
                        SliderSettingsItem("Delay", 40, "40 s", 0, 40, 1,
                            onValueChange = { writes++ }, modifier = Modifier.testTag("number"), settingId = "delay")
                    }
                }
            }
        }
        compose.onNodeWithTag("number").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNode(hasText("40 s") and isFocused()).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun numericListKeepsExplicitUnitsAndReturnsFocusAfterImmediateSelectionAndRecreation() {
        var current by mutableIntStateOf(0)
        var writes = 0
        val labels = listOf("512 KB", "1 MB", "2 MB")
        val restore = StateRestorationTester(compose)
        restore.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true),
                LocalNotflixSettingsFrame provides true, LocalNotflixSettingsFocus provides remember { NotflixSettingsFocus {} }) {
                NotflixPresentationTheme(true) {
                    Column {
                        SliderSettingsItem("Chunk size", current, labels[current], 0, 2, 1,
                            onValueChange = { current = it; writes++ }, modifier = Modifier.testTag("number"),
                            settingId = "chunk", notflixValueLabel = { labels[it] })
                    }
                }
            }
        }
        compose.onNodeWithTag("number").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNode(hasText("512 KB") and isFocused()).assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("512 KB") and isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("1 MB").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("number").assertIsFocused().assertTextContains("1 MB", substring = true)
        compose.runOnIdle { assertEquals(1, current); assertEquals(1, writes) }
    }
}
