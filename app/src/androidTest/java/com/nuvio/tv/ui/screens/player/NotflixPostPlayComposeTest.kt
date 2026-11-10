package com.nuvio.tv.ui.screens.player

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixNavigationState
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic PostPlayMode/SkipInterval fixtures (declared, no player or network); official overlays and events. */
@OptIn(ExperimentalTestApi::class)
class NotflixPostPlayComposeTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val output by lazy { File(instrumentation.targetContext.getExternalFilesDir(null), "nf003-m26-fixtures").apply { mkdirs() } }
    private val episode = NextEpisodeInfo("tt-fixture:1:2", 1, 2, "Synthetic second episode", null, null, "2026-01-01", hasAired = true, unairedMessage = null)
    private var mode by mutableStateOf<PostPlayMode?>(PostPlayMode.AutoPlay(episode))
    private var notflix by mutableStateOf(true)
    private var spanish = false
    private val calls = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            val base = LocalContext.current
            val context = remember { if (spanish) base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale("es")) }) else base }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalNotflixPresentation provides NotflixPresentation(notflix, NotflixNavigationState(), false)) {
                NuvioTheme { NotflixPresentationTheme(notflix) {
                    val next = remember { FocusRequester() }
                    Box(Modifier.fillMaxSize().background(Color(0xFF203040))) {
                        PostPlayOverlay(mode, controlsVisible = false, blurUnwatchedEpisodes = false, nextEpisodeFocusRequester = next,
                            progressBarFocusRequester = null, leftFocusRequester = null,
                            onPlayNext = { calls += "next" }, onContinueStillWatching = { calls += "continue" },
                            onDismissStillWatching = { calls += "exit" },
                            modifier = if (mode is PostPlayMode.StillWatching && notflix) Modifier.fillMaxSize()
                                else Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 30.dp),
                            onWatchCredits = { calls += "credits" })
                    }
                } }
            }
        }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        File(output, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    // Merged tree: each button merges its label, as a screen reader and the remote see it.
    private fun node(tag: String) = compose.onNodeWithTag(tag)

    @Test fun nextEpisodeOffersWatchCreditsAndNextEachDispatchingTheOfficialEventOnce() {
        show()
        node("notflix-postplay-next-button").assertIsFocused().assertTextContains("Next episode", substring = true)
        node("notflix-postplay-credits").assertTextContains("Watch credits", substring = true)
        node("notflix-postplay-next-label").assertTextContains("S1 E2", substring = true)
        capture("next-episode-en")
        node("notflix-postplay-next-button").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("next"), calls) }
        node("notflix-postplay-next-button").performKeyInput { pressKey(Key.DirectionLeft) }
        node("notflix-postplay-credits").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("next", "credits"), calls) }
    }

    @Test fun officialCountdownAndSourceAreShownAndFillTheButton() {
        show()
        compose.runOnIdle { mode = PostPlayMode.AutoPlay(episode, searching = true) }
        node("notflix-postplay-next-label").assertTextContains("Finding source", substring = true)
        compose.runOnIdle { mode = PostPlayMode.AutoPlay(episode, sourceName = "Fixture source", countdownSec = 10) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { mode = PostPlayMode.AutoPlay(episode, sourceName = "Fixture source", countdownSec = 5) }
        compose.mainClock.advanceTimeBy(1_200); compose.waitForIdle()
        node("notflix-postplay-next-label").assertTextContains("Fixture source", substring = true)
        capture("next-episode-countdown-5")
        // Focused button is white; the countdown progress is the darker band on its left (M27 focus rule).
        val bounds = node("notflix-postplay-next-button").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        // Below the label and inside the 3dp focus border: bottom padding band, 12px from each edge.
        val y = (bounds.bottom - 12).toInt()
        val left = image.getPixel((bounds.left + 12).toInt(), y); val right = image.getPixel((bounds.right - 12).toInt(), y)
        assertTrue("fill ${Integer.toHexString(left)} vs base ${Integer.toHexString(right)}",
            android.graphics.Color.red(right) > android.graphics.Color.red(left) + 20)
    }

    @Test fun unairedNextEpisodeIsHonestAndCannotStart() {
        mode = PostPlayMode.AutoPlay(episode.copy(hasAired = false, unairedMessage = "Airs on fixture date")); show()
        node("notflix-postplay-next-button").assertIsNotEnabled()
        node("notflix-postplay-next-label").assertTextContains("Airs on fixture date", substring = true)
        node("notflix-postplay-credits").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        node("notflix-postplay-credits").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertFalse("next" in calls) }
    }

    @Test fun stillWatchingIsFullScreenWithContinueFocusedAndExitOnce() {
        mode = PostPlayMode.StillWatching(episode, countdownSec = 30); show()
        node("notflix-still-watching").assertExists()
        node("notflix-still-watching-continue").assertIsFocused()
        compose.onNodeWithText("Stopping in 30", substring = true, useUnmergedTree = true).assertExists()
        capture("still-watching-en")
        // Fixed-width primary: the white fill spans the whole button, also at its right edge.
        val bounds = node("notflix-still-watching-continue").fetchSemanticsNode().boundsInRoot
        val px = compose.onRoot().captureToImage().asAndroidBitmap().getPixel((bounds.right - 12).toInt(), (bounds.bottom - 12).toInt())
        assertTrue(Integer.toHexString(px), android.graphics.Color.red(px) > 230)
        node("notflix-still-watching-continue").performKeyInput { pressKey(Key.DirectionDown) }
        node("notflix-still-watching-exit").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("exit"), calls) }
    }

    @Test fun spanishUsesNotflixAndOfficialStrings() {
        spanish = true; show()
        node("notflix-postplay-next-button").assertTextContains("Siguiente episodio", substring = true)
        node("notflix-postplay-credits").assertTextContains("Ver créditos", substring = true)
        capture("next-episode-es")
        compose.runOnIdle { mode = PostPlayMode.StillWatching(episode, countdownSec = 12) }
        node("notflix-still-watching-continue").assertTextContains("Seguir viendo", substring = true)
        node("notflix-still-watching-exit").assertTextContains("Volver a explorar", substring = true)
        compose.onNodeWithText("¿Sigues viendo?", useUnmergedTree = true).assertExists()
    }

    @Test fun nuvioAppearanceKeepsTheOfficialCard() {
        notflix = false; show()
        node("notflix-postplay-next").assertDoesNotExist()
        compose.onNodeWithText("Next Episode", useUnmergedTree = true).assertExists()
    }

    @Test fun skipButtonIsTheRectangleOnlyInNotflixAndSkipsOnce() {
        var skips = 0
        var active by mutableStateOf(true)
        // Manual clock: with auto-advance the official 10 s auto-hide would complete before the first assertion.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active, NotflixNavigationState(), false)) {
                NuvioTheme { NotflixPresentationTheme(active) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF203040))) {
                        SkipIntroButton(interval = SkipInterval(10.0, 70.0, "intro", "introdb"), dismissed = false, controlsVisible = false,
                            onSkip = { skips++ }, onDismiss = {}, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 30.dp))
                    }
                } }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("notflix-skip-button", useUnmergedTree = true).assertTextEquals("Skip Intro")
        compose.mainClock.advanceTimeBy(1_500) // let the focused white container finish its transition before capturing
        capture("skip-intro-en")
        compose.onNodeWithText("Skip Intro").assertIsFocused()
        // Focused rectangular button is white (found dark in R2: the Card's focused colour was not applied).
        val skip = compose.onNodeWithText("Skip Intro").fetchSemanticsNode().boundsInRoot
        val px = compose.onRoot().captureToImage().asAndroidBitmap().getPixel((skip.left + 14).toInt(), (skip.bottom - 14).toInt())
        assertTrue(Integer.toHexString(px), android.graphics.Color.red(px) > 230)
        compose.onNodeWithText("Skip Intro").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(1, skips); active = false }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("notflix-skip-button", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Skip Intro").assertExists()
    }
}
