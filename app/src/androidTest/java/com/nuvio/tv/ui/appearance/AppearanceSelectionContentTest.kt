package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.requestFocus
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.NotflixCardOpening
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R

/** Instrumented harness only; compilation is not evidence of execution on a TV. */
@OptIn(ExperimentalTestApi::class)
class AppearanceSelectionContentTest {
    @get:Rule val compose = createComposeRule()
    private fun active(name: String) = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.notflix_appearance_active, name)

    @Test fun unresolvedContextDisablesSelection() {
        compose.setContent { AppearanceSelectionContent(AppearanceState()) { error("Disabled") } }
        compose.onNodeWithTag("appearance-notflix").assertIsNotEnabled()
        compose.onNodeWithTag("appearance-effective").assertTextEquals(active("Nuvio"))
    }

    @Test fun requestedSelectionReflectsLaboratoryAvailability() {
        val state = mutableStateOf(AppearanceState(context = AppearanceContext(1, 1)))
        val selections = mutableListOf<AppAppearance>()
        compose.setContent {
            AppearanceSelectionContent(state.value) { value ->
                selections += value
                // A substitute for an already-confirmed save, not a distributed renderer.
                state.value = state.value.copy(requested = value, effective = AppearancePolicy.effective(value))
            }
        }
        // TV Button handles D-pad Enter, not the touch tap injected by performClick.
        compose.onNodeWithTag("appearance-notflix").assertIsEnabled()
            .requestFocus().assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
            .assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(AppAppearance.NOTFLIX), selections) }
        compose.onNodeWithTag("appearance-effective").assertTextEquals(active(if (AppearancePolicy.NOTFLIX_AVAILABLE) "Notflix" else "Nuvio"))
        compose.onNodeWithTag("appearance-nuvio").requestFocus().assertIsFocused()
            .performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
            .assertIsSelected()
        compose.onNodeWithTag("appearance-effective").assertTextEquals(active("Nuvio"))
        compose.runOnIdle { assertEquals(listOf(AppAppearance.NOTFLIX, AppAppearance.NUVIO), selections) }
    }

    @Test fun appearanceListsSaveTheirActualValuesAndReturnFocusAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(AppearanceState(context = AppearanceContext(1, 1),
            requested = AppAppearance.NOTFLIX, effective = AppAppearance.NOTFLIX))
        restoration.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true, reduceHomeMotion = true)) {
                NotflixPresentationTheme(true) {
                    AppearanceSelectionContent(state.value,
                        onSelectHomeMotion = { state.value = state.value.copy(reduceHomeMotion = it) },
                        onSelectCardOpening = { state.value = state.value.copy(cardOpening = it) },
                        onSelectCardSize = { state.value = state.value.copy(cardSize = it) }, onSelect = {})
                }
            }
        }
        compose.onNodeWithTag("card-size-large").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("card-size-option-small").performClick()
        compose.onNodeWithTag("card-size-small").assertIsFocused()
        compose.onNodeWithTag("card-opening-normal").performClick()
        compose.onNodeWithTag("card-opening-option-instant").performClick()
        compose.onNodeWithTag("card-opening-instant").assertIsFocused()
        compose.onNodeWithTag("home-motion-normal").performClick()
        compose.onNodeWithTag("home-motion-option-reduced").performClick()
        compose.onNodeWithTag("home-motion-reduced").assertIsFocused()
        compose.runOnIdle {
            assertEquals(NotflixCardSize.SMALL, state.value.cardSize)
            assertEquals(NotflixCardOpening.INSTANT, state.value.cardOpening)
            assertEquals(true, state.value.reduceHomeMotion)
        }
    }

    /** Separates semantic activation/callback/recomposition from the D-pad input path. */
    @Test fun semanticActivationUpdatesRequestedSelection() {
        val state = mutableStateOf(AppearanceState(context = AppearanceContext(1, 1)))
        val selections = mutableListOf<AppAppearance>()
        compose.setContent {
            AppearanceSelectionContent(state.value) { value ->
                selections += value
                state.value = state.value.copy(requested = value, effective = AppearancePolicy.effective(value))
            }
        }
        compose.onNodeWithTag("appearance-notflix")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }.assertIsSelected()
        compose.onNodeWithTag("appearance-effective").assertTextEquals(active(if (AppearancePolicy.NOTFLIX_AVAILABLE) "Notflix" else "Nuvio"))
        compose.runOnIdle { assertEquals(listOf(AppAppearance.NOTFLIX), selections) }
        compose.onNodeWithTag("appearance-nuvio")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }.assertIsSelected()
        compose.onNodeWithTag("appearance-effective").assertTextEquals(active("Nuvio"))
        compose.runOnIdle { assertEquals(listOf(AppAppearance.NOTFLIX, AppAppearance.NUVIO), selections) }
    }
}

