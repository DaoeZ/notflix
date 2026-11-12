package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.screens.settings.NotflixSettingsEntry
import com.nuvio.tv.ui.screens.settings.openNotflixIntegrations
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixDestination
import com.nuvio.tv.ui.appearance.NotflixNavigationState
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.appearance.PresentationShellHost
import com.nuvio.tv.ui.appearance.rememberNotflixNavigationShell
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** M66: the KIDS notice replaces the generic empty message and leads to Integraciones. */
@OptIn(ExperimentalTestApi::class)
class NotflixKidsHomeComposeTest {
    @get:Rule val compose = createComposeRule()
    private val strings = InstrumentationRegistry.getInstrumentation().targetContext
    private val integrations = mutableListOf<String>()

    private fun show(ageLimit: AgeLevel?, keyAvailable: Boolean) {
        val state = HomeUiState(
            layoutPreferencesReady = true, isLoading = false, heroSectionEnabled = false,
            notflixPresentationActive = true, ageLimit = ageLimit, ageTmdbKeyAvailable = keyAvailable,
            notflixCatalog = NotflixCatalogSnapshot(1, 1, true, listOf(
                NotflixCatalogRow("collection_notflix-kids:kids-animacion", "collection_notflix-kids:kids-animacion",
                    "Animated", NotflixRowPhase.READY, folderTarget = "notflix-kids" to "kids-animacion")
            )),
            notflixCwPhase = NotflixRowPhase.READY
        )
        compose.setContent {
            val controller = rememberNavController()
            NavHost(controller, startDestination = "home") {
                composable("home") {
                    val navigation = remember { NotflixNavigationState() }
                    val focus = remember { FocusRequester() }
                    val held = remember { mutableStateOf(false) }
                    val shell = rememberNotflixNavigationShell(navigation, "home", "home",
                        setOf("home"), "home", listOf(NotflixDestination("home", "Inicio")),
                        focus, null, {}, {}, {}, held)
                    CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true, navigation)) {
                        PresentationShellHost(shell, focus) {
                            NotflixHomeContent(state, NotflixHomeMemory(), navigation = navigation,
                                onDetail = { _, _, _ -> }, onContinue = {}, onFolder = { _, _ -> },
                                onVisibleRow = {}, onOpenIntegrations = {
                                    integrations += "integrations"
                                    controller.openNotflixIntegrations()
                                },
                                onRetry = { integrations += "retry" })
                        }
                    }
                }
                composable(Screen.Settings.route) { entry ->
                    if (NotflixSettingsEntry.isNotflixIntegrationsRequested(entry.savedStateHandle)) {
                        Text("Notflix integrations destination", Modifier.testTag("kids-integrations-destination"))
                    }
                }
            }
        }
    }

    @Test fun kidsProfileWithoutTheKeySeesTheNoticeAndTheIntegrationsButton() {
        show(AgeLevel.TP, keyAvailable = false)
        compose.onNodeWithText(strings.getString(R.string.notflix_kids_tmdb_required)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.notflix_no_content)).assertDoesNotExist()
        compose.onNodeWithTag("notflix-kids-integrations").assertExists()
        compose.onNodeWithTag("notflix-kids-integrations").performClick()
        compose.waitForIdle()
        assertEquals(listOf("integrations"), integrations)
        compose.onNodeWithTag("kids-integrations-destination").assertExists()
    }

    @Test fun theNoticeIsNotShownWithTheKeyOrForNormalProfiles() {
        show(AgeLevel.TP, keyAvailable = true)
        compose.onNodeWithText(strings.getString(R.string.notflix_kids_tmdb_required)).assertDoesNotExist()
        compose.onNodeWithTag("notflix-kids-integrations").assertDoesNotExist()
    }
    @Test fun normalProfileWithoutTheKeyNeverShowsTheKidsNotice() {
        show(null, keyAvailable = false)
        compose.onNodeWithTag("notflix-kids-integrations").assertDoesNotExist()
    }
}
