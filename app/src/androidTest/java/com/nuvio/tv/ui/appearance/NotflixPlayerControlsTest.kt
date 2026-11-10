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
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixPlayerControlsTest {
    @get:Rule val compose = createComposeRule()

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
