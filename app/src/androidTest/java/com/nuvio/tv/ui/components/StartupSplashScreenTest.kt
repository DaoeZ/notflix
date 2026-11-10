package com.nuvio.tv.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test

class StartupSplashScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun notflixSplashShowsWordmarkAndRedSpinner() {
        compose.setContent {
            NuvioTheme {
                StartupSplashScreen(
                    profileColorHex = "#E50914",
                    profileBackgroundUrl = "file:///not-used",
                    notflix = true
                )
            }
        }
        compose.onNodeWithContentDescription("NOTFLIX").assertIsDisplayed()
        compose.onNodeWithTag("notflix_startup_spinner").assertIsDisplayed()
    }
}
