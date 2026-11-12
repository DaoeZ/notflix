package com.nuvio.tv.ui.appearance

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixWhatsNewComposeTest {
    @get:Rule val compose = createComposeRule()
    private val items = listOf("Release note")

    @Test fun dismissedNoticeDoesNotReopenBeforeTheDeviceWriteCompletes() {
        val enabled = mutableStateOf(true)
        val canPresent = mutableStateOf(false)
        val writes = mutableListOf<Int>()
        compose.setContent {
            NotflixWhatsNewHost(enabled.value, 119, 120, "beta", items,
                onSeen = { writes += it }, canPresent = canPresent.value)
        }
        compose.onNodeWithTag("notflix-whats-new-ack").assertDoesNotExist()
        compose.runOnIdle { canPresent.value = true }
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsFocused().performClick()
        compose.runOnIdle { enabled.value = false }
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithTag("notflix-whats-new-ack").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(120), writes) }
    }

    @Test fun firstInstallIsInitializedEvenWhenNotflixIsDisabled() {
        val enabled = mutableStateOf(false)
        val writes = mutableListOf<Int>()
        compose.setContent {
            NotflixWhatsNewHost(enabled.value, 0, 120, "beta", items, onSeen = { writes += it })
        }
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithTag("notflix-whats-new-ack").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(120), writes) }
    }

    @Test fun anExistingDialogDefersTheAutomaticNotice() {
        val existing = mutableStateOf(true)
        compose.setContent {
            if (existing.value) Dialog(onDismissRequest = { existing.value = false }) {
                Box { Text("Existing dialog") }
            }
            NotflixWhatsNewHost(true, 119, 120, "beta", items, onSeen = {})
        }
        compose.onNodeWithText("Existing dialog").assertIsDisplayed()
        compose.onNodeWithTag("notflix-whats-new-ack").assertDoesNotExist()
        compose.runOnIdle { existing.value = false }
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsDisplayed().assertIsFocused()
    }

    @Test fun anOpenNoticeSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NotflixWhatsNewHost(true, 119, 120, "beta", items, onSeen = {})
        }
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("notflix-whats-new-ack").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("notflix-whats-new-ack").assertDoesNotExist()
    }

    @Test fun longNotesScrollWithTheRemoteAndKeepAcknowledgementFocused() {
        compose.setContent {
            NotflixWhatsNewPanel("beta", List(40) { "Long release note number $it" }, onDismiss = {})
        }
        val list = compose.onNodeWithTag("notflix-whats-new-items")
        val scroll = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, scroll.value())
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsDisplayed().assertIsFocused()
            .performKeyInput { repeat(3) { pressKey(Key.DirectionDown) } }
        compose.runOnIdle { assertTrue(scroll.value() > 0f) }
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsDisplayed().assertIsFocused()
    }

    @Test fun restoredStateFromAnOlderApkDoesNotSuppressANewUpdate() {
        val restoration = StateRestorationTester(compose)
        // These represent new BuildConfig and disk values read only after Activity recreation.
        var current = 120
        var lastSeen = 119
        restoration.setContent {
            NotflixWhatsNewHost(true, lastSeen, current, "beta", items, onSeen = {})
        }
        compose.onNodeWithTag("notflix-whats-new-ack").performClick()
        compose.runOnIdle {
            current = 121
            lastSeen = 120
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("notflix-whats-new-ack").assertIsDisplayed().assertIsFocused()
    }
}
