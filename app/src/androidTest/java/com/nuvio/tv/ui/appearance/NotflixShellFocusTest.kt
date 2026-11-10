package com.nuvio.tv.ui.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import org.junit.Rule
import org.junit.Test

/** M29 bug found on the promoter's Xiaomi: Down from the top bar was lost on every root screen except Home. */
@OptIn(ExperimentalTestApi::class)
class NotflixShellFocusTest {
    @get:Rule val compose = createComposeRule()
    private val routes = listOf("home", "search", "library", "settings")

    private fun show(route: String, nav: NotflixNavigationState) {
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true, nav, false)) {
                NotflixPresentationTheme(true) {
                    val focus = remember { FocusRequester() }; val held = remember { mutableStateOf(false) }
                    val shell = rememberNotflixNavigationShell(nav, route, route, routes.toSet(), "home",
                        routes.map { NotflixDestination(it, it) }, focus, null, {}, {}, {}, held)
                    PresentationShellHost(shell, focus) {
                        // Synthetic official-like screen: no LocalContentFocusRequester of its own, like Search/Library/Settings.
                        Column {
                            Button(onClick = {}, modifier = Modifier.testTag("content-first")) { Text("First") }
                            Button(onClick = {}, modifier = Modifier.testTag("content-second")) { Text("Second") }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun downFromTheBarReturnsToTheLastFocusedElementOfANonHomeScreen() {
        val nav = NotflixNavigationState()
        show("search", nav)
        compose.onNodeWithTag("content-second").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("content-second").assertIsFocused()
        compose.runOnIdle { nav.focusTop() }; compose.waitForIdle()
        val bar = compose.onNodeWithTag("notflix-destination-search")
        bar.assertIsFocused()
        // The promoter's exact path: move along the bar, then press Down.
        bar.performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("notflix-destination-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("notflix-destination-search").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("content-second").assertIsFocused()
    }

    @Test fun settingsReceivesDownFromTheBar() {
        val nav = NotflixNavigationState()
        show("settings", nav)
        compose.runOnIdle { nav.focusTop() }; compose.waitForIdle()
        compose.onNodeWithTag("notflix-destination-settings").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("content-first").assertIsFocused()
    }

    @Test fun libraryReceivesDownFromTheBar() {
        val nav = NotflixNavigationState()
        show("library", nav)
        compose.runOnIdle { nav.focusTop() }; compose.waitForIdle()
        compose.onNodeWithTag("notflix-destination-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("content-first").assertIsFocused()
    }
}
