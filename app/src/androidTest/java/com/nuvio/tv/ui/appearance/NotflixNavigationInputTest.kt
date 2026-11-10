package com.nuvio.tv.ui.appearance

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixNavigationInputTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NotflixNavigationState()
    private val actions = mutableListOf<String>()
    private lateinit var view: android.view.View
    private lateinit var back: OnBackPressedDispatcher
    private var held: MutableState<Boolean>? = null
    private var time = 0L
    private val routes = listOf("home", "discover", "search", "library", "settings")
    private fun show(direction: State<LayoutDirection> = mutableStateOf(LayoutDirection.Ltr)) {
        compose.setContent {
            view = LocalView.current
            back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            val contentFocus = remember { FocusRequester() }
            val held = remember { mutableStateOf(false) }.also { this.held = it }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                NuvioTheme {
                    NotflixPresentationTheme(true) {
                        val shell = rememberNotflixNavigationShell(navigation, "home", "home", routes.toSet(), "home",
                            routes.zip(listOf("Inicio", "Descubrir", "Buscar", "Biblioteca", "Ajustes")).map { NotflixDestination(it.first, it.second) },
                            contentFocus, "NF-M3 · Perfil de laboratorio con nombre largo", { actions += "profile" },
                            { actions += it }, { actions += "exit" }, held)
                        PresentationShellHost(shell, contentFocus) {
                            Box(Modifier.fillMaxSize().focusRequester(contentFocus).focusable())
                        }
                    }
                }
            }
        }
    }
    private fun native(code: Int, action: Int, repeat: Int = 0, long: Boolean = false) {
        if (action == android.view.KeyEvent.ACTION_DOWN && repeat == 0) time = android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread {
            view.dispatchKeyEvent(android.view.KeyEvent(time, android.os.SystemClock.uptimeMillis(), action, code, repeat,
                0, -1, 0, if (long) android.view.KeyEvent.FLAG_LONG_PRESS else 0))
        }
    }
    @Test fun heldBarMovesBeforeReleaseAndOnlyOkNavigates() {
        show()
        compose.onNodeWithTag("notflix-destination-home").requestFocus()
        native(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.ACTION_DOWN)
        compose.onNodeWithTag("notflix-destination-discover").assertIsFocused()
        native(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.ACTION_DOWN, 1, true)
        compose.onNodeWithTag("notflix-destination-search").assertIsFocused()
        native(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.ACTION_UP)
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("notflix-destination-search").performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("search"), actions) }
    }
    @Test fun allOfficialTabsAndLongProfileFitInLtrAndRtlWithCorrectArrowOrder() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        show(direction)
        for (rtl in listOf(false, true)) {
            compose.runOnIdle { direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr }
            val viewport = compose.onRoot().getUnclippedBoundsInRoot()
            for (route in listOf("profile") + routes) {
                val node = compose.onNodeWithTag("notflix-destination-$route").requestFocus().assertIsFocused()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue("Focused $route clipped in $direction: $bounds / $viewport", bounds.left >= viewport.left && bounds.right <= viewport.right)
            }
            compose.onNodeWithTag("notflix-destination-home").requestFocus()
                .performKeyInput { val key = if (rtl) Key.DirectionLeft else Key.DirectionRight; keyDown(key); keyUp(key) }
            compose.onNodeWithTag("notflix-destination-discover").assertIsFocused()
        }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun longBackAndRepeatsDoNotExitThenShortBackUsesOneOfficialExit() {
        show()
        compose.onNodeWithTag("notflix-destination-search").requestFocus()
        native(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.ACTION_DOWN)
        native(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.ACTION_DOWN, 1, true)
        native(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.ACTION_DOWN, 2, true)
        native(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.ACTION_UP)
        compose.runOnIdle { assertTrue(actions.isEmpty()); assertFalse(held!!.value) }
        compose.onNodeWithTag("notflix-destination-home").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.runOnIdle { assertEquals(listOf("exit"), actions) }
    }
}
