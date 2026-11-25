package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.nuvio.tv.data.local.hiddenTitleKey
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** M70 on the TV: hiding the focused Home card must leave focus on its neighbour, not on the top bar. */
@OptIn(ExperimentalTestApi::class)
class NotflixHideFocusComposeTest {
    @get:Rule val compose = createComposeRule()

    private fun hide(state: PreviewFixtureState, index: Int) {
        val meta = requireNotNull(state.rows[0].catalog).items[index]
        compose.runOnIdle {
            state.state = state.state.copy(hiddenTitleKeys = state.state.hiddenTitleKeys + hiddenTitleKey(meta.apiType, meta.id))
        }
        compose.waitForIdle()
    }

    private lateinit var current: PreviewFixtureState

    private fun focusedWithin(tag: String) = try {
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused(); true }.getOrDefault(false) }
    } catch (e: Throwable) {
        throw AssertionError("$tag not focused; memory=${current.memory.focus} top=${current.navigation.topFocused}", e)
    }

    @Test fun hidingAMiddleCardFocusesTheNextCard() {
        val state = PreviewFixtureState().apply { environment = environment.copy(enabled = false, reduced = true) }
        current = state; compose.setContent { PreviewFixture(state) }
        compose.onNodeWithTag(state.tag(0, 0)).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(state.tag(0, 1)).assertIsFocused()
        hide(state, 1)
        focusedWithin(state.tag(0, 2))
        assertFalse(state.navigation.topFocused)
    }

    // The fixture row ends in its «See all» tile, which takes the hidden card's place.
    @Test fun hidingTheLastTitleKeepsFocusInTheRow() {
        val state = PreviewFixtureState().apply { environment = environment.copy(enabled = false, reduced = true) }
        current = state; compose.setContent { PreviewFixture(state) }
        val last = requireNotNull(state.rows[0].catalog).items.lastIndex
        repeat(last) { step -> compose.onNodeWithTag(state.tag(0, step)).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag(state.tag(0, last)).assertIsFocused()
        hide(state, last)
        compose.waitUntil(5_000) { state.memory.focus?.let { it.rowKey == "A" && it.itemKey == "see-all" } == true }
        assertFalse(state.navigation.topFocused)
    }
}
