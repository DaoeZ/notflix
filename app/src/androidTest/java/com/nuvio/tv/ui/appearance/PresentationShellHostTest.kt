package com.nuvio.tv.ui.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.ui.navigation.rememberNavSessionViewModelStoreOwner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fake chrome only. Real Navigation/Compose and the production session store/host. */
@OptIn(ExperimentalTestApi::class)
class PresentationShellHostTest {
    @get:Rule val compose = createComposeRule()

    class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun savedOfficialDecisionRestoresWithoutSavingControllerOrCapability() {
        val restoration = StateRestorationTester(compose)
        lateinit var saved: MutableState<OfficialAppearanceDecision?>
        lateinit var controller: AppearanceSessionConfirmation
        restoration.setContent {
            saved = rememberSaveable(stateSaver = OfficialAppearanceDecisionSaver) { mutableStateOf(null) }
            controller = remember { AppearanceSessionConfirmation() }
            Text("decision restoration harness")
        }
        val decision = OfficialAppearanceDecision(7, 2, "guest", 3)
        val context = AppearanceSessionContext(
            com.nuvio.tv.domain.model.PersistedAppearanceProfile(2, 1),
            com.nuvio.tv.domain.model.AppearanceContext(2, 4)
        )
        val previous = controller
        compose.runOnIdle {
            saved.value = decision
            val request = requireNotNull(controller.begin(context))
            assertTrue(controller.confirm(request, context))
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(decision, saved.value)
            assertNotSame(previous, controller)
            assertNull(controller.authorized(context))
        }
    }

    @Test fun chromeRoundTripKeepsRouteStackStoreViewModelsAndFocus() {
        val mode = mutableIntStateOf(0)
        lateinit var nav: NavHostController
        lateinit var store: ViewModelStore
        lateinit var home: Probe
        lateinit var detail: Probe
        compose.setContent {
            nav = rememberNavController()
            val owner = rememberNavSessionViewModelStoreOwner(nav)
            SideEffect { store = owner.viewModelStore }
            val focus = remember { FocusRequester() }
            val shell = if (mode.intValue == 0) PresentationShell(chrome = { Text("harness lateral") })
                else PresentationShell(contentModifier = Modifier.padding(top = 12.dp),
                    chrome = { Text("harness superior") })
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                PresentationShellHost(shell, focus) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            val vm: Probe = viewModel()
                            SideEffect { home = vm }
                            Button(onClick = { nav.navigate("detail") }, modifier = Modifier.testTag("open")) { Text("Title") }
                        }
                        composable("detail") {
                            val vm: Probe = viewModel()
                            SideEffect { detail = vm }
                            Button(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("back")) { Text("Return") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("open").requestFocus().assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("back").requestFocus().assertIsFocused()
        val originalNav = nav
        val originalStore = store
        val originalHome = home
        val originalDetail = detail
        for (next in listOf(1, 0, 1)) {
            compose.runOnIdle { mode.intValue = next }
            compose.onNodeWithTag("back").assertIsFocused()
            compose.runOnIdle {
                assertSame(originalNav, nav)
                assertSame(originalStore, store)
                assertSame(originalHome, home)
                assertSame(originalDetail, detail)
                assertEquals("detail", nav.currentBackStackEntry?.destination?.route)
                assertEquals("home", nav.previousBackStackEntry?.destination?.route)
                assertFalse(home.cleared)
                assertFalse(detail.cleared)
            }
        }
        compose.onNodeWithTag("back").performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("open").requestFocus().assertIsFocused()
        compose.runOnIdle {
            assertEquals("home", nav.currentBackStackEntry?.destination?.route)
            assertSame(originalHome, home)
            assertFalse(home.cleared)
        }
    }

    @Test fun leavingOfficialProfileSessionClearsStoreAndNextSessionGetsNewViewModel() {
        val inSession = mutableStateOf(true)
        lateinit var probe: Probe
        lateinit var store: ViewModelStore
        compose.setContent {
            if (inSession.value) {
                val nav = rememberNavController()
                val owner = rememberNavSessionViewModelStoreOwner(nav)
                SideEffect { store = owner.viewModelStore }
                val focus = remember { FocusRequester() }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    PresentationShellHost(PresentationShell(), focus) {
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                val vm: Probe = viewModel()
                                SideEffect { probe = vm }
                                Column { Text("profile content", Modifier.testTag("session")) }
                            }
                        }
                    }
                }
            } else Text("official profile selector", Modifier.testTag("picker"))
        }
        compose.onNodeWithTag("session").assertExists()
        val previous = probe
        val previousStore = store
        compose.runOnIdle { inSession.value = false }
        compose.onNodeWithTag("picker").assertExists()
        compose.runOnIdle { assertTrue(previous.cleared); inSession.value = true }
        compose.onNodeWithTag("session").assertExists()
        compose.runOnIdle {
            assertNotSame(previous, probe)
            assertNotSame(previousStore, store)
            assertFalse(probe.cleared)
        }
    }
}
