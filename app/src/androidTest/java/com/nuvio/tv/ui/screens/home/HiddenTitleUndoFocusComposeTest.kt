package com.nuvio.tv.ui.screens.home

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterGrid
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterSpec
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production grid + notice + shared focus bridge; no network or profile preferences. */
@OptIn(ExperimentalTestApi::class)
class HiddenTitleUndoFocusComposeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var back: OnBackPressedDispatcher
    private var items by mutableStateOf(listOf("one", "two", "three").map { NotflixPosterSpec(it, it) })
    private var removed by mutableStateOf<String?>(null)
    private var noticeVisible by mutableStateOf(true)
    private var undoCount = 0
    private val focusEvents = mutableListOf<Boolean>()

    private fun show() {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NotflixPresentationTheme(true) {
                HiddenTitleUndoScope {
                    Box(Modifier.fillMaxSize()) {
                        NotflixPosterGrid(items, {}, columns = 3, removedFocusKey = { removed })
                        if (noticeVisible) HiddenTitleUndoNotice(HiddenTitleUndo(1, "movie:hidden", "Hidden"),
                            { undoCount++; noticeVisible = false },
                            Modifier.align(Alignment.BottomCenter), autoFocus = false,
                            onFocusChange = { focusEvents += it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun noticeDoesNotStealFocusAndDownUpAndBackReturnToTheSamePoster() {
        show()
        compose.onNodeWithTag("notflix-poster-one").assertIsFocused()
        compose.onNodeWithTag("notflix-poster-two").requestFocus()
        compose.onNodeWithTag("notflix-poster-two").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("notflix-undo-hide").assertIsFocused()
        assertTrue(focusEvents.last())
        compose.onNodeWithTag("notflix-undo-hide").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("notflix-poster-two").assertIsFocused()
        compose.onNodeWithTag("notflix-poster-two").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("notflix-poster-two").assertIsFocused()
    }

    @Test fun lateRemovalRepairCannotTakeFocusAwayFromUndo() {
        show()
        compose.onNodeWithTag("notflix-poster-two").requestFocus()
        compose.onNodeWithTag("notflix-poster-two").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { removed = "two"; items = items.filterNot { it.key == "two" } }
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-undo-hide").assertIsFocused()
        compose.onNodeWithTag("notflix-undo-hide").performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("notflix-poster-three").assertIsFocused(); true }.getOrDefault(false)
        }
    }

    @Test fun noticeKeepsDirectionalFocusAndOkRestoresThePoster() {
        show()
        compose.onNodeWithTag("notflix-poster-two").requestFocus()
            .performKeyInput { pressKey(Key.DirectionDown) }
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown).forEach { direction ->
            compose.onNodeWithTag("notflix-undo-hide").performKeyInput { pressKey(direction) }
                .assertIsFocused()
        }
        compose.onNodeWithTag("notflix-undo-hide").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("notflix-undo-hide").assertDoesNotExist()
        compose.onNodeWithTag("notflix-poster-two").assertIsFocused()
        compose.runOnIdle { assertEquals(1, undoCount) }
    }

    @Test fun homeAlsoOffersTheDownShortcutAndReturnsToItsOriginalCard() {
        val state = PreviewFixtureState().apply { environment = environment.copy(enabled = false, reduced = true) }
        compose.setContent {
            NotflixPresentationTheme(true) {
                HiddenTitleUndoScope {
                    Box(Modifier.fillMaxSize()) {
                        PreviewFixture(state)
                        HiddenTitleUndoNotice(HiddenTitleUndo(1, "movie:hidden", "Hidden"), {},
                            Modifier.align(Alignment.BottomCenter), autoFocus = false)
                    }
                }
            }
        }
        compose.onNodeWithTag(state.tag()).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("notflix-undo-hide").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag(state.tag()).assertIsFocused()
    }
}
