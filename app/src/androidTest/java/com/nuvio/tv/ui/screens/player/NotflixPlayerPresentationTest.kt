package com.nuvio.tv.ui.screens.player

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixPlayerPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun show(s: PlayerFixtureState) { compose.setContent { PlayerFixture(s) }; compose.waitForIdle() }
    private fun control(label: String) = compose.onNode(isFocusable() and hasClickAction() and
        (hasContentDescription(label) or hasAnyDescendant(hasContentDescription(label))))
    private fun button(label: String) = compose.onNode(isFocusable() and hasClickAction() and
        (hasText(label) or hasAnyDescendant(hasText(label))))
    private fun focus(n: SemanticsNodeInteraction) { n.performSemanticsAction(SemanticsActions.RequestFocus); compose.waitForIdle(); n.assertIsFocused() }
    private fun key(n: SemanticsNodeInteraction, k: Key) { focus(n); n.performKeyInput { pressKey(k) }; compose.waitForIdle() }
    private fun activate(n: SemanticsNodeInteraction) = key(n, Key.DirectionCenter)
    private fun native(s: PlayerFixtureState, code: Int, repeats: List<Int>) {
        compose.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            for (repeat in repeats) s.input.dispatchKeyEvent(KeyEvent(time, time + repeat, KeyEvent.ACTION_DOWN, code, repeat))
            s.input.dispatchKeyEvent(KeyEvent(time, time + repeats.size + 30, KeyEvent.ACTION_UP, code, 0))
        }; compose.waitForIdle()
    }

    @Test fun productionControlsHaveTopTitleCenterTransportAndBottomTimeline() {
        val s = PlayerFixtureState(); show(s)
        val stage = compose.onNodeWithTag("notflix_player_stage").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("notflix_player_title").getUnclippedBoundsInRoot()
        val transport = compose.onNodeWithTag("notflix_player_transport").getUnclippedBoundsInRoot()
        val progress = compose.onNodeWithTag("player_controls_progress").getUnclippedBoundsInRoot()
        val height = (stage.bottom - stage.top).value
        assertTrue(title.top.value / height in .05f.. .10f)
        assertTrue(((transport.top + transport.bottom) / 2).value / height in .47f.. .53f)
        assertTrue(progress.top.value / height > .80f)
        compose.onNodeWithText("Synthetic film").assertIsDisplayed()
        compose.onNodeWithText("0:30 / 2:00").assertIsDisplayed()
        control(text(R.string.cd_pause)).assertIsFocused()
        compose.runOnIdle { assertTrue(s.events.isEmpty()); assertTrue(s.timerResets > 0) }
    }

    @Test fun pauseAndBothTransportCallbacksFireExactlyOnce() {
        val s = PlayerFixtureState(); show(s)
        activate(control(text(R.string.cd_pause)))
        activate(control("Rewind 10 s")); activate(control("Forward 10 s"))
        compose.runOnIdle { assertEquals(listOf("play", "backward", "forward"), s.events); assertFalse(s.ui.isPlaying); assertTrue(s.preview.isEmpty()) }
    }

    @Test fun liveHidesTimelineDisablesSeekAndKeepsSourcesReachable() {
        val s = PlayerFixtureState().apply { timeline = timeline.copy(isLive = true, duration = 0, watchedDurationMs = 65_000) }; show(s)
        control("Rewind 10 s").assertIsNotEnabled(); control("Forward 10 s").assertIsNotEnabled()
        compose.onNodeWithTag("player_controls_progress").assertDoesNotExist()
        key(control(text(R.string.cd_pause)), Key.DirectionDown)
        control(text(R.string.cd_sources)).assertIsFocused(); activate(control(text(R.string.cd_sources)))
        compose.runOnIdle { assertEquals(listOf("sources"), s.events) }
    }

    @Test fun zeroOrUnknownDurationDoesNotInventProgressOrEnableTransportSeek() {
        val s = PlayerFixtureState().apply { timeline = timeline.copy(currentPosition = 0, duration = 0) }; show(s)
        compose.onNodeWithText("0:00 / 0:00").assertIsDisplayed(); control("Forward 10 s").assertIsNotEnabled()
        compose.runOnIdle { s.timeline = s.timeline.copy(duration = -1) }
        control("Rewind 10 s").assertIsNotEnabled(); compose.runOnIdle { assertTrue(s.events.isEmpty()) }
    }

    @Test fun dpadMovesTransportToTimelineSourcesAndBackWithoutActivation() {
        val s = PlayerFixtureState(); show(s)
        key(control(text(R.string.cd_pause)), Key.DirectionDown)
        compose.onNodeWithTag("player_controls_progress").assertIsFocused()
        compose.onNodeWithTag("player_controls_progress").performKeyInput { pressKey(Key.DirectionDown) }
        control(text(R.string.cd_sources)).assertIsFocused()
        key(control(text(R.string.cd_sources)), Key.DirectionUp)
        compose.onNodeWithTag("player_controls_progress").assertIsFocused()
        compose.onNodeWithTag("player_controls_progress").performKeyInput { pressKey(Key.DirectionUp) }
        control(text(R.string.cd_pause)).assertIsFocused(); compose.runOnIdle { assertTrue(s.events.isEmpty()) }
    }

    @Test fun heldTimelineDirectionKeepsOfficialPreviewRatesAndSingleCommit() {
        val s = PlayerFixtureState(); show(s); focus(compose.onNodeWithTag("player_controls_progress"))
        native(s, KeyEvent.KEYCODE_DPAD_RIGHT, listOf(0, 1, 3, 8, 15))
        compose.runOnIdle { assertEquals(listOf(10_000L,10_000L,20_000L,30_000L,60_000L),s.preview); assertEquals(1,s.commits); assertTrue(s.events.isEmpty()) }
        native(s, KeyEvent.KEYCODE_DPAD_LEFT, listOf(0))
        compose.runOnIdle { assertEquals(-10_000L,s.preview.last()); assertEquals(2,s.commits) }
    }

    @Test fun heldCenterOnActualTimelineDoesNotDoubleToggle() {
        val s = PlayerFixtureState(); show(s); focus(compose.onNodeWithTag("player_controls_progress"))
        native(s, KeyEvent.KEYCODE_DPAD_CENTER, listOf(0,1,2,3,4))
        compose.runOnIdle { assertEquals(listOf("play"),s.events); assertEquals(0,s.commits) }
    }

    @Test fun subtitleClearanceMeasuresBottomChromeWithoutTreatingTopTitleAsBottomObstacle() {
        val s = PlayerFixtureState().apply { ui = ui.copy(title = "Very long synthetic title ".repeat(20), isPlaying = false) }; show(s)
        val stage=compose.onNodeWithTag("notflix_player_stage").fetchSemanticsNode().boundsInRoot
        val bottom=compose.onNodeWithTag("notflix_player_bottom").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertTrue(s.clearance > 0f); assertTrue(s.clearance < stage.height * .35f)
            assertTrue(s.progressClearance > 0f); assertEquals(0f,s.titleRight)
            assertTrue(s.clearance >= stage.height-bottom.top)
        }
    }

    @Test fun allAvailableSecondaryActionsKeepOriginalCallbacks() {
        val s=PlayerFixtureState().apply { ui=ui.copy(currentSeason=1,currentEpisode=2,
            audioTracks=listOf(TrackInfo(0,"Synthetic audio","en")),subtitleTracks=listOf(TrackInfo(0,"Synthetic subtitle","es")),
            chapters=listOf(PlayerChapter(0,"Synthetic chapter 1"),PlayerChapter(60_000,"Synthetic chapter 2")),
            nextEpisode=NextEpisodeInfo("synthetic:1:3",1,3,"Synthetic next",null,null,"2020-01-01T00:00:00Z",true,available=true,unairedMessage=null),
            playbackIssueReportsEnabled=true) }; show(s)
        for((id,name) in listOf(R.string.next_episode_label to "next", R.string.cd_subtitles to "subtitle",
            R.string.cd_audio_tracks to "audio",R.string.player_chapters to "chapters",R.string.cd_sources to "sources",
            R.string.cd_episodes to "episodes",R.string.party_title to "party")) activate(control(text(id)))
        activate(control(text(R.string.cd_more_actions)))
        for((id,name) in listOf(R.string.cd_playback_speed to "speed",R.string.cd_aspect_ratio to "aspect",
            R.string.cd_open_external_player to "external",R.string.cd_stream_info to "info",R.string.player_report_issue to "report")) activate(control(text(id)))
        compose.runOnIdle { assertEquals(listOf("next","subtitle","audio","chapters","sources","episodes","party","more","speed","aspect","external","info","report"),s.events) }
    }

    @Test fun engineConfirmationCanCancelAndConfirmWithoutDuplicateDispatch() {
        val s=PlayerFixtureState().apply { ui=ui.copy(showMoreDialog=true) }; show(s)
        activate(control(text(R.string.cd_switch_player_engine)))
        compose.runOnIdle { assertFalse("engine" in s.events) }
        activate(button(text(R.string.action_cancel)))
        control(text(R.string.cd_switch_player_engine)).assertIsFocused()
        activate(control(text(R.string.cd_switch_player_engine)))
        activate(button(text(R.string.action_switch)))
        compose.runOnIdle { assertEquals(listOf("engine"),s.events) }
    }

    @Test fun unavailableSecondaryActionsAndSentReportAreNotActionable() {
        val s=PlayerFixtureState().apply { ui=ui.copy(showMoreDialog=true,playbackIssueReportsEnabled=true,playbackIssueReportStatus=PlaybackIssueReportStatus.Sent) }; show(s)
        control(text(R.string.cd_audio_tracks)).assertDoesNotExist(); control(text(R.string.cd_subtitles)).assertDoesNotExist()
        control(text(R.string.cd_episodes)).assertDoesNotExist(); control(text(R.string.player_chapters)).assertDoesNotExist()
        control(text(R.string.player_report_issue)).assertIsNotEnabled(); compose.runOnIdle { assertTrue(s.events.isEmpty()) }
    }

    @Test fun hideAndReopenDoNotDispatchPlaybackAndKeepUsefulFocus() {
        val s=PlayerFixtureState(); show(s)
        key(control(text(R.string.cd_sources)),Key.DirectionDown)
        compose.onNodeWithTag("notflix_player_stage").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("hide"),s.events); s.visible=true }
        control(text(R.string.cd_pause)).assertIsFocused(); compose.runOnIdle { assertEquals(listOf("hide"),s.events) }
    }

    @Test fun nuvioReversionKeepsSameTimelineDataAndOriginalControlRow() {
        val s=PlayerFixtureState(); show(s); compose.runOnIdle { s.active=false }
        compose.onNodeWithTag("notflix_player_stage").assertDoesNotExist(); compose.onNodeWithText("Synthetic film").assertIsDisplayed()
        compose.onNodeWithText("0:30 / 2:00").assertIsDisplayed(); control(text(R.string.cd_pause)).assertIsFocused()
        compose.runOnIdle { s.active=true }; control(text(R.string.cd_pause)).assertIsFocused()
        compose.runOnIdle { assertTrue(s.events.isEmpty()); assertEquals(30_000L,s.timeline.currentPosition) }
    }

    @Test fun spanishStringsUseLocalTestConfigurationOnly() {
        val s=PlayerFixtureState().apply { spanish=true }; show(s)
        control("Retroceder 10 s").assertIsDisplayed(); control("Avanzar 10 s").assertIsDisplayed()
        activate(control("Avanzar 10 s")); compose.runOnIdle { assertEquals(listOf("forward"),s.events) }
    }
}
