package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixPresentation
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test

/** M68: the build's own TMDB key is announced and TMDB is credited in Acerca de. */
class NotflixTmdbAttributionComposeTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theTmdbRowShowsTheNotflixKeyInsteadOfNotConfigured() {
        val builtIn = context.getString(R.string.notflix_provider_tmdb_built_in)

        compose.setContent {
            NuvioTheme {
                NotflixProviderTmdbRow(value = builtIn, initialFocusRequester = null, onClick = {})
            }
        }

        compose.onNodeWithTag("notflix-provider-tmdb").assertIsDisplayed()
        compose.onNodeWithText(builtIn).assertIsDisplayed()
    }

    @Test
    fun notflixAboutCreditsTmdb() {
        val attribution = context.getString(R.string.notflix_tmdb_attribution)

        compose.setContent {
            NuvioTheme {
                CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = true)) {
                    AboutSettingsContent()
                }
            }
        }

        compose.onNodeWithTag("notflix-tmdb-attribution").assertExists()
        compose.onNodeWithText(attribution).assertExists()
    }

    @Test
    fun nuvioAboutKeepsItsOwnCreditsWithoutTheNotflixAttribution() {
        compose.setContent {
            NuvioTheme {
                CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = false)) {
                    AboutSettingsContent()
                }
            }
        }

        compose.onNodeWithTag("notflix-tmdb-attribution").assertDoesNotExist()
    }
}
