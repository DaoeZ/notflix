package com.nuvio.tv.ui.screens.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixNavigationState
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic recommendation/episode fixtures (declared; no player, trailer or network); official overlays and events. */
@OptIn(ExperimentalTestApi::class)
class NotflixPostPlayEndComposeTest {
    @get:Rule val compose = createComposeRule()
    private val output by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "nf003-m27-fixtures").apply { mkdirs() }
    }
    private val calls = mutableListOf<String>()
    private val recommendation = PostPlayRecommendation("tt-fixture-b", "movie", "Synthetic recommendation", null, null, null,
        "Declared synthetic description for the end of a movie.", "2026", 7.4f, listOf("Drama"), "112")
    private val episode = NextEpisodeInfo("tt-fixture:1:2", 1, 2, "Synthetic second episode", null, null, "2026-01-01", hasAired = true, unairedMessage = null)

    private fun host(notflix: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(notflix, NotflixNavigationState(), false)) {
                NuvioTheme { NotflixPresentationTheme(notflix) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF203040))) { content() }
                } }
            }
        }
        compose.waitForIdle()
    }
    private fun movieEnd(notflix: Boolean) = host(notflix) {
        val play = remember { FocusRequester() }; val window = remember { FocusRequester() }
        PostPlayRecommendationOverlay(
            state = PostPlayRecommendationUiState(recommendation = recommendation, recommendationCount = 1, isVisible = true),
            currentTitle = "Synthetic finished movie", showManualPlayOption = false, playFocusRequester = play,
            playerWindowFocusRequester = window, onBack = { calls += "back" }, onStopTrailer = {},
            onPlay = { calls += "play:${it.id}" }, onPlayManually = {}, onOpenDetails = { calls += "details" },
            onPlayTrailer = {}, onTrailerEnded = {}, onPreviousRecommendation = {}, onNextRecommendation = {},
            modifier = Modifier.fillMaxSize())
    }
    private fun capture(name: String) =
        File(output, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    /** Red channel inside the bottom-left padding band of a node: 255 = white button, lower = grey/dark. */
    private fun red(node: SemanticsNodeInteraction): Int {
        val b = node.fetchSemanticsNode().boundsInRoot
        return android.graphics.Color.red(compose.onRoot().captureToImage().asAndroidBitmap().getPixel((b.left + 14).toInt(), (b.bottom - 12).toInt()))
    }

    @Test fun movieEndShowsBrandBecauseYouWatchedAndAWhiteFocusedPlayThatDispatchesOnce() {
        movieEnd(true)
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        compose.onNodeWithTag("notflix-postplay-end-brand", useUnmergedTree = true).assertContentDescriptionEquals("NOTFLIX")
        compose.onNodeWithText("Because you watched Synthetic finished movie", useUnmergedTree = true).assertExists()
        val play = compose.onNodeWithText("Play"); play.assertIsFocused()
        assertTrue(red(play) > 230)
        capture("movie-end-en")
        play.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("play:tt-fixture-b"), calls) }
    }

    @Test fun nuvioMovieEndKeepsTheOfficialLook() {
        movieEnd(false)
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        compose.onNodeWithTag("notflix-postplay-end-brand", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Because you watched Synthetic finished movie", useUnmergedTree = true).assertExists()
    }

    @Test fun endPromptUsesNotflixButtonsAndOnlyTheFocusedOneIsWhite() {
        host(true) { NextEpisodeEndPromptOverlay(episode, onContinue = { calls += "continue" }, onReturnToDetails = { calls += "details" }) }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        val yes = compose.onNodeWithTag("notflix-end-prompt-yes"); val no = compose.onNodeWithTag("notflix-end-prompt-no")
        yes.assertIsFocused(); assertTrue(red(yes) > 230); assertTrue(red(no) < 200)
        capture("end-prompt-yes-focused")
        yes.performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        no.assertIsFocused(); assertTrue(red(no) > 230); assertTrue(red(yes) < 200)
        no.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("details"), calls) }
    }

    @Test fun stillWatchingFocusMovesTheWhiteBetweenContinueAndExit() {
        var mode by mutableStateOf<PostPlayMode?>(PostPlayMode.StillWatching(episode, countdownSec = 20))
        host(true) {
            val next = remember { FocusRequester() }
            PostPlayOverlay(mode, controlsVisible = false, blurUnwatchedEpisodes = false, nextEpisodeFocusRequester = next,
                progressBarFocusRequester = null, leftFocusRequester = null, onPlayNext = {}, onContinueStillWatching = { calls += "continue" },
                onDismissStillWatching = { calls += "exit" }, modifier = Modifier.fillMaxSize())
        }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        val cont = compose.onNodeWithTag("notflix-still-watching-continue"); val exit = compose.onNodeWithTag("notflix-still-watching-exit")
        cont.assertIsFocused(); assertTrue(red(cont) > 230); assertTrue(red(exit) < 200)
        cont.performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        exit.assertIsFocused(); assertTrue(red(exit) > 230); assertTrue(red(cont) < 200)
        capture("still-watching-exit-focused")
    }
}
