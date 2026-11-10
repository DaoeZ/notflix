package com.nuvio.tv.ui.screens.detail

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic callbacks only. No stores, catalogs, actual source resolution or video execution. */
@OptIn(ExperimentalTestApi::class)
class NotflixDetailPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun tag(season: Int, episode: Int) = "notflix-detail-episode-fixture:$season:$episode"
    private fun show(state: DetailFixtureState) { compose.setContent { DetailFixture(state) }; compose.waitForIdle() }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun key(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    private fun byText(label: String) = compose.onNode(isFocusable() and hasClickAction() and
        (hasText(label) or hasAnyDescendant(hasText(label))))
    private fun byDescription(label: String) = compose.onNode(isFocusable() and hasClickAction() and
        (hasContentDescription(label) or hasAnyDescendant(hasContentDescription(label))))
    private fun activate(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        node.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }
    private fun closeSource() {
        compose.onNodeWithTag("synthetic-source-close").assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    @Test fun movieHasHonestMetadataFallbackAndOfficialLibraryWatchedAndManualCallbacks() {
        val state = DetailFixtureState(); show(state)
        compose.onNodeWithTag("notflix_detail_title").assertTextEquals("Synthetic movie")
        compose.onNodeWithTag("notflix-rating-imdb").assertTextEquals("IMDb 7.6/10")
        compose.onNodeWithTag("notflix_episode_panel").assertDoesNotExist()
        activate(byDescription(text(R.string.hero_add_to_library)))
        activate(byDescription(text(R.string.hero_mark_watched)))
        compose.runOnIdle { assertEquals(listOf("library", "movie-watched"), state.actions) }
        byText(text(R.string.hero_play)).performSemanticsAction(SemanticsActions.RequestFocus)
        byText(text(R.string.hero_play)).performKeyInput { keyDown(Key.Menu); keyUp(Key.Menu) }
        activate(byText(text(R.string.play_manually)))
        compose.runOnIdle { assertEquals("manual:fixture-movie", state.actions.last()) }
        closeSource()
        byText(text(R.string.hero_play)).assertIsFocused()
    }

    @Test fun unavailableMovieKeepsLibraryAndTitleWithoutFakePlay() {
        val state = DetailFixtureState().apply { playback = false; overallRatings = HomeImdbRatingsVisibility.HIDE_ALL }
        show(state)
        compose.onNodeWithText(text(R.string.playback_unavailable)).assertIsNotEnabled()
        compose.onNodeWithTag("notflix-rating-imdb").assertDoesNotExist()
        activate(byDescription(text(R.string.hero_add_to_library)))
        compose.runOnIdle { assertEquals(listOf("library"), state.actions); assertFalse(state.overlay) }
    }

    @Test fun seriesPanelMatchesReferenceProportionsAndFocusMovesVerticallyWithoutMutation() {
        val state = DetailFixtureState(series = true); show(state)
        val stage = compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("notflix_episode_panel").getUnclippedBoundsInRoot()
        assertTrue((panel.right - panel.left).value / (stage.right - stage.left).value in .29f.. .34f)
        assertTrue(panel.left.value / (stage.right - stage.left).value in .63f.. .68f)
        val first = compose.onNodeWithTag(tag(1, 1)).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag(tag(1, 2)).getUnclippedBoundsInRoot()
        assertEquals(first.left.value, second.left.value, .5f)
        assertTrue(second.top > first.bottom)
        focus(tag(1, 1)); key(tag(1, 1), Key.DirectionDown)
        compose.onNodeWithTag(tag(1, 2)).assertIsFocused()
        key(tag(1, 2), Key.DirectionUp)
        compose.onNodeWithTag(tag(1, 1)).assertIsFocused()
        key(tag(1, 1), Key.DirectionUp)
        compose.onNodeWithTag("notflix-detail-season-1").assertIsFocused()
        key("notflix-detail-season-1", Key.DirectionLeft)
        byText(text(R.string.hero_play)).assertIsFocused()
        compose.runOnIdle { assertEquals(1, state.season); assertTrue(state.actions.isEmpty()); assertTrue(state.progress.isEmpty()) }
    }

    @Test fun seasonSelectionAndSourceReturnKeepCorrectEpisodeIdentity() {
        val state = DetailFixtureState(series = true); show(state)
        focus("notflix-detail-season-1"); key("notflix-detail-season-1", Key.DirectionRight)
        compose.onNodeWithTag("notflix-detail-next").assertIsFocused()
        compose.runOnIdle { assertEquals(1, state.season); assertTrue(state.actions.isEmpty()) }
        key("notflix-detail-next", Key.DirectionCenter)
        compose.waitUntil(5_000) { state.season == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-detail-season-2").assertIsFocused()
        key("notflix-detail-season-2", Key.DirectionDown)
        compose.onNodeWithTag(tag(2, 1)).assertIsFocused()
        key(tag(2, 1), Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("play:fixture:2:1"), state.actions); assertEquals(2, state.season) }
        closeSource()
        compose.onNodeWithTag(tag(2, 1)).assertIsFocused()
        compose.runOnIdle { assertEquals("fixture:2:1", state.lastFocused[2]); assertTrue(state.watched.isEmpty()) }
    }

    @Test fun episodeManualOptionsUseSameIdAndReturnToSameCard() {
        val state = DetailFixtureState(series = true); show(state)
        focus(tag(1, 3)); key(tag(1, 3), Key.Menu)
        activate(byText(text(R.string.play_manually)))
        compose.runOnIdle { assertEquals(listOf("manual:fixture:1:3"), state.actions) }
        closeSource()
        compose.onNodeWithTag(tag(1, 3)).assertIsFocused()
    }

    @Test fun hiddenUnwatchedRatingsAndRealBlurRequestKeepWatchedAndAvailabilityHonest() {
        val state = DetailFixtureState(series = true).apply {
            blur = true; watched = setOf(1 to 1)
            episodeRatingsVisibility = DetailImdbRatingsVisibility.HIDE_UNWATCHED_EPISODES
            unavailable = setOf("fixture:1:2")
            meta = meta.copy(videos = meta.videos.map { if (it.id == "fixture:1:2") it.copy(available = false) else it })
        }
        show(state)
        compose.onNodeWithText("IMDb 7.3", substring = true).assertExists()
        compose.onNodeWithText("IMDb 9.2", substring = true).assertDoesNotExist()
        val item = hasTestTag("notflix_episode_item")
        compose.onNode(item and hasAnyAncestor(hasTestTag(tag(1, 1))), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(NotflixEpisodeImageBlurApplied, false))
        compose.onNode(item and hasAnyAncestor(hasTestTag(tag(1, 2))), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(NotflixEpisodeImageBlurApplied, true))
        focus(tag(1, 2)); key(tag(1, 2), Key.Menu)
        compose.onNodeWithText(text(R.string.playback_unavailable)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.play_manually)).assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.actions.isEmpty()); assertEquals(setOf(1 to 1), state.watched) }
    }

    @Test fun episodeStartFromBeginningUsesSelectedIdWithoutMutatingProgress() {
        val progress = WatchProgress(contentId = "fixture-series", contentType = "series", name = "Synthetic series",
            poster = null, backdrop = null, logo = null, videoId = "fixture:1:3", season = 1, episode = 3,
            episodeTitle = "Synthetic episode 1-3", position = 300_000L, duration = 1_200_000L, lastWatched = 1L)
        val state = DetailFixtureState(series = true).apply { this.progress = mapOf((1 to 3) to progress) }
        show(state); focus(tag(1, 3)); key(tag(1, 3), Key.Menu)
        activate(byText(text(R.string.cw_action_start_from_beginning)))
        compose.runOnIdle { assertEquals(listOf("start:fixture:1:3"), state.actions); assertEquals(progress, state.progress[1 to 3]) }
        closeSource(); compose.onNodeWithTag(tag(1, 3)).assertIsFocused()
    }

    @Test fun heldDownMakesProgressInsidePanelWithoutScrollingParentOrActivatingActions() {
        val state = DetailFixtureState(series = true); show(state)
        val initialStage = compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot()
        val page = compose.onNodeWithTag("meta-details-page").getUnclippedBoundsInRoot()
        assertEquals("Initial hero focus must keep primary stage at top; page=$page", 0f, initialStage.top.value, 1f)
        focus(tag(1, 1))
        assertEquals("Entering first episode must keep primary stage at top", 0f,
            compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        val downTime = android.os.SystemClock.uptimeMillis()
        fun native(action: Int, repeat: Int) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                state.input.dispatchKeyEvent(android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(), action,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, repeat))
            }
        }
        native(android.view.KeyEvent.ACTION_DOWN, 0)
        repeat(4) { Thread.sleep(95); native(android.view.KeyEvent.ACTION_DOWN, it + 1) }
        native(android.view.KeyEvent.ACTION_UP, 0); compose.waitForIdle()
        compose.runOnIdle { assertNotEquals("fixture:1:1", state.lastFocused[1]); assertTrue(state.actions.isEmpty()); assertEquals(1, state.season) }
        val stage = compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot()
        assertEquals(0f, stage.top.value, 1f)
    }

    @Test fun missingLogoAndEmptyEpisodesLeaveTitleActionsAndExitReachable() {
        val state = DetailFixtureState(series = true).apply {
            meta = meta.copy(logo = "file:///android_asset/notflix-m11-missing-logo.png", videos = emptyList())
        }
        show(state)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("notflix_detail_title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Synthetic series").assertExists()
        compose.onNodeWithText(text(R.string.episodes_panel_no_episodes)).assertIsDisplayed()
        activate(byDescription(text(R.string.hero_add_to_library)))
        compose.runOnIdle { assertEquals(listOf("library"), state.actions) }
    }

    @Test fun nuvioRetainsOriginalHeroHorizontalEpisodesAndSecondaryCommentsRetry() {
        val state = DetailFixtureState(series = true).apply { active = false; comments = true }
        show(state)
        compose.onNodeWithTag("notflix_detail_stage").assertDoesNotExist()
        compose.onNodeWithText("Synthetic series").assertExists()
        val page = compose.onNodeWithTag("meta-details-page")
        page.performScrollToNode(hasText("Synthetic episode 1-1"))
        val first = compose.onNodeWithText("Synthetic episode 1-1").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Synthetic episode 1-2").getUnclippedBoundsInRoot()
        assertTrue(second.left > first.left)
        page.performScrollToNode(hasText("Synthetic comments error"))
        activate(byText(text(R.string.action_retry)))
        compose.runOnIdle { assertEquals(listOf("retry-comments"), state.actions) }
    }

    @Test fun notflixEpisodeCommentsKeepOfficialIdAndReachRetryAfterPrimaryRowMerge() {
        val state = DetailFixtureState(series = true).apply { comments = true }
        show(state); focus(tag(1, 2)); key(tag(1, 2), Key.Menu)
        activate(byText(text(R.string.episodes_open_comments)))
        compose.onNodeWithText("Synthetic comments error").assertIsDisplayed()
        activate(byText(text(R.string.action_retry)))
        compose.runOnIdle {
            assertTrue(state.navigation.contains("comments:fixture:1:2"))
            assertEquals(listOf("retry-comments"), state.actions)
            assertTrue(state.watched.isEmpty()); assertTrue(state.progress.isEmpty())
        }
    }

    @Test fun seasonDropdownUsesRealIdsAndRetainsOfficialLongPressOptions() {
        val state = DetailFixtureState(series = true); show(state)
        focus("notflix-detail-season-1"); key("notflix-detail-season-1", Key.DirectionCenter)
        activate(compose.onNodeWithTag("notflix-detail-season-option-2"))
        compose.runOnIdle { assertEquals(2, state.season); assertTrue(state.actions.isEmpty()) }
        compose.onNodeWithTag("notflix-detail-season-2").assertIsFocused()
        key("notflix-detail-season-2", Key.Menu)
        compose.onNodeWithText(text(R.string.episodes_mark_season_watched)).assertExists()
        compose.runOnIdle { assertEquals(2, state.season); assertTrue(state.actions.isEmpty()) }
        compose.onNodeWithText(text(R.string.episodes_mark_season_watched)).performKeyInput { keyDown(Key.Back); keyUp(Key.Back) }
        compose.waitForIdle()
        focus("notflix-detail-season-2"); key("notflix-detail-season-2", Key.DirectionCenter)
        compose.onNodeWithTag("notflix-detail-season-option-2").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, state.season); assertTrue(state.actions.isEmpty()) }
    }

    @Test fun seasonRoundTripAfterScrollingKeepsEachOfficialEpisodeAnchor() {
        val state = DetailFixtureState(series = true); show(state)
        assertEquals("Before explicit episode focus", 0f,
            compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        focus(tag(1, 5))
        assertEquals("After focusing fifth episode", 0f,
            compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        val before = compose.onNodeWithTag(tag(1, 5)).getUnclippedBoundsInRoot().top.value
        key(tag(1, 5), Key.DirectionDown)
        compose.onNodeWithTag(tag(1, 6)).assertIsFocused()
        val after = compose.onNodeWithTag(tag(1, 5)).getUnclippedBoundsInRoot().top.value
        assertTrue("The episode list must actually scroll before switching season", after < before - 1f)
        assertEquals("M11_GEOMETRY before=$before after=$after page=" +
            compose.onNodeWithTag("meta-details-page").getUnclippedBoundsInRoot() + " panel=" +
            compose.onNodeWithTag("notflix_episode_panel").getUnclippedBoundsInRoot() + "\n" +
            compose.onRoot(useUnmergedTree = true).printToString(),
            0f, compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        focus("notflix-detail-season-1"); key("notflix-detail-season-1", Key.DirectionRight)
        key("notflix-detail-next", Key.DirectionCenter)
        compose.waitUntil(5_000) { state.season == 2 }; compose.waitForIdle()
        key("notflix-detail-season-2", Key.DirectionDown)
        compose.onNodeWithTag(tag(2, 1)).assertIsFocused()
        key(tag(2, 1), Key.DirectionUp); key("notflix-detail-season-2", Key.DirectionLeft)
        compose.onNodeWithTag("notflix-detail-prev").assertIsFocused()
        key("notflix-detail-prev", Key.DirectionCenter)
        compose.waitUntil(5_000) { state.season == 1 }; compose.waitForIdle()
        key("notflix-detail-season-1", Key.DirectionDown)
        compose.onNodeWithTag(tag(1, 6)).assertIsFocused()
        compose.runOnIdle {
            assertEquals("fixture:1:6", state.lastFocused[1]); assertEquals("fixture:2:1", state.lastFocused[2])
            assertTrue(state.actions.isEmpty()); assertTrue(state.progress.isEmpty()); assertTrue(state.watched.isEmpty())
        }
    }

    @Test fun rapidOffscreenDirectionsUseLatestEpisodeWithoutScrollingParentOrActivating() {
        val state = DetailFixtureState(series = true); show(state); focus(tag(1, 1))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(6) {
                val time = android.os.SystemClock.uptimeMillis()
                state.input.dispatchKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, 0))
                state.input.dispatchKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN, 0))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tag(1, 7)).assertIsFocused()
        assertEquals(0f, compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle { assertTrue(state.actions.isEmpty()); assertTrue(state.progress.isEmpty()); assertTrue(state.watched.isEmpty()) }
    }

    @Test fun horizontalEntryFromHeroTargetsSelectorWithoutOffscreenEpisodeJump() {
        val state = DetailFixtureState(series = true); show(state)
        byText(text(R.string.hero_play)).performSemanticsAction(SemanticsActions.RequestFocus)
        byText(text(R.string.hero_play)).performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        byDescription(text(R.string.hero_add_to_library)).assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        compose.onNodeWithTag("notflix-detail-season-1").assertIsFocused()
        assertEquals(0f, compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle { assertTrue(state.actions.isEmpty()); assertEquals(1, state.season) }
    }

    @Test fun lastEpisodeCanReachOfficialSecondarySectionAndReturnWithoutMutation() {
        val state = DetailFixtureState(series = true); show(state); focus(tag(1, 5))
        repeat(3) { index -> key(tag(1, 5 + index), Key.DirectionDown) }
        compose.onNodeWithTag(tag(1, 8)).assertIsFocused()
        key(tag(1, 8), Key.DirectionDown)
        val cast = byText(text(R.string.detail_tab_cast))
        cast.assertIsFocused().assertIsDisplayed()
            .performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        compose.onNodeWithTag(tag(1, 8)).assertIsFocused()
        assertEquals(0f, compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle { assertTrue(state.actions.isEmpty()); assertTrue(state.progress.isEmpty()); assertTrue(state.watched.isEmpty()) }
    }
}
