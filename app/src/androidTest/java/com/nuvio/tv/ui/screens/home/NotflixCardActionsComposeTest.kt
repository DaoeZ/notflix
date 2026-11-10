package com.nuvio.tv.ui.screens.home

import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production card/rows/coordinator with a recording host instead of HomeViewModel; no library, history or network is touched. */
@OptIn(ExperimentalTestApi::class)
class NotflixCardActionsComposeTest {
    @get:Rule val compose = createComposeRule()

    private class Host {
        var status by mutableStateOf(NotflixCardStatus(inLibrary = false, watched = false))
        var manage = false
        val expanded = mutableListOf<String>(); val library = mutableListOf<String>(); val watched = mutableListOf<String>()
        val plays = mutableListOf<String>()
    }

    private fun PreviewFixtureState.withRuntime(runtime: String?) = apply {
        val timed = rows.map { row -> row.copy(catalog = row.catalog!!.copy(items = row.catalog!!.items.map { it.copy(runtime = runtime) })) }
        state = state.copy(notflixCatalog = state.notflixCatalog.copy(rows = timed))
    }

    @Composable
    private fun Fixture(s: PreviewFixtureState, h: Host) {
        s.view = LocalView.current
        val scope = rememberCoroutineScope()
        val session = remember(s) { NotflixPreviewSession(s.binding, NotflixPreviewCoordinator(scope, s.resolver), s.renderer, { s.liveBlocked }) }
        s.session = session
        SideEffect { session.environment(s.environment) }
        DisposableEffect(session) { onDispose { session.dispose() } }
        val base = LocalContext.current
        val context = remember(s.spanish) { if (s.spanish) base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale("es")) }) else base }
        val host = remember(h) {
            NotflixCardActionsHost(status = { h.status }, manageLists = h.manage, onExpanded = { h.expanded += it.id },
                onLibrary = { meta, _ -> h.library += meta.id }, onWatched = { meta -> h.watched += meta.id })
        }
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
            LocalLifecycleOwner provides s.lifecycle, LocalNotflixPresentation provides NotflixPresentation(true, s.navigation, s.environment.reduced)) {
            NotflixPresentationTheme(true) {
                val focus = remember { FocusRequester() }; val held = remember { mutableStateOf(false) }
                val shell = rememberNotflixNavigationShell(s.navigation, if (s.destination == null) "home" else "detail", "home", setOf("home", "search", "library", "settings"), "home",
                    listOf("home", "search", "library", "settings").map { NotflixDestination(it, it) }, focus, "Fixture", {}, { s.navigated += it }, {}, held)
                PresentationShellHost(shell, focus) {
                    CompositionLocalProvider(LocalContentFocusRequester provides focus) {
                        if (s.destination == null) NotflixHomeContent(s.state, s.memory, navigation = s.navigation,
                            onDetail = { id, _, _ -> s.details += id; s.destination = id },
                            onPlay = { id, _, _ -> h.plays += id; s.destination = "play:$id" },
                            onContinue = {}, onFolder = { _, _ -> }, onVisibleRow = {},
                            onLongPress = { meta, _ -> s.holds += meta.id }, previewSession = session, cardActions = host, onRetry = {})
                        else {
                            val back = remember { FocusRequester() }
                            Button(onClick = { s.destination = null }, modifier = Modifier.focusRequester(back).testTag("actions-fixture-back")) { Text("Return synthetic destination") }
                            BackHandler { s.destination = null }; LaunchedEffect(Unit) { back.requestFocus() }
                        }
                    }
                }
            }
        }
    }

    private fun show(s: PreviewFixtureState, h: Host = Host()): Host { compose.setContent { Fixture(s, h) }; compose.waitForIdle(); return h }
    private fun card(s: PreviewFixtureState, row: Int = 0, index: Int = 0) = compose.onNodeWithTag(s.tag(row, index))
    private fun key(s: PreviewFixtureState, key: Key, row: Int = 0, index: Int = 0) { card(s, row, index).performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun expanded(s: PreviewFixtureState) { compose.waitUntil(10_000) { s.memory.focus?.expanded == true }; compose.waitForIdle() }
    private fun mounted(s: PreviewFixtureState) { compose.waitUntil(10_000) { s.fake.mounts.size == 1 }; compose.waitForIdle() }
    private fun action(s: PreviewFixtureState, name: String, row: Int = 0, index: Int = 0) =
        compose.onNodeWithTag("notflix-card-action-${s.identity(row, index)}-$name", useUnmergedTree = true)
    private fun label(s: PreviewFixtureState, index: Int = 0) =
        compose.onNodeWithTag("notflix-card-action-label-${s.identity(0, index)}", useUnmergedTree = true)
    private fun state(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
    private fun native(s: PreviewFixtureState, code: Int, action: Int, repeat: Int = 0) {
        compose.runOnUiThread { val now = SystemClock.uptimeMillis(); s.view.dispatchKeyEvent(KeyEvent(now, now, action, code, repeat)) }
        compose.waitForIdle()
    }

    @Test fun remoteWalksActionsWhileCardKeepsFocusExpansionAndOnePreviewLease() {
        val s = PreviewFixtureState(); val h = show(s); mounted(s)
        key(s, Key.DirectionDown)
        card(s).assertIsFocused(); action(s, "play").assertIsSelected(); label(s).assertTextEquals("Play")
        repeat(6) { key(s, Key.DirectionRight) }
        action(s, "sound").assertIsSelected(); action(s, "play").assertIsNotSelected()
        repeat(9) { key(s, Key.DirectionLeft) }
        action(s, "play").assertIsSelected()
        key(s, Key.DirectionUp)
        label(s).assertDoesNotExist(); compose.onNodeWithTag("notflix-card-facts-${s.identity()}", true).assertExists()
        card(s).assertIsFocused()
        compose.runOnIdle {
            assertEquals(1, s.fake.mounts.size); assertEquals(0, s.fake.closes); assertEquals(1, s.resolver.calls.size)
            assertTrue(s.memory.focus!!.expanded); assertEquals(listOf("same"), h.expanded.distinct())
            assertTrue(h.plays.isEmpty() && s.details.isEmpty() && h.library.isEmpty() && h.watched.isEmpty())
        }
        // Down inside the actions leaves to the next row with the same column; the old owner closes once.
        key(s, Key.DirectionDown); key(s, Key.DirectionDown)
        compose.waitUntil(10_000) { runCatching { card(s, 1).assertIsFocused(); true }.getOrDefault(false) }
        compose.runOnIdle { assertEquals(1, s.fake.closes) }
    }

    @Test fun centerDispatchesEachOfficialCallbackOnceAndPlayReturnsToTheSameTitle() {
        val s = PreviewFixtureState(); val h = show(s); mounted(s)
        key(s, Key.DirectionDown); key(s, Key.DirectionRight); key(s, Key.DirectionRight)
        action(s, "library").assertIsSelected()
        native(s, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN); native(s, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 1)
        native(s, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 2); native(s, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP)
        key(s, Key.DirectionRight); key(s, Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("same"), h.library); assertEquals(listOf("same"), h.watched); assertTrue(s.holds.isEmpty() && s.details.isEmpty()) }
        repeat(4) { key(s, Key.DirectionLeft) }; key(s, Key.DirectionCenter)
        compose.onNodeWithTag("actions-fixture-back").assertIsFocused()
        compose.runOnIdle { assertEquals(listOf("same"), h.plays); assertTrue(s.details.isEmpty()); assertEquals("same", s.memory.focus?.canonicalKey?.substringAfter(':')) }
        compose.onNodeWithTag("actions-fixture-back").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { runCatching { card(s).assertIsFocused(); true }.getOrDefault(false) }
        // Back on the card itself: card focus, actions closed, no second play.
        compose.runOnIdle { assertEquals(listOf("same"), h.plays) }
    }

    @Test fun pressAndReleaseInTheSameFrameStillActivatesOnce() {
        // A real remote/adb can deliver down and up before any recomposition (found in the native demo).
        val s = PreviewFixtureState().apply { environment = environment.copy(enabled = false) }; show(s); expanded(s)
        key(s, Key.DirectionDown); key(s, Key.DirectionRight); action(s, "info").assertIsSelected()
        compose.runOnUiThread {
            val now = SystemClock.uptimeMillis()
            s.view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            s.view.dispatchKeyEvent(KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("same"), s.details) }
    }

    @Test fun unknownOrPendingOfficialStateIsNeverPresentedAsConfirmed() {
        val s = PreviewFixtureState().apply { environment = environment.copy(enabled = false) }
        val h = Host().apply { status = NotflixCardStatus(inLibrary = null, watched = null) }
        show(s, h); expanded(s); key(s, Key.DirectionDown)
        assertEquals("Checking library", state(action(s, "library"))); action(s, "library").assertIsNotEnabled()
        // Promoter 08-11: unknown watched state behaves as unwatched (Nuvio poster menu parity) and stays usable.
        assertEquals("Not watched", state(action(s, "watched"))); action(s, "watched").assertIsEnabled()
        key(s, Key.DirectionRight); key(s, Key.DirectionRight); key(s, Key.DirectionCenter); key(s, Key.DirectionRight); key(s, Key.DirectionCenter)
        compose.runOnIdle { assertTrue(h.library.isEmpty()); assertEquals(1, h.watched.size) }
        compose.runOnIdle { h.status = NotflixCardStatus(inLibrary = true, libraryPending = true, watched = true, watchedPending = true) }; compose.waitForIdle()
        assertEquals("Updating…", state(action(s, "library"))); action(s, "library").assertIsNotEnabled()
        compose.runOnIdle { h.status = NotflixCardStatus(inLibrary = true, watched = true) }; compose.waitForIdle()
        assertEquals("In library", state(action(s, "library"))); action(s, "library").assertIsEnabled()
        action(s, "library").assertContentDescriptionEquals("Remove from library")
        action(s, "watched").assertContentDescriptionEquals("Mark as unwatched"); assertEquals("Watched", state(action(s, "watched")))
        // No preview: the sound control is not drawn rather than shown as a fake toggle.
        action(s, "sound").assertDoesNotExist()
    }

    @Test fun soundToggleIsAVisitChoiceWithoutNewResolutionMountOrRestart() {
        val s = PreviewFixtureState(); show(s); mounted(s)
        key(s, Key.DirectionDown); repeat(4) { key(s, Key.DirectionRight) }
        action(s, "sound").assertIsSelected()
        val lease = requireNotNull(s.session.coordinator.state.value.lease())
        val silenced = action(s, "sound").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        key(s, Key.DirectionCenter)
        if (silenced) {
            assertEquals("Silenced by the system", state(action(s, "sound")))
            compose.runOnIdle { assertFalse(s.session.coordinator.audible(lease)) }
        } else {
            compose.runOnIdle { assertTrue(s.session.coordinator.audible(lease)) }
            action(s, "sound").assertContentDescriptionEquals("Mute"); assertEquals("Sound on", state(action(s, "sound")))
            key(s, Key.DirectionCenter); compose.runOnIdle { assertFalse(s.session.coordinator.audible(lease)) }
            key(s, Key.DirectionCenter)
        }
        compose.runOnIdle { assertEquals(1, s.resolver.calls.size); assertEquals(1, s.fake.mounts.size); assertEquals(0, s.fake.closes); assertTrue(s.session.coordinator.isCurrent(lease)) }
        // Leaving the title ends the visit; the next one starts again from the official profile value.
        key(s, Key.DirectionUp); key(s, Key.DirectionRight)
        compose.waitUntil(10_000) { s.fake.mounts.size == 2 }
        compose.runOnIdle { assertNull(s.session.coordinator.audibleOverride.value); assertFalse(s.session.coordinator.audible(s.fake.mounts[1])) }
    }

    @Test fun backInsideActionsReturnsToTheCardNotToTheFirstColumn() {
        val s = PreviewFixtureState().apply { environment = environment.copy(enabled = false) }; show(s); expanded(s)
        key(s, Key.DirectionRight); compose.waitUntil(10_000) { s.memory.focus?.index == 1 && s.memory.focus?.expanded == true }; compose.waitForIdle()
        key(s, Key.DirectionDown, index = 1); action(s, "play", index = 1).assertIsSelected()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle()
        card(s, 0, 1).assertIsFocused(); label(s, 1).assertDoesNotExist()
        compose.runOnIdle { assertFalse(s.navigation.topFocused) }
        // A second Back keeps the historic contract: first column of the row.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { runCatching { card(s, 0, 0).assertIsFocused(); true }.getOrDefault(false) }
    }

    @Test fun upReachesTheArtworkOnlyWhileThePreviewIsPlayingAndOpensTheBigView() {
        val s = PreviewFixtureState(); val h = show(s); mounted(s)
        key(s, Key.DirectionDown)
        key(s, Key.DirectionUp)
        label(s).assertDoesNotExist()
        compose.onNodeWithTag("notflix-card-facts-${s.identity()}", true).assertExists()
        key(s, Key.DirectionCenter)
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertExists()
        compose.onNodeWithTag("notflix-fullscreen-play", useUnmergedTree = true).assertIsFocused()
        compose.runOnIdle { assertTrue(h.plays.isEmpty()); assertTrue(s.details.isEmpty()) }
    }

    @Test fun backFromTheBigViewReturnsToTheSameCardAndRestartsTheMutedPreview() {
        val s = PreviewFixtureState(); val h = show(s); mounted(s)
        key(s, Key.DirectionDown); key(s, Key.DirectionUp); key(s, Key.DirectionCenter)
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle()
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertDoesNotExist()
        compose.waitUntil(10_000) { runCatching { card(s).assertIsFocused(); true }.getOrDefault(false) }
        compose.waitUntil(10_000) { s.fake.mounts.size == 2 }
        compose.onNodeWithTag("notflix-card-art-${s.identity()}", true).assertExists()
        compose.runOnIdle {
            assertEquals(1, s.fake.closes); assertTrue(s.session.ownsVideo)
            assertTrue(h.plays.isEmpty() && s.details.isEmpty())
        }
    }

    @Test fun playInTheBigViewDispatchesOnPlayOnceAndDoesNotRestoreTheTrailer() {
        val s = PreviewFixtureState(); val h = show(s); mounted(s)
        key(s, Key.DirectionDown); key(s, Key.DirectionUp); key(s, Key.DirectionCenter)
        compose.onNodeWithTag("notflix-fullscreen-play", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("actions-fixture-back").assertIsFocused(); true }.getOrDefault(false) }
        compose.onNodeWithTag("notflix-fullscreen-trailer").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("same"), h.plays)
            assertTrue(s.details.isEmpty())
            assertEquals(1, s.fake.mounts.size); assertEquals(1, s.fake.closes)
            assertFalse(s.session.ownsVideo)
        }
    }

    @Test fun englishFactsShowOfficialRuntimeAndEndsAt() {
        val en = PreviewFixtureState().apply { environment = environment.copy(enabled = false) }.withRuntime("126"); show(en); expanded(en)
        compose.onNodeWithTag("notflix-card-facts-${en.identity()}", true).assertTextEquals("2026 · 126 min")
        compose.onNodeWithTag("notflix-card-ends-${en.identity()}", true).assertTextContains("Ends at ", substring = true)
        // Narrow (first-row) card: the end time stays whole inside the panel.
        val ends = compose.onNodeWithTag("notflix-card-ends-${en.identity()}", true).getUnclippedBoundsInRoot()
        // Compare with the focused card (both under its 1.025 focus scale), not the panel's unscaled box.
        val cardBounds = card(en).getUnclippedBoundsInRoot()
        assertTrue("$ends inside $cardBounds", ends.right <= cardBounds.right && ends.left >= cardBounds.left)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("notflix-card-ends-${en.identity()}", true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse(layouts.single().hasVisualOverflow)
    }

    @Test fun spanishFactsAndActionNamesUseOfficialStrings() {
        val es = PreviewFixtureState().apply { environment = environment.copy(enabled = false); spanish = true }.withRuntime("2h 6min"); show(es); expanded(es)
        compose.onNodeWithTag("notflix-card-facts-${es.identity()}", true).assertTextEquals("2026 · 126 min")
        compose.onNodeWithTag("notflix-card-ends-${es.identity()}", true).assertTextContains("Termina a: ", substring = true)
        key(es, Key.DirectionDown); label(es).assertTextEquals("Reproducir")
        action(es, "library").assertContentDescriptionEquals("Añadir a biblioteca"); action(es, "info").assertContentDescriptionEquals("Más información")
        assertEquals("No visto", state(action(es, "watched")))
    }

    @Test fun invalidRuntimeOmitsDurationAndEndsAt() {
        val missing = PreviewFixtureState().apply { environment = environment.copy(enabled = false) }.withRuntime("0"); show(missing); expanded(missing)
        val facts = compose.onNodeWithTag("notflix-card-facts-${missing.identity()}", true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertEquals("2026", facts)
        compose.onNodeWithTag("notflix-card-ends-${missing.identity()}", true).assertDoesNotExist()
    }

    @Test fun reducedMotionKeepsStaticActionsWithoutSoundOrPreview() {
        val s = PreviewFixtureState().apply { environment = environment.copy(reduced = true) }; show(s); expanded(s)
        key(s, Key.DirectionDown); action(s, "play").assertIsSelected(); action(s, "sound").assertDoesNotExist()
        compose.runOnIdle { assertTrue(s.resolver.calls.isEmpty()); assertTrue(s.fake.mounts.isEmpty()) }
    }
}
