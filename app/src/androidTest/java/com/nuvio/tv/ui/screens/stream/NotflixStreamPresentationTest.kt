package com.nuvio.tv.ui.screens.stream

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.StreamBadge
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixStreamPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun show(s: StreamFixtureState) { compose.setContent { StreamFixture(s) }; compose.waitForIdle() }
    private fun control(label: String) = compose.onNode(isFocusable() and hasClickAction() and
        (hasText(label) or hasAnyDescendant(hasText(label))))
    private fun description(label: String) = compose.onNode(isFocusable() and hasClickAction() and
        (hasContentDescription(label) or hasAnyDescendant(hasContentDescription(label))))
    private fun filter(label: String) = compose.onNode(isFocusable() and hasClickAction() and hasText(label) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    private fun source(number: Int) = control("Synthetic source $number")
    private fun key(node: SemanticsNodeInteraction, k: Key) = node.performKeyInput { keyDown(k); keyUp(k) }
    private fun focus(node: SemanticsNodeInteraction) { node.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused(); compose.waitForIdle() }
    private fun activate(node: SemanticsNodeInteraction) { focus(node); key(node, Key.DirectionCenter); compose.waitForIdle() }
    private fun closeChild() = activate(compose.onNodeWithTag("synthetic-stream-child-close"))

    @Test fun realContentHasSidePanelHonestMetadataAndSingleSelectionCallback() {
        val s = StreamFixtureState(); show(s)
        val stage = compose.onNodeWithTag("notflix_stream_stage").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("notflix_stream_panel").getUnclippedBoundsInRoot()
        assertTrue((panel.right - panel.left).value / (stage.right - stage.left).value in .29f.. .34f)
        assertTrue(panel.left.value / (stage.right - stage.left).value in .63f.. .68f)
        compose.onNodeWithText("Synthetic title").assertIsDisplayed()
        compose.onNodeWithText("Drama • 2024").assertIsDisplayed()
        source(1).assertIsFocused(); activate(source(1))
        compose.runOnIdle { assertEquals(1, s.selections.size); assertSame(s.ui.filteredStreams[0], s.selections.single()); assertTrue(s.sharedSelections.isEmpty()) }
    }

    @Test fun loadingThenEmptyKeepBackUsefulWithoutSelectingAnySource() {
        val s = StreamFixtureState(0).apply { ui = ui.copy(isLoading = true, availableAddons = emptyList(), sourceChips = emptyList()) }
        show(s); compose.onAllNodesWithTag("stream_card").assertCountEquals(0)
        activate(compose.onNodeWithTag("notflix_stream_back"))
        compose.runOnIdle { assertEquals(1, s.backCount); s.ui = s.ui.copy(isLoading = false) }
        compose.onNodeWithText(text(R.string.stream_no_streams)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(s.selections.isEmpty()) }
    }

    @Test fun errorRetryUsesActualErrorComponentAndPreservesExit() {
        val s = StreamFixtureState(0).apply { ui = ui.copy(error = "Synthetic source failure", availableAddons = emptyList(), sourceChips = emptyList()) }
        show(s); compose.onNodeWithText("Synthetic source failure").assertIsDisplayed()
        activate(control(text(R.string.stream_retry)))
        compose.runOnIdle { assertEquals(1, s.retryCount); assertTrue(s.selections.isEmpty()) }
        activate(compose.onNodeWithTag("notflix_stream_back")); compose.runOnIdle { assertEquals(1, s.backCount) }
    }

    @Test fun autoplayDecisionAndDirectOverlayNeverExposeManualListPrematurely() {
        val s = StreamFixtureState().apply { ui = ui.copy(autoPlayDecided = false) }; show(s)
        compose.onNodeWithTag("notflix_stream_stage").assertDoesNotExist()
        compose.onAllNodesWithTag("stream_card").assertCountEquals(0)
        compose.runOnIdle { s.ui = s.ui.copy(autoPlayDecided = true, showDirectAutoPlayOverlay = true, directAutoPlayMessage = "Synthetic resolving") }
        compose.onNodeWithTag("notflix_stream_panel").assertDoesNotExist()
        compose.runOnIdle { s.ui = s.ui.copy(showDirectAutoPlayOverlay = false) }
        source(1).assertIsFocused(); compose.runOnIdle { assertTrue(s.selections.isEmpty()) }
    }

    @Test fun failedLogoFallsBackToActualTitleAndEpisodeMetadata() {
        val s = StreamFixtureState().apply { ui = ui.copy(logo = "file:///android_asset/m12-missing.png", season = 2,
            episode = 3, episodeName = "Synthetic episode", runtime = 74) }; show(s)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("notflix_stream_title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Synthetic title").assertIsDisplayed()
        compose.onNodeWithText("S2 E3").assertIsDisplayed()
        compose.onNodeWithText("Synthetic episode").assertIsDisplayed()
        compose.onNodeWithText("1h 14m").assertIsDisplayed()
        source(1).assertIsFocused()
    }

    @Test fun filtersAndRefreshUseExistingControllerWithoutPlayback() {
        val s = StreamFixtureState(); show(s)
        key(source(1), Key.DirectionRight); compose.waitForIdle()
        compose.runOnIdle { assertEquals("Addon A", s.ui.selectedAddonFilter); assertTrue(s.selections.isEmpty()) }
        filter("Addon A").assertIsFocused()
        key(filter("Addon A"), Key.DirectionLeft)
        filter(text(R.string.stream_filter_all)).assertIsFocused()
        key(filter(text(R.string.stream_filter_all)), Key.DirectionLeft)
        description(text(R.string.cd_refresh)).assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, s.refreshCount); assertTrue(s.selections.isEmpty()) }
    }

    @Test fun lateProviderResultsKeepFocusedNonFirstIdentityAndNeverSelect() {
        val s = StreamFixtureState(); show(s); key(source(1), Key.DirectionDown)
        source(2).assertIsFocused()
        compose.runOnIdle {
            val added = fixtureStream(99, "Addon B")
            s.ui = s.ui.copy(allStreams = listOf(added) + s.ui.allStreams, filteredStreams = listOf(added) + s.ui.filteredStreams)
        }
        source(2).assertIsFocused(); compose.runOnIdle { assertTrue(s.selections.isEmpty()) }
    }

    @Test fun partialFailureKeepsAvailableRowsAndNoInventedProviderResults() {
        val s = StreamFixtureState().apply { ui = ui.copy(sourceChips = listOf(SourceChipItem("Addon A", SourceChipStatus.SUCCESS),
            SourceChipItem("Addon B", SourceChipStatus.ERROR))) }; show(s)
        source(1).assertIsDisplayed()
        compose.runOnIdle { assertTrue(s.selections.isEmpty()) }
        activate(source(1)); compose.runOnIdle { assertSame(s.ui.filteredStreams[0], s.selections.single()) }
    }

    @Test fun duplicateRowsKeepDistinctOccurrencesAndPassExactSelectedObject() {
        val s = StreamFixtureState(0)
        val first = fixtureStream(1); val second = first.copy()
        s.ui = s.ui.copy(allStreams = listOf(first, second), filteredStreams = listOf(first, second))
        show(s)
        val rows = compose.onAllNodes(isFocusable() and hasTestTag("stream_card"))
        rows.assertCountEquals(2); activate(rows[1])
        compose.runOnIdle { assertEquals(1, s.selections.size); assertSame(second, s.selections.single()) }
        // The screen's legacy lookup can identify an identical payload at index0.
        compose.runOnIdle { s.focusedIndex = 0 }
        closeChild(); compose.onAllNodes(isFocusable() and hasTestTag("stream_card"))[1].assertIsFocused()
    }

    @Test fun childReturnTracksSelectedIdentityWhenNewResultIsInsertedBeforeIt() {
        val s = StreamFixtureState(); show(s)
        key(source(1), Key.DirectionDown); key(source(2), Key.DirectionDown)
        activate(source(3))
        compose.runOnIdle {
            val added = fixtureStream(99)
            s.ui = s.ui.copy(allStreams = listOf(added) + s.ui.allStreams, filteredStreams = listOf(added) + s.ui.filteredStreams)
        }
        closeChild()
        source(3).assertIsFocused()
        compose.runOnIdle { assertEquals(1, s.selections.size); assertTrue(s.restoredCount > 0) }
    }

    @Test fun missingSelectedResultUsesOfficialClampedFallback() {
        val s = StreamFixtureState(3); show(s); key(source(1), Key.DirectionDown); key(source(2), Key.DirectionDown)
        activate(source(3))
        compose.runOnIdle { s.ui = s.ui.copy(allStreams = s.ui.allStreams.take(1), filteredStreams = s.ui.filteredStreams.take(1)) }
        closeChild(); source(1).assertIsFocused(); compose.runOnIdle { assertEquals(1, s.selections.size) }
    }

    @Test fun actualPlayerPickerHandsOffOnceAndDismissDoesNotLaunchAnything() {
        val s = StreamFixtureState().apply { ask = true }; show(s); activate(source(1))
        compose.onNodeWithText(text(R.string.stream_player_picker_title)).assertIsDisplayed()
        activate(control(text(R.string.stream_player_external)))
        compose.runOnIdle { assertEquals(listOf("external"), s.playerChoices); assertEquals(1, s.selections.size) }
        closeChild(); source(1).assertIsFocused()
        activate(source(1))
        control(text(R.string.stream_player_internal)).performKeyInput { keyDown(Key.Back); keyUp(Key.Back) }
        compose.runOnIdle { assertFalse(s.child); assertEquals(listOf("external"), s.playerChoices); assertEquals(2, s.selections.size) }
    }

    @Test fun sharedSourceKeepsItsDedicatedCallback() {
        val s = StreamFixtureState(0).apply { shared = SharedStream("Synthetic host", fixtureStream(90)) }; show(s)
        activate(source(90))
        compose.runOnIdle { assertTrue(s.selections.isEmpty()); assertSame(s.shared!!.stream, s.sharedSelections.single()) }
    }

    @Test fun sharedChildReturnUsesSharedIdentityThenFallsBackIfItDisappears() {
        val s = StreamFixtureState(3).apply { shared = SharedStream("Synthetic host", fixtureStream(90)) }; show(s)
        activate(source(90)); closeChild(); source(90).assertIsFocused()
        activate(source(90)); compose.runOnIdle { s.shared = null }
        closeChild(); source(1).assertIsFocused()
        compose.runOnIdle { assertEquals(2, s.sharedSelections.size); assertTrue(s.selections.isEmpty()) }
    }

    @Test fun longListExpansionAndHeldKeyMakeProgressWithoutSelection() {
        val s = StreamFixtureState(40); show(s)
        val time = android.os.SystemClock.uptimeMillis()
        fun native(action: Int, repeat: Int) { InstrumentationRegistry.getInstrumentation().runOnMainSync {
            s.input.dispatchKeyEvent(android.view.KeyEvent(time, android.os.SystemClock.uptimeMillis(), action,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN, repeat))
        } }
        native(android.view.KeyEvent.ACTION_DOWN, 0)
        repeat(5) { Thread.sleep(125); native(android.view.KeyEvent.ACTION_DOWN, it + 1) }
        native(android.view.KeyEvent.ACTION_UP, 0); compose.waitForIdle()
        val focused = compose.onNode(isFocused() and hasTestTag("stream_card")).fetchSemanticsNode()
        assertFalse(focused.config[SemanticsProperties.Text].any { it.text == "Synthetic source 1" })
        compose.runOnIdle { assertTrue(s.selections.isEmpty()) }
        compose.onNodeWithTag("stream_sources_list").performScrollToNode(hasText("Synthetic source 35"))
        compose.runOnIdle { assertTrue(s.expansionCount > 0) }
    }

    @Test fun longRowsBadgesResumeAndNuvioReversionKeepSameData() {
        val s = StreamFixtureState(1).apply {
            val row = ui.filteredStreams.single().copy(description = "Synthetic long description ".repeat(12),
                pluginSourceName = "Synthetic plugin", badges = listOf(StreamBadge("Synthetic badge", imageURL = "file:///android_asset/m12-missing-badge.png")))
            ui = ui.copy(allStreams = listOf(row), filteredStreams = listOf(row)); resumeText = "Synthetic resume 00:42"
        }; show(s)
        compose.onNodeWithContentDescription("Synthetic badge").assertExists()
        compose.onNodeWithText("Synthetic plugin").assertExists()
        compose.onNodeWithText("Synthetic resume 00:42").assertExists()
        compose.runOnIdle { s.active = false }
        compose.onNodeWithTag("notflix_stream_stage").assertDoesNotExist()
        source(1).assertExists(); compose.onNodeWithContentDescription("Synthetic badge").assertExists()
        compose.runOnIdle { s.active = true }
        source(1).assertExists(); compose.runOnIdle { assertTrue(s.selections.isEmpty()) }
    }
}
