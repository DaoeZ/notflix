package com.nuvio.tv.ui.appearance

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.R
import com.nuvio.tv.data.local.*
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.ui.navigation.rememberNavSessionViewModelStoreOwner
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import com.squareup.moshi.Moshi
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real store/VM; its directories are isolated inside the laboratory app's cache. */
@OptIn(ExperimentalTestApi::class)
class NotflixAppearanceIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    class Probe : ViewModel() { var cleared = false; override fun onCleared() { cleared = true } }
    private val fixtures = mutableListOf<IsolatedProfileHarness>()
    private fun model(failStorage: Boolean = false): Pair<AppearanceViewModel, ProfileManager> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val fixture = IsolatedProfileHarness(base, failStorage).also { fixtures += it }
        val factory = fixture.factory
        val manager = fixture.manager
        lateinit var vm: AppearanceViewModel
        instrumentation.runOnMainSync {
            vm = AppearanceViewModel(manager, NotflixAppearanceStore(factory))
            owner.put("appearance", vm)
        }
        return vm to manager
    }
    private fun authorize(vm: AppearanceViewModel, manager: ProfileManager) {
        compose.waitUntil(10_000) { manager.persistedAppearanceProfile.value != null }
        compose.runOnIdle {
            assertNull(vm.state.value.context)
            val request = requireNotNull(vm.beginSessionConfirmation(1))
            assertTrue(vm.confirmSession(request))
        }
        compose.waitUntil(10_000) { vm.state.value.context != null }
    }
    @After fun clear() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.clear() }
        fixtures.forEach { it.close() }
    }

    @Test fun realSaveRoundTripRetainsSettingsNavigationStoreViewModelAndControlFocus() {
        val (vm, manager) = model()
        lateinit var nav: NavHostController
        lateinit var store: ViewModelStore
        lateinit var probe: Probe
        compose.setContent {
            val state by vm.state.collectAsState()
            nav = rememberNavController()
            val session = rememberNavSessionViewModelStoreOwner(nav)
            SideEffect { store = session.viewModelStore }
            val focus = remember { FocusRequester() }
            // Harness chrome only: production B itself is exercised in NotflixHomeContentTest/smoke.
            val shell = PresentationShell(rootModifier = Modifier.background(
                if (state.effective == AppAppearance.NOTFLIX) NotflixVisualSpec.Background else NotflixVisualSpec.Surface))
            CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
                NotflixPresentationTheme(state.effective == AppAppearance.NOTFLIX) {
                PresentationShellHost(shell, focus) {
                    NavHost(nav, startDestination = "settings") {
                        composable("settings") {
                            val model: Probe = viewModel()
                            SideEffect { probe = model }
                            AppearanceSelectionContent(state, onSelect = vm::select)
                        }
                    }
                }
                }
            }
        }
        compose.onNodeWithTag("appearance-notflix").assertIsNotEnabled()
        authorize(vm, manager)
        val previousNav = nav; val previousStore = store; val previousProbe = probe
        compose.onNodeWithTag("appearance-notflix").requestFocus().assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.waitUntil(10_000) { vm.state.value.requested == AppAppearance.NOTFLIX }
        compose.onNodeWithTag("appearance-notflix").assertIsSelected().assertIsFocused()
        compose.runOnIdle {
            assertEquals(AppearancePolicy.effective(AppAppearance.NOTFLIX), vm.state.value.effective)
            assertSame(previousNav, nav); assertSame(previousStore, store); assertSame(previousProbe, probe)
            assertEquals("settings", nav.currentBackStackEntry?.destination?.route); assertFalse(probe.cleared)
        }
        compose.onNodeWithTag("appearance-nuvio").requestFocus().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.waitUntil(10_000) { vm.state.value.requested == AppAppearance.NUVIO }
        compose.onNodeWithTag("appearance-nuvio").assertIsSelected().assertIsFocused()
        compose.runOnIdle { assertSame(previousProbe, probe); assertFalse(probe.cleared) }
    }

    @Test fun failedRealStorageDoesNotApplyAnotherAppearance() {
        val (vm, manager) = model(failStorage = true)
        compose.setContent {
            val state by vm.state.collectAsState()
            AppearanceSelectionContent(state, onSelect = vm::select)
        }
        authorize(vm, manager)
        compose.onNodeWithTag("appearance-notflix").requestFocus().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.waitUntil(10_000) { vm.state.value.saveFailed }
        compose.onNodeWithTag("appearance-nuvio").assertIsSelected()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.notflix_save_failed)).assertExists()
        compose.runOnIdle {
            assertEquals(AppAppearance.NUVIO, vm.state.value.requested)
            assertEquals(AppAppearance.NUVIO, vm.state.value.effective)
        }
    }
}
