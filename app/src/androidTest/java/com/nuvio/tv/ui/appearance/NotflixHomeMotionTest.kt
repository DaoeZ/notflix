package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.home.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Home renderer and rendered motion values; explicitly synthetic catalog. */
@OptIn(ExperimentalTestApi::class)
class NotflixHomeMotionTest {
    @get:Rule val compose = createComposeRule()
    private val reduced = mutableStateOf(false)
    private val memory = NotflixHomeMemory()
    private val navigation = NotflixNavigationState()
    private val details = mutableListOf<String>()
    private val options = mutableListOf<String>()
    private var mounts = 0
    private var disposals = 0
    private lateinit var input: android.view.View
    private var downTime = 0L
    private fun item(index: Int) = MetaPreview("item$index", ContentType.MOVIE, name = "Motion title $index",
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = "Motion fixture information $index", releaseInfo = "2026", imdbRating = null, genres = emptyList())
    private val catalog = CatalogRow("fixture", "Fixture", "https://example.invalid", "motion", "motion", ContentType.MOVIE,
        items = (0..30).map(::item))
    private val row = NotflixCatalogRow("motion", "motion", "Motion fixture", NotflixRowPhase.READY, catalog)
    private fun tag(index: Int) = "notflix-card-motion|${catalog.stableItemKeys()[index]}"
    private fun show() {
        compose.setContent {
            input = LocalView.current
            val focus = remember { FocusRequester() }
            val held = remember { mutableStateOf(false) }
            val shell = rememberNotflixNavigationShell(navigation, "home", "home", setOf("home", "settings"), "home",
                listOf(NotflixDestination("home", "Home"), NotflixDestination("settings", "Settings")), focus, null, {}, {}, {}, held)
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true, navigation, reduced.value)) {
                PresentationShellHost(shell, focus) {
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    NotflixHomeContent(HomeUiState(layoutPreferencesReady = true, isLoading = false,
                        notflixCatalog = NotflixCatalogSnapshot(1, 1, true, listOf(row), emptyList()),
                        notflixCwPhase = NotflixRowPhase.READY), memory, navigation = navigation,
                        onDetail = { id, _, _ -> details += id }, onContinue = {}, onFolder = { _, _ -> },
                        onVisibleRow = {}, onLongPress = { meta, _ -> options += meta.id }, onRetry = {})
                }
            }
        }
    }
    private fun awaitFocus(index: Int) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag(index)).fetchSemanticsNodes().any {
            it.config.contains(SemanticsProperties.Focused) && it.config[SemanticsProperties.Focused]
        } }
        compose.onNodeWithTag(tag(index)).assertIsFocused()
    }
    private fun width(index: Int) = compose.onNodeWithTag(tag(index)).fetchSemanticsNode().config[NotflixVisualSpec.RenderedCardWidthDp]
    private fun scale(index: Int) = compose.onNodeWithTag(tag(index)).fetchSemanticsNode().config[NotflixVisualSpec.RenderedCardScale]
    private fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private data class MotionFrame(val elapsed: Long, val width: Float, val scale: Float)
    private fun traceUntilExpanded(index: Int): List<MotionFrame> {
        var focusedAt: Long? = null
        val frames = mutableListOf<MotionFrame>()
        repeat(100) {
            advance(16)
            val node = compose.onAllNodesWithTag(tag(index)).fetchSemanticsNodes().singleOrNull()
            if (node != null && node.config.contains(SemanticsProperties.Focused) && node.config[SemanticsProperties.Focused]) {
                if (focusedAt == null) focusedAt = compose.mainClock.currentTime
                val frame = MotionFrame(compose.mainClock.currentTime - requireNotNull(focusedAt),
                    node.config[NotflixVisualSpec.RenderedCardWidthDp], node.config[NotflixVisualSpec.RenderedCardScale])
                frames += frame
                if (frame.elapsed >= 850 && frame.width == NotflixVisualSpec.EXPANDED_WIDTH.toFloat()) {
                    android.util.Log.i("NF_M7_MOTION", "item=$index reduced=${reduced.value} trace=$frames")
                    return frames
                }
            }
        }
        throw AssertionError("No focused final expansion for item$index: $frames")
    }
    private fun nativeDown(code: Int, repeat: Int = 0, long: Boolean = false) {
        if (repeat == 0) downTime = android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread { input.dispatchKeyEvent(android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(),
            android.view.KeyEvent.ACTION_DOWN, code, repeat, 0, -1, 0, if (long) android.view.KeyEvent.FLAG_LONG_PRESS else 0)) }
    }
    private fun nativeUp(code: Int) = compose.runOnUiThread {
        input.dispatchKeyEvent(android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, code, 0))
    }

    @Test fun reducedExpansionFromVisibleRightEdgePreservesTitleAndInformationInsideRow() {
        reduced.value = true
        show()
        awaitFocus(0)
        compose.waitUntil(10_000) { memory.focus?.expanded == true }
        compose.waitForIdle()
        val viewport = compose.onNodeWithTag("notflix-row-motion").getUnclippedBoundsInRoot()
        val target = (1..8).lastOrNull { index ->
            val nodes = compose.onAllNodesWithTag(tag(index)).fetchSemanticsNodes()
            if (nodes.isEmpty()) false else {
                val bounds = compose.onNodeWithTag(tag(index)).getUnclippedBoundsInRoot()
                bounds.left < viewport.right && bounds.left + NotflixVisualSpec.EXPANDED_WIDTH.dp > viewport.right
            }
        } ?: throw AssertionError("No visible expansion target at row edge: $viewport")
        val before = compose.onNodeWithTag(tag(target)).getUnclippedBoundsInRoot()
        // Home intentionally rejects unsolicited programmatic identity changes.
        // Reach this visible edge candidate through its real D-pad intent loop.
        compose.onNodeWithTag(tag(0)).performKeyInput {
            repeat(target) { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        }
        awaitFocus(target)
        compose.waitUntil(10_000) { memory.focus?.index == target && memory.focus?.expanded == true }
        compose.waitForIdle()
        val after = compose.onNodeWithTag(tag(target)).getUnclippedBoundsInRoot()
        assertTrue("Edge expansion clipped: $before -> $after / $viewport", after.left >= viewport.left && after.right <= viewport.right)
        assertEquals(NotflixVisualSpec.EXPANDED_WIDTH.toFloat(), width(target), 0.01f)
        assertEquals(1f, scale(target), 0f)
        compose.onNodeWithText("Motion fixture information $target").assertExists()
        compose.onNodeWithTag(tag(target)).assertIsFocused()
        android.util.Log.i("NF_M7_EDGE", "item=$target before=$before after=$after viewport=$viewport")
    }

    @Test fun clockDistinguishesNormalInterpolationFromReducedSnapWithoutLosingIntentFocusOrContent() {
        show()
        awaitFocus(0)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag(tag(0)).performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
            val normal = traceUntilExpanded(1)
            assertTrue("Normal must render an intermediate scale: $normal", normal.any { it.scale > 1f && it.scale < 1.025f })
            assertTrue("Normal must render an intermediate width: $normal", normal.any { it.width > NotflixVisualSpec.POSTER_WIDTH.toFloat() && it.width < NotflixVisualSpec.EXPANDED_WIDTH.toFloat() })
            assertTrue("Intent delay shortened: $normal", normal.first { it.width > NotflixVisualSpec.POSTER_WIDTH.toFloat() }.elapsed >= NotflixVisualSpec.EXPAND_DELAY_MS - 16)
            assertEquals(NotflixVisualSpec.EXPANDED_WIDTH.toFloat(), width(1), 0.01f)
            compose.runOnUiThread { reduced.value = true }
            advance(32)
            compose.onNodeWithTag(tag(1)).assertIsFocused()
            assertEquals(NotflixVisualSpec.EXPANDED_WIDTH.toFloat(), width(1), 0.01f)
            assertEquals(1f, scale(1), 0f)
            compose.onNodeWithTag(tag(1)).performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
            val reducedFrames = traceUntilExpanded(2)
            assertTrue("Reduced must never zoom: $reducedFrames", reducedFrames.all { it.scale == 1f })
            assertTrue("Reduced must never interpolate width: $reducedFrames", reducedFrames.all { it.width == NotflixVisualSpec.POSTER_WIDTH.toFloat() || it.width == NotflixVisualSpec.EXPANDED_WIDTH.toFloat() })
            assertTrue("Reduced lost intent delay: $reducedFrames", reducedFrames.first { it.width > NotflixVisualSpec.POSTER_WIDTH.toFloat() }.elapsed >= NotflixVisualSpec.EXPAND_DELAY_MS - 16)
            assertEquals(NotflixVisualSpec.EXPANDED_WIDTH.toFloat(), width(2), 0.01f) // no intermediate width, same T1 result
            compose.onNodeWithTag(tag(2)).assertIsFocused()
            compose.onNodeWithText("Motion fixture information 2").assertExists()
            compose.runOnUiThread { reduced.value = false }
            advance(250)
            assertEquals(1.025f, scale(2), 0.0001f)
            assertEquals(NotflixVisualSpec.EXPANDED_WIDTH.toFloat(), width(2), 0.01f)
            compose.onNodeWithTag(tag(2)).assertIsFocused().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
            compose.runOnUiThread { assertEquals(listOf("item2"), details); assertEquals(1, mounts); assertEquals(0, disposals) }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun reducedKeepsRapidAndHeldInputLongActionAndExpandedCardInsideRightEdge() {
        reduced.value = true
        show()
        awaitFocus(0)
        compose.onNodeWithTag(tag(0)).performKeyInput { repeat(5) { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) } }
        awaitFocus(5)
        compose.mainClock.autoAdvance = false
        var middle = -1
        var beforeRelease = -1
        try {
            nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            for (repeat in 1..12) {
                advance(32)
                nativeDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat, long = repeat == 1)
                if (repeat == 6) compose.runOnUiThread { middle = memory.focus?.index ?: -1 }
            }
            compose.runOnUiThread { beforeRelease = memory.focus?.index ?: -1 }
        } finally { nativeUp(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); compose.mainClock.autoAdvance = true }
        assertTrue("Held input stalled: $middle", middle > 5)
        assertTrue("No further progress while held: $beforeRelease", beforeRelease > middle)
        awaitFocus(18)
        compose.waitUntil(10_000) { memory.focus?.expanded == true }
        compose.waitForIdle()
        val card = compose.onNodeWithTag(tag(18)).getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("notflix-home").getUnclippedBoundsInRoot()
        assertTrue("Expanded card clipped: $card / $viewport", card.left >= viewport.left && card.right <= viewport.right)
        assertEquals(1f, scale(18), 0f)
        assertEquals(NotflixVisualSpec.EXPANDED_WIDTH.toFloat(), width(18), 0.01f)
        compose.onNodeWithTag(tag(18)).assertIsFocused().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        nativeDown(android.view.KeyEvent.KEYCODE_DPAD_CENTER, 1, long = true)
        nativeUp(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(listOf("item18"), details); assertEquals(listOf("item18"), options) }
    }
}

