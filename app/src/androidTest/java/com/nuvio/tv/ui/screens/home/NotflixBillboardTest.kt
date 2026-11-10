package com.nuvio.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.ambient.AmbientIdleController
import com.nuvio.tv.ui.screens.ambient.LocalAmbientIdleController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Controlled Compose clock and synthetic data; no network, video or account state. */
@OptIn(ExperimentalTestApi::class)
class NotflixBillboardTest {
    @get:Rule val compose = createComposeRule()
    private fun item(id: Int) = MetaPreview("fixture:$id", if (id % 2 == 0) ContentType.MOVIE else ContentType.SERIES,
        name = "Fixture $id", poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = "2026", imdbRating = null, genres = emptyList())
    private var items by mutableStateOf((0..5).map(::item))
    private var key by mutableStateOf("movie:fixture:0")
    private var previous = 0
    private var eligible by mutableStateOf(true)
    private var reduced by mutableStateOf(false)
    private var binding by mutableStateOf(PreviewBinding(AppearanceContext(2, 1), 1))
    private var epoch by mutableLongStateOf(0)
    private var live = true
    private var frameProgress: () -> Float = { 0f }
    private var frameScale: () -> Float = { 1f }
    private var advances = 0
    private var infoActivations = 0
    private val ambient = AmbientIdleController()
    private lateinit var view: android.view.View
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun progress() = frameProgress()
    private fun focus(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    private fun key(tag: String, value: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(value); keyUp(value) }
    private fun standalone() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            NuvioTheme {
                val header = remember { FocusRequester() }; val info = remember { FocusRequester() }; val play = remember { FocusRequester() }
                CompositionLocalProvider(LocalAmbientIdleController provides ambient,
                    LocalBillboardFrameObserver provides { progress, scale -> frameProgress = progress; frameScale = scale }) {
                Column {
                    Text("Home header", Modifier.focusRequester(header).clickable {}.testTag("header"))
                    val heroes = notflixBillboardItems(items)
                    val index = notflixBillboardIndex(heroes, key, previous)
                    val selected = heroes.getOrNull(index)
                    if (selected == null) Text("No official hero") else NotflixBillboard(selected, index, heroes.size, binding, reduced,
                        eligible, epoch, info, play, liveAllowed = { live },
                        onAdvance = { advances++; previous = (index + 1) % heroes.size; key = notflixMetaKey(heroes[previous]) },
                        onSelect = { previous = it; key = notflixMetaKey(heroes[it]); epoch++ },
                        onInteraction = {}, onFocus = {}, onKey = { _, direction -> if (direction == Key.DirectionUp) header.requestFocus() },
                        onInfo = { infoActivations++ }, onPlay = {})
                }
                }
                LaunchedEffect(Unit) { header.requestFocus() }
            }
        }
        step(80)
    }
    @Test fun idleNormalFillsMovesAndAdvancesOneTitleWithoutFocusTheft() {
        standalone()
        compose.onNodeWithTag("header").assertIsFocused()
        step(2000)
        assertTrue(progress() in .19f.. .23f)
        val scale = frameScale()
        assertTrue(scale > 1.02f)
        step(8200)
        assertEquals(1, advances); assertEquals("series:fixture:1", key)
        compose.onNodeWithTag("header").assertIsFocused()
    }
    @Test fun ctaFocusFreezesVisibleTimeAndManualChoiceRestartsWholePeriod() {
        standalone();step(2000)
        focus("notflix-editorial-details");step(32)
        val paused = progress();step(30_000)
        assertEquals(0, advances);assertEquals(paused, progress(), .002f)
        focus("header");step(32);step(3000)
        assertTrue(progress() > paused + .28f)
        compose.onNodeWithTag("notflix-billboard-select-2").performClick();step(32)
        assertEquals("movie:fixture:2", key);assertTrue(progress() < .02f)
        step(9000);assertEquals(0, advances)
        step(1200);assertEquals(1, advances)
    }
    @Test fun blockedVisitAndNewConfirmationDiscardElapsedTimeWithoutCatchUp() {
        standalone();step(9000)
        compose.runOnUiThread { eligible = false };step(32);step(40_000)
        assertEquals(0, advances);assertEquals(0f, progress(), 0f)
        compose.runOnUiThread { eligible = true };step(2000)
        assertTrue(progress() < .21f)
        compose.runOnUiThread { binding = PreviewBinding(AppearanceContext(3, 2), 2) };step(32)
        assertTrue(progress() < .01f)
        compose.runOnUiThread { ambient.show() };step(1000)
        assertEquals(0f, progress(), 0f);assertEquals(0, advances)
        compose.runOnUiThread { ambient.dismiss() };step(500)
        assertTrue(progress() < .06f)
        live = false;step(1000)
        assertEquals(0, advances);assertEquals(0f, progress(), 0f)
    }
    @Test fun reducedAndZeroOrOneHeroNeverInventAnAutomaticCycle() {
        reduced = true;standalone();step(30_000)
        assertEquals(0, advances);assertEquals(0f, progress(), 0f)
        assertEquals(1f, frameScale(), 0f)
        compose.runOnUiThread { reduced = false;items = listOf(item(0)) };step(32);step(30_000)
        assertEquals(0, advances);compose.onAllNodesWithTag("notflix-billboard-select-0").assertCountEquals(0)
        compose.runOnUiThread { items = emptyList() };step(32)
        compose.onNodeWithText("No official hero").assertExists()
        compose.onAllNodesWithTag("notflix-editorial-play").assertCountEquals(0)
    }
    @Test fun sameIdentityEnrichmentDoesNotRestartPeriodAndRemovalRepairsSelection() {
        standalone();step(3000)
        compose.runOnUiThread { items = items.map { if (it.id == "fixture:0") it.copy(name = "Enriched same identity") else it } }
        step(1000)
        assertTrue(progress() > .38f);assertEquals(0, advances)
        compose.runOnUiThread { items = items.drop(1) };step(32)
        assertTrue(progress() < .02f) // Reset is immediate even while the outgoing copy leaves.
        step(400)
        compose.onNodeWithText("Fixture 1").assertExists()
        compose.onNodeWithText("Enriched same identity").assertDoesNotExist()
    }
    @Test fun heldCenterIsCancelledWhenManualSelectionChangesIdentity() {
        standalone();focus("notflix-editorial-details");step(32)
        val at = android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread { view.dispatchKeyEvent(android.view.KeyEvent(at,at,android.view.KeyEvent.ACTION_DOWN,23,0)) }
        compose.onNodeWithTag("notflix-billboard-select-1").performClick();step(32)
        compose.runOnUiThread { view.dispatchKeyEvent(android.view.KeyEvent(at,at+50,android.view.KeyEvent.ACTION_UP,23,0)) }
        step(32);assertEquals(0, infoActivations)
        step(400) // A fresh press is accepted only after the sequential copy transition.
        key("notflix-editorial-details",Key.DirectionCenter);step(32)
        assertEquals(1, infoActivations)
    }
    @Test fun fullHomeRoutesDispatchOnceForDownRepeatUpAndRestoreTitleAndCta() {
        compose.mainClock.autoAdvance = false
        val memory = NotflixHomeMemory();val navigation = NotflixNavigationState()
        val actions = mutableListOf<String>()
        var destinationPlay: String? = null;var destinationManual: String? = null
        var introFinished by mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            val scope = rememberCoroutineScope()
            val session = remember {
                NotflixPreviewSession(binding, NotflixPreviewCoordinator(scope, object : NotflixPreviewResolver {
                    override suspend fun resolve(lease: PreviewLease, authorized: () -> Boolean): PreviewSource? = error("No preview expected")
                    override fun cancel(lease: PreviewLease) {}
                }), object : NotflixPreviewRenderer {
                    @Composable override fun Content(phase: PreviewPhase.Playing, coordinator: NotflixPreviewCoordinator, modifier: Modifier, startPositionMs: Long) { error("No video expected") }
                })
            }
            val readyAfterIntro = introFinished
            SideEffect { session.environment(PreviewEnvironment(binding, enabled = false, valid = true, reduced = false,
                resumed = true, windowFocused = true, introActive = !readyAfterIntro)) }
            DisposableEffect(session) { onDispose { session.dispose() } }
            val nav = rememberNavController()
            NuvioTheme {
                NavHost(nav, startDestination = "home", enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None }) {
                    composable("home") {
                        val requester = remember { FocusRequester() };val held = remember { mutableStateOf(false) }
                        val shell = rememberNotflixNavigationShell(navigation,"home","home",setOf("home"),"home",
                            listOf(NotflixDestination("home","Home")),requester,null,{}, {}, {},held)
                        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true,navigation,false)) {
                            PresentationShellHost(shell,requester) {
                                NotflixHomeContent(HomeUiState(layoutPreferencesReady = true,isLoading = false,
                                    notflixCatalog = NotflixCatalogSnapshot(2,1,true,heroItems = items)),memory,navigation = navigation,previewSession = session,
                                    onDetail = { id,type,base -> actions += "info:$id";nav.navigate(Screen.Detail.createRoute(id,type,base)) },
                                    onPlay = { id,type,base -> actions += "play:$id";nav.navigate(notflixBillboardPlayRoute(id,type,base,true)) { launchSingleTop = true } },
                                    onContinue = {},onFolder = { _,_-> },onVisibleRow = {},onRetry = {})
                            }
                        }
                    }
                    composable(Screen.Detail.route) { entry ->
                        destinationPlay = entry.arguments?.getString("playOnLoad")
                        destinationManual = entry.arguments?.getString("manualSelection")
                        BackHandler { nav.popBackStack() }
                        Text("Inert official Detail route")
                    }
                }
            }
        }
        step(160)
        // The prepared destination retains its CTA while intro/host visibility blocks idle entry.
        compose.onNodeWithTag("notflix-editorial-play").assertIsFocused()
        compose.runOnUiThread { introFinished = true };step(160)
        compose.onNodeWithTag("notflix-destination-home").assertIsFocused()
        key("notflix-destination-home",Key.DirectionDown);step(200)
        compose.onNodeWithTag("notflix-editorial-play").assertIsFocused()
        key("notflix-editorial-play",Key.DirectionRight);step(32)
        compose.onNodeWithTag("notflix-editorial-details").assertIsFocused()
        fun centerRepeat() {
            val at = android.os.SystemClock.uptimeMillis()
            compose.runOnUiThread {
                view.dispatchKeyEvent(android.view.KeyEvent(at,at,android.view.KeyEvent.ACTION_DOWN,23,0))
                view.dispatchKeyEvent(android.view.KeyEvent(at,at+20,android.view.KeyEvent.ACTION_DOWN,23,1))
                view.dispatchKeyEvent(android.view.KeyEvent(at,at+40,android.view.KeyEvent.ACTION_UP,23,0))
            }
            step(160)
        }
        fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(4);step(240) }
        centerRepeat();compose.onNodeWithText("Inert official Detail route").assertExists()
        assertEquals(listOf("info:fixture:0"),actions);assertEquals("false",destinationPlay)
        back();compose.onNodeWithTag("notflix-editorial-details").assertIsFocused()
        key("notflix-editorial-details",Key.DirectionLeft);step(32)
        centerRepeat();assertEquals(listOf("info:fixture:0","play:fixture:0"),actions)
        assertEquals("true",destinationPlay);assertEquals("true",destinationManual)
        back();compose.onNodeWithTag("notflix-editorial-play").assertIsFocused()
        assertEquals("movie:fixture:0",memory.editorialKey)
    }
}