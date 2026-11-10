package com.nuvio.tv.ui.screens.account

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixPresentationTheme
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test

class AuthQrNotflixSkinTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setBrand(notflixSkin: Boolean) {
        compose.setContent {
            NuvioTheme {
                NotflixPresentationTheme(notflixSkin) {
                    AuthQrBrandPanel(
                        modifier = Modifier,
                        isSignedIn = false,
                        fullAccount = null,
                        useEmailLogin = false,
                        skin = authQrSkin(notflixSkin)
                    )
                }
            }
        }
    }

    @Test
    fun notflixSkinShowsNotflixBrandAndNoNuvioWordmark() {
        setBrand(notflixSkin = true)
        val nuvioDescription = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.cd_nuvio)

        compose.onNodeWithContentDescription("NOTFLIX").assertExists()
        compose.onNodeWithContentDescription(nuvioDescription).assertDoesNotExist()
    }

    @Test
    fun officialSkinKeepsNuvioWordmark() {
        setBrand(notflixSkin = false)
        val nuvioDescription = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.cd_nuvio)

        compose.onNodeWithContentDescription(nuvioDescription).assertExists()
        compose.onNodeWithContentDescription("NOTFLIX").assertDoesNotExist()
    }
}
