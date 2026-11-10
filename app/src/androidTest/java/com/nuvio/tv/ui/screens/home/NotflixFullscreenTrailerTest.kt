package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Vista grande del tráiler aislada, con un vídeo sintético en lugar del reproductor real.
 * El foco, los controles y las salidas son los de producción.
 */
@OptIn(ExperimentalTestApi::class)
class NotflixFullscreenTrailerTest {
    @get:Rule val compose = createComposeRule()

    private val trailer = NotflixFullscreenTrailer(
        identity = "row|card",
        title = "Trailer fixture",
        logo = null,
        source = PreviewSource("https://fixture.invalid/trailer.mp4"),
        startPositionMs = 15_000L,
        meta = previewFixtureItem("card"),
        base = ""
    )
    private var plays = 0
    private var releases = 0
    private val closes = mutableListOf<Boolean>()
    private val shown = mutableStateOf(true)

    private fun show(controlsTimeoutMs: Long = 60_000L) {
        compose.setContent {
            if (shown.value) {
                NotflixFullscreenTrailerOverlay(
                    trailer = trailer,
                    onPlay = { plays++; shown.value = false },
                    onClose = { restore, _ -> closes += restore; shown.value = false },
                    controlsTimeoutMs = controlsTimeoutMs,
                    video = { ended, _ ->
                        Box(Modifier.fillMaxSize()) {
                            Button(onClick = ended, modifier = Modifier.testTag("fake-video-end")) { Text("end") }
                        }
                    },
                    onRelease = { releases++ }
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun initialFocusIsPlayAndBackClosesWithRestore() {
        show()
        compose.onNodeWithTag("notflix-fullscreen-play", useUnmergedTree = true).assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(true), closes)
            assertEquals(0, plays)
            assertEquals(1, releases)
        }
    }

    @Test
    fun playDispatchesOnceAndReleasesTheTrailer() {
        show()
        compose.onNodeWithTag("notflix-fullscreen-play", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, plays)
            assertTrue(closes.isEmpty())
            assertEquals(1, releases)
        }
    }

    @Test
    fun videoEndClosesWithoutPlayingOrResuming() {
        show()
        compose.onNodeWithTag("fake-video-end").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(false), closes)
            assertEquals(0, plays)
            assertEquals(1, releases)
        }
    }

    @Test
    fun controlsHideAfterInactivityAndAnyKeyBringsThemBack() {
        show(controlsTimeoutMs = 4_000L)
        compose.onNodeWithTag("notflix-fullscreen-play", useUnmergedTree = true).assertExists()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("notflix-fullscreen-play", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertIsFocused()
        compose.onNodeWithTag("notflix-fullscreen-trailer").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-fullscreen-play", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(0, plays) }
    }
}
