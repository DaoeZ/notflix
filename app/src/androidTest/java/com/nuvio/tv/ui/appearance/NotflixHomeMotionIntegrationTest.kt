package com.nuvio.tv.ui.appearance

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.ui.navigation.rememberNavSessionViewModelStoreOwner
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.runBlocking
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixHomeMotionIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    private var fixture: IsolatedProfileHarness? = null
    class Probe : ViewModel() { var cleared = false; override fun onCleared() { cleared = true } }
    @After fun clear() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.clear() }
        fixture?.close()
    }
    private fun key(tag: String) = compose.onNodeWithTag(tag).requestFocus().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }

    @Test fun realMotionSaveKeepsFocusedControlNavStoreViewModelAndIndependentProfiles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val own = IsolatedProfileHarness(instrumentation.targetContext).also { fixture = it }
        val store = NotflixAppearanceStore(own.factory)
        lateinit var vm: AppearanceViewModel
        instrumentation.runOnMainSync { vm = AppearanceViewModel(own.manager, store); owner.put("appearance", vm) }
        lateinit var nav: NavHostController
        lateinit var viewModels: ViewModelStore
        lateinit var probe: Probe
        compose.setContent {
            val state by vm.state.collectAsState()
            nav = rememberNavController()
            val session = rememberNavSessionViewModelStoreOwner(nav)
            SideEffect { viewModels = session.viewModelStore }
            val focus = remember { FocusRequester() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
                PresentationShellHost(PresentationShell(), focus) {
                    NavHost(nav, startDestination = "settings") {
                        composable("settings") {
                            val current: Probe = viewModel()
                            SideEffect { probe = current }
                            AppearanceSelectionContent(state, onSelectHomeMotion = vm::selectHomeMotion, onSelect = vm::select)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("home-motion-reduced").assertDoesNotExist()
        compose.waitUntil(10_000) { own.manager.persistedAppearanceProfile.value != null }
        compose.runOnIdle { assertTrue(vm.confirmSession(requireNotNull(vm.beginSessionConfirmation(1)))) }
        compose.waitUntil(10_000) { vm.state.value.context != null }
        val previousNav = nav; val previousStore = viewModels; val previousProbe = probe
        key("appearance-notflix")
        compose.waitUntil(10_000) { vm.state.value.effective == AppAppearance.NOTFLIX }
        compose.onNodeWithTag("home-motion-normal").assertExists()
        key("home-motion-normal")
        compose.waitUntil(10_000) { vm.state.value.reduceHomeMotion }
        compose.onNodeWithTag("home-motion-reduced").assertIsFocused()
        compose.onNodeWithText("Home motion").assertExists()
        compose.onNodeWithTag("home-motion-reduced").assertTextContains("Reduced ›")
        compose.runOnIdle {
            assertSame(previousNav, nav); assertSame(previousStore, viewModels); assertSame(previousProbe, probe)
            assertFalse(probe.cleared); assertEquals("settings", nav.currentBackStackEntry?.destination?.route)
        }
        key("appearance-nuvio")
        compose.waitUntil(10_000) { vm.state.value.effective == AppAppearance.NUVIO }
        compose.onNodeWithTag("home-motion-reduced").assertDoesNotExist()
        assertTrue(vm.state.value.reduceHomeMotion)
        key("appearance-notflix")
        compose.waitUntil(10_000) { vm.state.value.effective == AppAppearance.NOTFLIX }
        compose.onNodeWithTag("home-motion-reduced").assertExists()
        runBlocking {
            own.profiles.upsertProfile(UserProfile(2, "Motion fixture2", "#112233"))
            own.profiles.setActiveProfile(2)
        }
        compose.waitUntil(10_000) { own.manager.persistedAppearanceProfile.value?.profileId == 2 && vm.state.value.context == null }
        compose.onNodeWithTag("home-motion-reduced").assertDoesNotExist()
        compose.runOnIdle { assertTrue(vm.confirmSession(requireNotNull(vm.beginSessionConfirmation(2)))) }
        compose.waitUntil(10_000) { vm.state.value.context?.profileId == 2 }
        assertFalse(vm.state.value.reduceHomeMotion)
        key("appearance-notflix")
        compose.waitUntil(10_000) { vm.state.value.effective == AppAppearance.NOTFLIX }
        compose.onNodeWithTag("home-motion-normal").assertExists()
        compose.runOnIdle { assertSame(previousNav, nav); assertSame(previousStore, viewModels); assertSame(previousProbe, probe); assertFalse(probe.cleared) }
    }

    @Test fun motionLabelsScopeAndSelectionWorkInEnglishAndSpanishWithoutGlobalLocaleMutation() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val locales = base.resources.configuration.locales.toLanguageTags()
        val language = mutableStateOf("en")
        val state = mutableStateOf(AppearanceState(context = AppearanceContext(1, 1), requested = AppAppearance.NOTFLIX,
            effective = AppAppearance.NOTFLIX, reduceHomeMotion = true))
        compose.setContent {
            val localized = remember(language.value) { base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.value))
            }) }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                NuvioTheme { AppearanceSelectionContent(state.value, onSelectHomeMotion = { state.value = state.value.copy(reduceHomeMotion = it) }, onSelect = {}) }
            }
        }
        compose.onNodeWithText("Home motion").assertExists()
        compose.onNodeWithTag("home-motion-reduced").assertTextContains("Reduced ›")
        compose.onNodeWithText("Applies only to Notflix Home. Reduced removes card zoom and animation; expanded information and focus stay available.").assertExists()
        compose.onNodeWithTag("home-motion-reduced").assertExists()
        compose.runOnIdle { language.value = "es" }
        compose.onNodeWithText("Movimiento de Inicio").assertExists()
        compose.onNodeWithTag("home-motion-reduced").assertTextContains("Reducido ›")
        key("home-motion-reduced")
        compose.onNodeWithTag("home-motion-normal").assertIsFocused()
        compose.onNodeWithTag("home-motion-normal").assertTextContains("Normal ›")
        assertFalse(state.value.reduceHomeMotion)
        assertEquals(locales, base.resources.configuration.locales.toLanguageTags())
    }
}
