package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.ui.appearance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixTrailerSettingsProjectionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun originalRowsProjectAllTrailerControlsWithOneSoundSwitchAndNoWritesOnEntry() {
        val events = mutableListOf<LayoutSettingsEvent>()
        val sounds = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true), LocalNotflixSettingsFrame provides true,
                LocalNotflixSettingsFocus provides remember { NotflixSettingsFocus {} }) {
                NotflixPresentationTheme(true) {
                    Box(Modifier.requiredSize(960.dp, 540.dp)) {
                        NotflixProjection(NotflixSettingsPage.TRAILERS) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                AppearanceSelectionContent(AppearanceState(context = AppearanceContext(1, 1),
                                    requested = AppAppearance.NOTFLIX, effective = AppAppearance.NOTFLIX,
                                    previewEnabled = true, previewOptionsValid = true),
                                    onSelectPreviewSoundEnabled = { sounds += it }, onSelect = {})
                                LayoutDetailPageSection(LayoutSettingsUiState(detailPageTrailerAutoplayEnabled = true,
                                    detailPageTrailerPlayInBackground = true), onEvent = { events += it })
                            }
                        }
                    }
                }
            }
        }
        val sound = SemanticsMatcher.expectValue(NotflixSettingsRowId, "AppearanceSelectionContent.notflix_preview_sound")
        compose.onAllNodes(sound).assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher.expectValue(NotflixSettingsRowId, "LayoutCardSettings.layout_trailer_muted")).assertCountEquals(0)
        NotflixSettingsCatalog.ids(NotflixSettingsPage.TRAILERS).forEach { id ->
            compose.onNode(SemanticsMatcher.expectValue(NotflixSettingsRowId, id)).assertExists()
        }
        assertTrue(sounds.isEmpty()); assertTrue(events.isEmpty())
        compose.onNode(sound).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(true), sounds) }
    }
}
