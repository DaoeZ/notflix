package com.nuvio.tv.ui.appearance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.ui.screens.player.ControlButton
import com.nuvio.tv.ui.screens.player.PlayerFixture
import com.nuvio.tv.ui.screens.player.PlayerFixtureState
import com.nuvio.tv.ui.screens.player.TrackInfo
import com.nuvio.tv.domain.model.NotflixPlayerAction
import com.nuvio.tv.ui.screens.home.LocalNotflixCompositionObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import java.util.concurrent.atomic.AtomicInteger
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixPlayerControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun m71RemoteWalksPauseTimelineRowAndMoreBackReturnsToItsButton() {
        val s = PlayerFixtureState()
        compose.setContent { PlayerFixture(s) }
        compose.onNodeWithContentDescription("Speed (1×)").assertExists()
        compose.onNodeWithContentDescription("Pause").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("player_controls_progress").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("notflix-player-action-speed").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("notflix-player-action-more").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("notflix-player-more-sources").assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-player-action-more").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("player_controls_progress").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix_player_stage").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("more", "more", "hide"), s.events) }
    }

    @Test fun m71MovingBetweenActionsDoesNotRecomposeTheControlsRoot() {
        val s = PlayerFixtureState()
        val roots = AtomicInteger()
        compose.setContent { CompositionLocalProvider(LocalNotflixCompositionObserver provides { name ->
            if (name == "player_controls") roots.incrementAndGet()
        }) { PlayerFixture(s) } }
        compose.onNodeWithTag("notflix-player-action-speed").requestFocus()
        val before = roots.get()
        repeat(5) {
            compose.onNodeWithTag("notflix-player-action-speed").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("notflix-player-action-more").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        }
        compose.onNodeWithTag("notflix-player-action-speed").assertIsFocused()
        assertEquals(before, roots.get())
    }

    @Test fun m71MountingAndClosingAnActionPanelRestoreTheProductionFocusTarget() {
        val s = PlayerFixtureState().apply { visible = false }
        compose.setContent { PlayerFixture(s) }
        compose.runOnIdle { s.visible = true }
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
        compose.onNodeWithTag("notflix-player-action-speed").performClick()
        compose.runOnIdle { s.ui = s.ui.copy(showSpeedDialog = true) }
        compose.onNodeWithContentDescription("Pause").requestFocus()
        compose.runOnIdle { s.ui = s.ui.copy(showSpeedDialog = false) }
        compose.onNodeWithTag("notflix-player-action-speed").assertIsFocused()
        compose.runOnIdle { s.visible = false }
        compose.runOnIdle { s.visible = true }
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
        compose.runOnIdle { assertEquals(listOf("speed"), s.events) }
    }

    @Test fun m71LongOkOnAnActionDispatchesOnlyOnce() {
        val s = PlayerFixtureState()
        compose.setContent { PlayerFixture(s) }
        compose.onNodeWithTag("notflix-player-action-speed").requestFocus()
        compose.runOnUiThread {
            val now = android.os.SystemClock.uptimeMillis()
            for (repeat in 0..5) s.input.dispatchKeyEvent(android.view.KeyEvent(now, now + repeat,
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat))
            s.input.dispatchKeyEvent(android.view.KeyEvent(now, now + 20, android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0))
        }
        compose.runOnIdle { assertEquals(listOf("speed"), s.events) }
    }

    @Test fun m71DisabledOrRemovedActionsCannotTrapFocusOrHideMore() {
        val s = PlayerFixtureState().apply { ui = ui.copy(playbackIssueReportsEnabled = true,
            playbackIssueReportStatus = com.nuvio.tv.ui.screens.player.PlaybackIssueReportStatus.Sent,
            notflixPlayerButtons = listOf("report")) }
        compose.setContent { PlayerFixture(s) }
        compose.onNodeWithTag("player_controls_progress").requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("notflix-player-action-more").assertIsFocused()
        compose.onNodeWithTag("notflix-player-action-report").assertIsNotEnabled()
        compose.runOnIdle { s.ui = s.ui.copy(notflixPlayerButtons = listOf("speed")) }
        compose.onNodeWithTag("notflix-player-action-speed").requestFocus()
        compose.runOnIdle { s.ui = s.ui.copy(notflixPlayerButtons = emptyList()) }
        compose.onNodeWithTag("notflix-player-action-more").assertIsFocused()
    }

    @Test fun m71TranslatedLabelsFitAtNarrowWidthAndOverflowActionsStayInMore() {
        val s = PlayerFixtureState().apply { spanish = true; ui = ui.copy(
            audioTracks = listOf(TrackInfo(0, "Audio", "es")), playbackIssueReportsEnabled = true,
            notflixPlayerButtons = listOf("audio_subtitles", "engine", "external", "report", "aspect")) }
        compose.setContent { Box(Modifier.width(720.dp).height(440.dp)) { PlayerFixture(s) } }
        val stage = compose.onNodeWithTag("notflix_player_stage").getUnclippedBoundsInRoot()
        for (action in NotflixPlayerAction.entries) {
            val nodes = compose.onAllNodesWithTag("notflix-player-action-${action.id}").fetchSemanticsNodes()
            if (nodes.isEmpty()) continue
            val node = compose.onNodeWithTag("notflix-player-action-${action.id}")
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= stage.left && bounds.right <= stage.right)
            val label = nodes.single().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(label, layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        compose.onNodeWithTag("notflix-player-action-more").performClick()
        compose.onNodeWithTag("notflix-player-more-actions").performScrollToNode(hasTestTag("notflix-player-more-external"))
        compose.onNodeWithTag("notflix-player-more-external").assertIsDisplayed()
    }

    @Test fun officialControlChangesOnlyPresentationAndKeepsFocusActionAndContentOnReversion() {
        val active = mutableStateOf(false)
        val requester = FocusRequester()
        var clicks = 0
        lateinit var marker: Any
        compose.setContent {
            NuvioTheme(appTheme = AppTheme.WHITE) {
                NotflixPresentationTheme(active.value) {
                    val identity = remember { Any() }
                    SideEffect { marker = identity }
                    ControlButton(Icons.Default.PlayArrow, contentDescription = "M4 play",
                        onClick = { clicks++ }, focusRequester = requester)
                    LaunchedEffect(Unit) { requester.requestFocus() }
                }
            }
        }
        val control = compose.onNodeWithContentDescription("M4 play")
        control.assertIsFocused()
        val identity = marker
        fun countPixels(red: Boolean): Int {
            val pixels = control.captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (c.red > .8f && (if (red) c.green < .15f && c.blue < .2f else c.green > .8f && c.blue > .8f)) count++
            }
            return count
        }
        assertTrue("Official focused control remains white", countPixels(false) > 100)
        compose.runOnIdle { active.value = true }
        control.assertIsFocused()
        assertTrue("Notflix focused control is red", countPixels(true) > 100)
        control.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, clicks); assertSame(identity, marker); active.value = false }
        control.assertIsFocused()
        assertTrue("Reversion restores official white", countPixels(false) > 100)
        control.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(2, clicks); assertSame(identity, marker) }
    }
}
