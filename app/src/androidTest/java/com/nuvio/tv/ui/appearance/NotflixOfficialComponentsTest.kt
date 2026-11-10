package com.nuvio.tv.ui.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.core.streams.StreamBadgePlacement
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.screens.detail.EpisodesRow
import com.nuvio.tv.ui.screens.detail.SeasonTabs
import com.nuvio.tv.ui.screens.stream.PlayerChoiceDialog
import com.nuvio.tv.ui.screens.stream.StreamCard
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Explicit fixtures exercise the actual season/episode/source/dialog components; no playback. */
@OptIn(ExperimentalTestApi::class)
class NotflixOfficialComponentsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: android.view.View
    private var time = 0L
    private fun down(code: Int, repeat: Int = 0, long: Boolean = false) {
        if (repeat == 0) time = android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread { view.dispatchKeyEvent(android.view.KeyEvent(time, android.os.SystemClock.uptimeMillis(),
            android.view.KeyEvent.ACTION_DOWN, code, repeat, 0, -1, 0, if (long) android.view.KeyEvent.FLAG_LONG_PRESS else 0)) }
    }
    private fun up(code: Int) = compose.runOnUiThread { view.dispatchKeyEvent(android.view.KeyEvent(time,
        android.os.SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, code, 0)) }
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun realSeasonsAndEpisodesKeepOptionsAndSpecificEpisodeReturnUnderOwnPalette() {
        val season = mutableIntStateOf(1)
        val tab = FocusRequester()
        val requests = mutableMapOf<String, FocusRequester>()
        var focused = ""
        val played = mutableListOf<String>()
        val watched = mutableListOf<String>()
        compose.setContent {
            view = LocalView.current
            NuvioTheme {
                NotflixPresentationTheme(true) {
                    val current = season.intValue
                    val episodes = remember(current) { listOf(Video("fixture-$current-a", "Fixture chapter A", null, null,
                        season = current, episode = 1, overview = "Declared test episode", available = true),
                        Video("fixture-$current-b", "Fixture chapter B", null, null,
                            season = current, episode = 2, overview = null, available = true)) }
                    Column(Modifier.fillMaxSize()) {
                        SeasonTabs(listOf(1, 2), current, { season.intValue = it }, selectedTabFocusRequester = tab)
                        EpisodesRow(episodes, onEpisodeClick = { played += it.id }, onToggleEpisodeWatched = { watched += it.id },
                            selectedSeason = current, upFocusRequester = tab, episodeFocusRequesters = requests,
                            onEpisodeFocused = { focused = it })
                    }
                }
            }
        }
        compose.runOnIdle { tab.requestFocus() }
        down(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); up(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitUntil(10_000) { season.intValue == 2 && requests.containsKey("fixture-2-a") }
        compose.runOnIdle { requests.getValue("fixture-2-a").requestFocus() }
        compose.waitUntil(10_000) { focused == "fixture-2-a" }
        down(android.view.KeyEvent.KEYCODE_DPAD_CENTER); up(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(listOf("fixture-2-a"), played); played.clear() }
        down(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        down(android.view.KeyEvent.KEYCODE_DPAD_CENTER, 1, true)
        up(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText(label(R.string.episodes_mark_watched)).assertExists()
        compose.runOnIdle { assertTrue(played.isEmpty()); assertTrue(watched.isEmpty()) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(label(R.string.episodes_mark_watched)).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) { focused == "fixture-2-a" }
        compose.onNodeWithContentDescription("Fixture chapter A").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, season.intValue); assertTrue(played.isEmpty()); assertTrue(watched.isEmpty()) }
    }

    @Test fun realSourceCardAndPlayerChoiceDialogRetainActionAndDismissWithoutPlayback() {
        val focus = FocusRequester()
        val showChoice = mutableStateOf(false)
        var sourceFocused = false
        var selections = 0
        var dismissed = 0
        var playerActions = 0
        val stream = Stream(name = "Fixture source", title = "Declared fixture · no playback", description = null,
            url = "https://example.invalid/fixture.mp4", ytId = null, infoHash = null, fileIdx = null,
            externalUrl = null, behaviorHints = null, addonName = "Fixture addon", addonLogo = null)
        compose.setContent {
            view = LocalView.current
            NuvioTheme {
                NotflixPresentationTheme(true) {
                    StreamCard(stream, showFileSizeBadges = false, showAddonLogo = false,
                        badgePlacement = StreamBadgePlacement.BOTTOM, focusRequester = focus,
                        onFocusChanged = { sourceFocused = it }, onClick = { selections++; showChoice.value = true })
                    if (showChoice.value) PlayerChoiceDialog(
                        onInternalSelected = { playerActions++ }, onExternalSelected = { playerActions++ },
                        onDismiss = { dismissed++; showChoice.value = false })
                }
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        compose.waitUntil(10_000) { sourceFocused }
        down(android.view.KeyEvent.KEYCODE_DPAD_CENTER); up(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText(label(R.string.stream_player_picker_title)).assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !showChoice.value }
        compose.onNodeWithText("Fixture source").assertExists()
        compose.runOnIdle { assertTrue(sourceFocused); assertEquals(1, selections); assertEquals(1, dismissed); assertEquals(0, playerActions) }
    }
}
