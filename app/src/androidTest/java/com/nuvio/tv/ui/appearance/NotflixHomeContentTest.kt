package com.nuvio.tv.ui.appearance

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.home.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The real B renderer with explicitly synthetic data; no production catalogs are replaced. */
@OptIn(ExperimentalTestApi::class)
class NotflixHomeContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var back: OnBackPressedDispatcher
    private val memory = NotflixHomeMemory()
    private val navigation = NotflixNavigationState()
    private val navigations = mutableListOf<String>()
    private val details = mutableListOf<String>()
    private val options = mutableListOf<String>()
    private lateinit var inputView: android.view.View
    private var downTime = 0L
    private fun item(id: String) = MetaPreview(id, ContentType.MOVIE, name = "Title $id", poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = "Description from the fixture for $id", releaseInfo = "2026", imdbRating = null, genres = emptyList())
    private fun row(key: String, ids: List<String>, phase: NotflixRowPhase = NotflixRowPhase.READY): NotflixCatalogRow {
        val catalog = CatalogRow("fixture", "Fixture", "https://example.invalid", key, key, ContentType.MOVIE, items = ids.map(::item))
        return NotflixCatalogRow(key, key, "Row $key", phase, catalog)
    }
    private fun state(rows: List<NotflixCatalogRow>, heroes: List<MetaPreview> = emptyList()) = HomeUiState(
        layoutPreferencesReady = true, isLoading = false, heroItems = heroes,
        notflixCatalog = NotflixCatalogSnapshot(1, 1, true, rows, heroes), notflixCwPhase = NotflixRowPhase.READY)
    private fun tag(row: NotflixCatalogRow, index: Int) = "notflix-card-${row.key}|${row.catalog!!.stableItemKeys()[index]}"
    private fun show(state: State<HomeUiState>) {
        compose.setContent {
            inputView = LocalView.current
            back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            val focus = remember { FocusRequester() }
            val held = remember { mutableStateOf(false) }
            val shell = rememberNotflixNavigationShell(navigation, "home", "home", setOf("home", "search", "settings"), "home",
                listOf(NotflixDestination("home", "Inicio"), NotflixDestination("search", "Buscar"), NotflixDestination("settings", "Ajustes")),
                focus, null, {}, { navigations += it }, { navigations += "exit" }, held)
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true, navigation)) {
                PresentationShellHost(shell, focus) {
                    NotflixHomeContent(state.value, memory, navigation = navigation,
                        onDetail = { id, _, _ -> details += id }, onContinue = {}, onFolder = { _, _ -> }, onVisibleRow = {},
                        onLongPress = { meta, _ -> options += meta.id }, onRetry = {})
                }
            }
        }
    }
    private fun key(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.contains(SemanticsProperties.Focused) && it.config[SemanticsProperties.Focused] } }
        compose.onNodeWithTag(tag).assertIsFocused()
    }

    private fun nativeDown(code: Int, repeat: Int = 0, long: Boolean = false) {
        if (repeat == 0) downTime = android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread {
            inputView.dispatchKeyEvent(android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(),
                android.view.KeyEvent.ACTION_DOWN, code, repeat, 0, -1, 0,
                if (long) android.view.KeyEvent.FLAG_LONG_PRESS else 0))
        }
    }
    private fun nativeUp(code: Int) = compose.runOnUiThread {
        inputView.dispatchKeyEvent(android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(),
            android.view.KeyEvent.ACTION_UP, code, 0))
    }

    @Test fun rapidDirectionsAdvanceFromThePendingTargetInsteadOfCollapsingToOneStep() {
        val row = row("rapid", (0..7).map { "item$it" })
        show(mutableStateOf(state(listOf(row))))
        awaitFocus(tag(row, 0))
        compose.onNodeWithTag(tag(row, 0)).performKeyInput {
            repeat(5) { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        }
        awaitFocus(tag(row, 5))
        compose.runOnIdle { assertTrue(details.isEmpty()); assertTrue(options.isEmpty()) }
    }

    @Test fun heldDirectionsAdvanceOnNativeRepeatsBeforeKeyUp() {
        val row = row("held", (0..7).map { "item$it" })
        show(mutableStateOf(state(listOf(row))))
        awaitFocus(tag(row, 0))
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(tag(row, 1))
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1, long = true)
        awaitFocus(tag(row, 2))
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 2)
        awaitFocus(tag(row, 3))
        nativeUp(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.runOnIdle { assertTrue(details.isEmpty()); assertTrue(options.isEmpty()) }
    }

    @Test fun continuousNativeRepeatsMakeProgressWhileHeldAndFinishAtLatestTarget() {
        val row = row("continuous", (0..30).map { "item$it" })
        show(mutableStateOf(state(listOf(row))))
        awaitFocus(tag(row, 0))
        compose.mainClock.autoAdvance = false
        var progressMidHold = -1
        var progressBeforeRelease = -1
        try {
            nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            for (repeat in 1..12) {
                // Two frames per repeat: no awaitFocus/idle pause that could mask starvation.
                compose.mainClock.advanceTimeBy(32)
                nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat, long = repeat == 1)
                if (repeat == 6) compose.runOnUiThread { progressMidHold = memory.focus?.index ?: -1 }
            }
            compose.runOnUiThread { progressBeforeRelease = memory.focus?.index ?: -1 }
        } finally {
            nativeUp(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.mainClock.autoAdvance = true
        }
        assertTrue("Focus did not advance mid-hold: $progressMidHold", progressMidHold > 0)
        assertTrue("Focus stalled during continuous repeats: $progressMidHold -> $progressBeforeRelease", progressBeforeRelease > progressMidHold)
        awaitFocus(tag(row, 13))
        compose.runOnIdle { assertTrue(details.isEmpty()); assertTrue(options.isEmpty()) }
    }

    @Test fun shortCenterCancelsPendingDirectionAndKeepsTheActivatedTitleFocused() {
        val row = row("activation", listOf("a", "b", "c"))
        show(mutableStateOf(state(listOf(row))))
        awaitFocus(tag(row, 0))
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag(tag(row, 0)).performKeyInput {
                keyDown(Key.DirectionRight); keyUp(Key.DirectionRight)
                keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter)
            }
            compose.runOnIdle { assertEquals(listOf("a"), details) }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.onNodeWithTag(tag(row, 0)).assertIsFocused()
        compose.runOnIdle { assertEquals(tag(row, 0).substringAfter('|'), memory.focus?.itemKey) }
    }

    @Test fun longCenterOpensOptionsOnceWithoutShortActionOnRelease() {
        val row = row("long", listOf("a", "b"))
        show(mutableStateOf(state(listOf(row))))
        awaitFocus(tag(row, 0))
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1, long = true)
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat = 2, long = true)
        nativeUp(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(listOf("a"), options); assertTrue("Long press also opened detail", details.isEmpty()) }
    }

    @Test fun topFocusDoesNotNavigateAndBackOnRootTopUsesOfficialExitAction() {
        show(mutableStateOf(state(listOf(row("one", listOf("a", "b"))))))
        compose.onNodeWithTag("notflix-destination-home").requestFocus()
        key("notflix-destination-home", Key.DirectionRight)
        awaitFocus("notflix-destination-search")
        compose.runOnIdle { assertTrue(navigations.isEmpty()) }
        key("notflix-destination-search", Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("search"), navigations); back.onBackPressed() }
        compose.runOnIdle { assertEquals(listOf("search", "exit"), navigations) }
    }

    @Test fun t1WithoutArtworkMakesRoomAndRightEdgeStaysInsideViewport() {
        val row = row("one", (0..7).map { "item$it" })
        show(mutableStateOf(state(listOf(row))))
        awaitFocus(tag(row, 0))
        val normal = compose.onNodeWithTag(tag(row, 1)).fetchSemanticsNode().boundsInRoot.width
        compose.waitUntil(10_000) { compose.onNodeWithTag(tag(row, 0)).fetchSemanticsNode().boundsInRoot.width > normal * 1.8f }
        compose.onNodeWithText("Description from the fixture for item0").assertExists()
        for (index in 0..6) {
            key(tag(row, index), Key.DirectionRight)
            awaitFocus(tag(row, index + 1))
        }
        compose.waitUntil(10_000) { memory.focus?.expanded == true }
        compose.waitForIdle()
        val card = compose.onNodeWithTag(tag(row, 7)).getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("notflix-home").getUnclippedBoundsInRoot()
        assertTrue(card.left >= viewport.left)
        assertTrue("Expanded T1 clips right edge: $card / $viewport", card.right <= viewport.right)
        key(tag(row, 7), Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("item7"), details) }
    }

    @Test fun transientMissingItemWaitsThenDefinitiveRemovalUsesNeighbourAndEmptyUsesTop() {
        val original = row("one", listOf("a", "b", "c"))
        val state = mutableStateOf(state(listOf(original, row("two", listOf("x")))))
        show(state)
        awaitFocus(tag(original, 0)); key(tag(original, 0), Key.DirectionRight); awaitFocus(tag(original, 1))
        val saved = memory.focus!!.itemKey
        compose.runOnIdle { state.value = state.value.copy(notflixCatalog = state.value.notflixCatalog.copy(
            rows = listOf(row("one", listOf("a"), NotflixRowPhase.PENDING), row("two", listOf("x"))))) }
        compose.runOnIdle { assertEquals(saved, memory.focus?.itemKey) }
        val completed = row("one", listOf("a", "c"))
        compose.runOnIdle { state.value = state.value.copy(notflixCatalog = state.value.notflixCatalog.copy(rows = listOf(completed, row("two", listOf("x"))))) }
        awaitFocus(tag(completed, 1))
        compose.runOnIdle { state.value = state.value.copy(notflixCatalog = state.value.notflixCatalog.copy(rows = emptyList())) }
        awaitFocus("notflix-destination-home")
    }

    @Test fun staticEditorialUsesManualChoiceAndFirstRowUpReturnsToItsActionWithoutCw() {
        val row = row("one", listOf("a", "b"))
        show(mutableStateOf(state(listOf(row), listOf(item("hero1"), item("hero2")))))
        awaitFocus("notflix-editorial-details")
        compose.mainClock.advanceTimeBy(35_000)
        compose.onNodeWithText("Title hero1").assertExists()
        key("notflix-editorial-details", Key.DirectionRight)
        compose.onNodeWithText("Title hero2").assertExists()
        key("notflix-editorial-details", Key.DirectionDown); awaitFocus(tag(row, 0))
        key(tag(row, 0), Key.DirectionUp); awaitFocus("notflix-editorial-details")
        key("notflix-editorial-details", Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("hero2"), details) }
    }

    @Test fun browsingKeepsHeaderFocusedRowAndNextRowContextThenBackRestoresEntry() {
        val rows = listOf("one", "two", "three", "four").map { row(it, listOf("a", "b")) }
        show(mutableStateOf(state(rows, listOf(item("hero")))))
        awaitFocus("notflix-editorial-details")
        key("notflix-editorial-details", Key.DirectionDown); awaitFocus(tag(rows[0], 0))
        key(tag(rows[0], 0), Key.DirectionDown); awaitFocus(tag(rows[1], 0))
        compose.onNodeWithTag("notflix-navigation").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("notflix-home").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("notflix-navigation").getUnclippedBoundsInRoot()
        val selected = compose.onNodeWithTag(tag(rows[1], 0)).getUnclippedBoundsInRoot()
        assertTrue(selected.top > header.bottom && selected.bottom <= viewport.bottom)
        compose.onNodeWithText("Row three").assertIsDisplayed()
        val next = compose.onNodeWithTag(tag(rows[2], 0)).getUnclippedBoundsInRoot()
        assertTrue("Expected next-row context, not two compact whole rows", next.top < viewport.bottom && next.bottom > viewport.bottom)
        key(tag(rows[1], 0), Key.DirectionRight); awaitFocus(tag(rows[1], 1))
        compose.runOnIdle { back.onBackPressed() }; awaitFocus(tag(rows[1], 0))
        compose.runOnIdle { back.onBackPressed() }; awaitFocus("notflix-destination-home")
        compose.onNodeWithTag("notflix-editorial").assertIsDisplayed()
        compose.runOnIdle { assertFalse(navigation.homeBrowsing) }
    }

    @Test fun cwReorderKeepsIdentityAndUniqueEpisodeTransitionFindsSameSeries() {
        fun progress(id: String) = ContinueWatchingItem.InProgress(WatchProgress(id, "series", "Series $id", null, null, null,
            "$id:1:1", 1, 1, "Episode one", 20, 100, 1))
        val a = progress("a"); val b = progress("b")
        fun cwTag(item: ContinueWatchingItem) = "notflix-card-cw|${continueWatchingItemKey(item)}"
        val current = mutableStateOf(state(listOf(row("one", listOf("catalog")))).copy(continueWatchingItems = listOf(a, b)))
        show(current)
        awaitFocus(cwTag(a)); key(cwTag(a), Key.DirectionRight); awaitFocus(cwTag(b))
        compose.runOnIdle { current.value = current.value.copy(continueWatchingItems = listOf(b, a)) }
        awaitFocus(cwTag(b))
        val next = ContinueWatchingItem.NextUp(NextUpInfo("b", "series", "Series b", null, null, null,
            "b:1:2", 1, 2, "Episode two", thumbnail = null, lastWatched = 2, sortTimestamp = 2))
        compose.runOnIdle { current.value = current.value.copy(continueWatchingItems = listOf(next, a)) }
        awaitFocus(cwTag(next))
        compose.runOnIdle { assertEquals(0, memory.focus?.index); assertEquals("series:b", memory.focus?.canonicalKey) }
    }

    @Test fun focusedLoadingRowKeepsItsPlaceWhenRealDataArrives() {
        val pending = row("one", emptyList(), NotflixRowPhase.PENDING)
        val current = mutableStateOf(state(listOf(pending)))
        show(current)
        awaitFocus("notflix-status-one")
        val loaded = row("one", listOf("a", "b"))
        compose.runOnIdle { current.value = current.value.copy(notflixCatalog = current.value.notflixCatalog.copy(rows = listOf(loaded))) }
        awaitFocus(tag(loaded, 0))
        compose.runOnIdle { assertEquals(tag(loaded, 0).substringAfter('|'), memory.focus?.itemKey) }
    }

    @Test fun emptyHomeKeepsRetryReachableFromTop() {
        show(mutableStateOf(state(emptyList())))
        awaitFocus("notflix-retry")
        compose.runOnIdle { back.onBackPressed() }; awaitFocus("notflix-destination-home")
        key("notflix-destination-home", Key.DirectionDown); awaitFocus("notflix-retry")
    }
}
