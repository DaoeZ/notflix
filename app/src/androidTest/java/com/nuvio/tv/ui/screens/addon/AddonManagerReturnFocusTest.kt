package com.nuvio.tv.ui.screens.addon

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.server.AddonWebConfigMode
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.navigation.addonManagerDestinations
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real destination contract and production manager content; no repositories, stores, QR or network. */
@OptIn(ExperimentalTestApi::class)
class AddonManagerReturnFocusTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun text(id: Int) = instrumentation.targetContext.getString(id)
    private val catalogAddon = Addon(
        id = "focus-fixture", name = "Synthetic addon", version = "1", description = null,
        logo = null, baseUrl = "https://fixture.invalid", types = listOf(ContentType.MOVIE),
        resources = emptyList(), catalogs = listOf(CatalogDescriptor(ContentType.MOVIE, id = "fixture", name = "Fixture"))
    )
    private class Fixture(initial: AddonManagerUiState) {
        var ui by mutableStateOf(initial)
        var readOnly by mutableStateOf(false)
        var mode by mutableStateOf(ExperienceMode.ADVANCED)
        lateinit var nav: NavHostController
        var editorBacks = 0
        var managerBacks = 0
        val effects = mutableListOf<String>()
    }
    private fun show(): Fixture {
        val state = Fixture(AddonManagerUiState(installedAddons = listOf(catalogAddon)))
        compose.setContent {
            NuvioTheme {
                state.nav = rememberNavController()
                NavHost(
                    state.nav, startDestination = "fixture-origin",
                    enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }
                ) {
                    composable("fixture-origin") { Text("Synthetic origin") }
                    addonManagerDestinations(
                        state.nav,
                        managerScreen = { token, back, order, collections ->
                            AddonManagerContent(
                                uiState = state.ui, experienceMode = state.mode, isReadOnly = state.readOnly,
                                webConfigMode = AddonWebConfigMode.FULL, catalogOrderReturnToken = token,
                                onBackPress = { state.managerBacks++; back() },
                                onNavigateToCatalogOrder = order, onNavigateToCollections = collections,
                                actions = AddonManagerActions(
                                    onInstallUrlChange = { state.ui = state.ui.copy(installUrl = it) },
                                    installAddon = { state.effects += "install" },
                                    startQrMode = { state.effects += "qr" },
                                    requestAddonSyncNow = { state.effects += "sync" },
                                    rejectPendingChange = {
                                        state.effects += "reject"
                                        state.ui = state.ui.copy(pendingChange = null)
                                    },
                                    confirmPendingChange = { state.effects += "confirm" }
                                )
                            )
                        },
                        catalogOrderScreen = { back ->
                            BackHandler { state.editorBacks++; back() }
                            Box(Modifier.fillMaxSize()) { Text("Synthetic editor: no data changes") }
                        }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { state.nav.navigate(Screen.AddonManager.route) }
        compose.waitForIdle()
        return state
    }
    private fun control(id: Int) = compose.onNode(isFocusable() and hasClickAction() and
        (hasText(text(id)) or hasAnyDescendant(hasText(text(id)))))
    private fun url() = compose.onNode(isFocusable() and hasClickAction() and !hasSetTextAction() and
        hasAnyDescendant(hasText(text(R.string.addon_install_placeholder))))
    private fun order() = control(R.string.addon_reorder_title)
    private fun focus(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle(); node.assertIsFocused()
    }
    private fun open(state: Fixture) {
        focus(order())
        order().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.CatalogOrder.route, state.nav.currentDestination?.route) }
    }
    private fun back() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test fun returningEditorRestoresItsEntryAndOnePressDoesNotAlsoLeaveManager() {
        val state = show(); url().assertIsFocused(); open(state); back()
        order().assertIsFocused()
        compose.runOnIdle {
            assertEquals(Screen.AddonManager.route, state.nav.currentDestination?.route)
            assertEquals(1, state.editorBacks); assertEquals(0, state.managerBacks); assertTrue(state.effects.isEmpty())
        }
        open(state); back(); order().assertIsFocused()
        compose.runOnIdle { assertEquals(2, state.editorBacks); assertEquals(0, state.managerBacks) }
        back()
        compose.runOnIdle {
            assertEquals(1, state.managerBacks); assertEquals("fixture-origin", state.nav.currentDestination?.route)
            state.nav.navigate(Screen.AddonManager.route)
        }
        compose.waitForIdle(); url().assertIsFocused()
    }

    @Test fun initialEntryKeepsUrlUsableWithoutActivatingInstallOrQr() {
        val state = show(); url().assertIsFocused()
        url().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.onNode(hasSetTextAction()).performTextInput("https://example.invalid/manifest.json")
        compose.runOnIdle {
            assertEquals("https://example.invalid/manifest.json", state.ui.installUrl)
            assertTrue(state.effects.isEmpty()); assertEquals(0, state.managerBacks)
        }
    }

    @Test fun missingCatalogEntryReturnsToUrlAndLateCatalogDoesNotStealFocus() {
        val state = show(); open(state)
        compose.runOnIdle { state.ui = state.ui.copy(installedAddons = emptyList()) }
        back(); url().assertIsFocused(); order().assertDoesNotExist()
        focus(control(R.string.addon_manage_from_phone_title))
        compose.runOnIdle { state.ui = state.ui.copy(installedAddons = listOf(catalogAddon)) }
        compose.waitForIdle()
        control(R.string.addon_manage_from_phone_title).assertIsFocused()
    }

    @Test fun readOnlyReturnFallsBackToManageWithoutActivatingIt() {
        val state = show(); open(state)
        compose.runOnIdle { state.readOnly = true }
        back(); order().assertDoesNotExist(); url().assertDoesNotExist()
        control(R.string.addon_manage_from_phone_title).assertIsFocused()
        compose.runOnIdle { assertTrue(state.effects.isEmpty()) }
    }

    @Test fun essentialReturnFallsBackToUrlWithoutChangingExperience() {
        val state = show(); open(state)
        compose.runOnIdle { state.mode = ExperienceMode.ESSENTIAL }
        back(); order().assertDoesNotExist(); url().assertIsFocused()
        compose.runOnIdle { assertEquals(ExperienceMode.ESSENTIAL, state.mode); assertTrue(state.effects.isEmpty()) }
    }

    @Test fun pendingReturnWaitsForLoadingThenLaterLoadCannotStealUserFocus() {
        val state = show(); open(state)
        compose.runOnIdle { state.ui = state.ui.copy(isLoading = true, installedAddons = emptyList()) }
        back(); order().assertDoesNotExist()
        compose.runOnIdle { state.ui = state.ui.copy(isLoading = false, installedAddons = listOf(catalogAddon)) }
        compose.waitForIdle(); order().assertIsFocused()
        focus(control(R.string.addon_manage_from_phone_title))
        compose.runOnIdle { state.ui = state.ui.copy(isLoading = true) }
        compose.waitForIdle()
        compose.runOnIdle { state.ui = state.ui.copy(isLoading = false, installedAddons = listOf(catalogAddon.copy(displayName = "Late metadata"))) }
        compose.waitForIdle(); control(R.string.addon_manage_from_phone_title).assertIsFocused()
    }

    @Test fun confirmationBackClosesOnlyContextBeforeReturningFocusAndAnotherPressLeavesManager() {
        val state = show(); open(state)
        compose.runOnIdle {
            state.ui = state.ui.copy(pendingChange = PendingChangeInfo(
                changeId = "synthetic", proposedUrls = emptyList(), addedUrls = emptyList(), removedUrls = emptyList()
            ))
        }
        back()
        compose.onNodeWithText(text(R.string.addon_confirm_title)).assertExists()
        back()
        compose.onNodeWithText(text(R.string.addon_confirm_title)).assertDoesNotExist()
        order().assertIsFocused()
        compose.runOnIdle {
            assertEquals(listOf("reject"), state.effects)
            assertEquals(1, state.editorBacks); assertEquals(0, state.managerBacks)
            assertEquals(Screen.AddonManager.route, state.nav.currentDestination?.route)
        }
        back()
        compose.runOnIdle {
            assertEquals(1, state.managerBacks)
            assertEquals("fixture-origin", state.nav.currentDestination?.route)
        }
    }
}
