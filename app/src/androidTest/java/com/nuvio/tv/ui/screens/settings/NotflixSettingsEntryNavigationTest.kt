package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixSettingsEntryNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    @Composable private fun Content() {
        nav = rememberNavController()
        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true)) {
            NotflixPresentationTheme(true) {
                NavHost(nav, startDestination = "origin") {
                    composable("origin") { SettingsActionRow("Origin", null, onClick = {}, modifier = Modifier.testTag("origin")) }
                    composable("old-about") { RedirectNotflixSettings(nav, "old-about", NotflixSettingsPage.ABOUT) }
                    composable("old-playback") { RedirectNotflixSettings(nav, "old-playback", NotflixSettingsPage.PLAYBACK) }
                    composable(Screen.Settings.route) { entry ->
                        val requested by entry.savedStateHandle.getStateFlow<String?>(NotflixSettingsEntry.DESTINATION, null).collectAsState()
                        NotflixSettingsWorkspace(true, true, NotflixSettingsEntry.isNotflixIntegrationsRequested(entry.savedStateHandle),
                            true, requested) { page, open ->
                            Column {
                                SettingsActionRow(page.name, null, onClick = {}, settingId = "body", modifier = Modifier.testTag("body-${page.name}"))
                                notflixSettingsChildren(page).forEach { NotflixSettingsLink(it, open) }
                            }
                        }
                    }
                }
            }
        }
    }
    @Test fun compatibilityEntryIsReplacedAndBackCannotRedirectInALoop() {
        compose.setContent { Content() }
        compose.runOnIdle { nav.navigate("old-about") }
        compose.onNodeWithTag("body-ABOUT").assertIsFocused()
        compose.runOnIdle {
            assertEquals(Screen.Settings.route, nav.currentBackStackEntry?.destination?.route)
            assertEquals("origin", nav.previousBackStackEntry?.destination?.route)
            nav.popBackStack()
        }
        compose.onNodeWithTag("origin").assertExists()
        compose.runOnIdle { nav.navigate("old-playback") }
        compose.onNodeWithTag("body-PLAYBACK").assertIsFocused()
    }
    @Test fun savedEntryDoesNotReapplyAnOldDirectDestinationAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.runOnIdle { nav.navigate("old-about") }
        compose.onNodeWithTag("body-ABOUT").assertIsFocused()
        compose.onNodeWithTag("notflix-settings-open-about").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("body-ABOUT").assertIsFocused()
        compose.runOnIdle { assertEquals("origin", nav.previousBackStackEntry?.destination?.route) }
    }
}
