package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The production clock stays mounted while its lazy artwork is absent from the viewport. */
@OptIn(ExperimentalTestApi::class)
class NotflixBillboardRotationComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rotationSurvivesAbsentArtworkAndReducedMotionAndPausesOnlyWithBannerFocusOrInactiveHost() {
        var visible by mutableStateOf(true)
        var eligible by mutableStateOf(true)
        var focused by mutableStateOf(false)
        var index by mutableIntStateOf(0)
        var advances = 0
        val binding = PreviewBinding(AppearanceContext(1, 1), 1)
        val items = (0..2).map { MetaPreview(id = "m70:$it", type = ContentType.MOVIE, name = "Title $it",
            poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
            description = null, releaseInfo = null, imdbRating = null, genres = emptyList()) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NotflixPresentationTheme(true) {
                val header = remember { FocusRequester() }
                val info = remember { FocusRequester() }
                val play = remember { FocusRequester() }
                val progress = rememberNotflixBillboardRotation(binding, notflixMetaKey(items[index]), 0,
                    eligible, focused, items.size, onAdvance = { advances++; index = (index + 1) % items.size })
                Column {
                    Text("Header", Modifier.focusRequester(header).clickable {}.testTag("m70-header"))
                    if (visible) NotflixBillboard(items[index], index, items.size, binding, true, eligible, 0,
                        info, play, onAdvance = {}, onSelect = { index = it }, onInteraction = { focused = it },
                        onFocus = {}, onKey = { _, _ -> }, onInfo = {}, onPlay = {}, rotationProgress = progress)
                }
                LaunchedEffect(Unit) { header.requestFocus() }
            }
        }
        fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
        step(64)
        compose.runOnIdle { visible = false }
        step(64)
        val beforeHidden = advances
        step(22_000)
        assertTrue("Offscreen artwork must not stop the Home clock", advances >= beforeHidden + 2)
        compose.runOnIdle { visible = true }; step(64)
        compose.onNodeWithTag("notflix-editorial-details").requestFocus(); step(32)
        val paused = advances
        step(25_000); assertEquals(paused, advances)
        compose.onNodeWithTag("m70-header").requestFocus(); step(32); step(11_000)
        assertTrue(advances > paused)
        compose.runOnIdle { eligible = false }; step(32)
        val inactive = advances
        step(15_000); assertEquals(inactive, advances)
        compose.runOnIdle { eligible = true }; step(32); step(11_000)
        assertTrue(advances > inactive)
    }
}
