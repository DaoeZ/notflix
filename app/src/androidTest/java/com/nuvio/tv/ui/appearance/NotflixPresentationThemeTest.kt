package com.nuvio.tv.ui.appearance

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.data.local.ThemeDataStore
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.ThemeColors
import com.squareup.moshi.Moshi
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixPresentationThemeTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = mutableListOf<IsolatedProfileHarness>()
    @After fun closeFixtures() { fixtures.forEach { it.close() } }
    private data class Snapshot(val actual: AppTheme, val accent: Color, val primary: Color,
        val background: Color, val text: Color, val body: TextStyle, val marker: Any)

    @Test fun realOfficialThemeRemainsStoredAndLatestLegitimateChangeReturnsWithoutLosingContent() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = IsolatedProfileHarness(base).also { fixtures += it }
        val factory = fixture.factory
        val manager = fixture.manager
        runBlocking { kotlinx.coroutines.withTimeout(10_000) { manager.activeProfileReady.first { it } } }
        val theme = ThemeDataStore(factory, manager)
        runBlocking { theme.setTheme(AppTheme.WHITE) }
        val active = mutableStateOf(false)
        lateinit var snapshot: Snapshot
        compose.setContent {
            val actual by remember(theme) { theme.themeSelection.map { it.theme ?: AppTheme.WHITE } }.collectAsState(initial = AppTheme.WHITE)
            NuvioTheme(appTheme = actual) {
                NotflixPresentationTheme(active.value) {
                    val marker = remember { Any() }
                    val next = Snapshot(NuvioTheme.currentTheme, NuvioTheme.colors.Secondary,
                        MaterialTheme.colorScheme.primary, NuvioTheme.colors.Background,
                        NuvioTheme.colors.TextPrimary, MaterialTheme.typography.bodyLarge, marker)
                    SideEffect { snapshot = next }
                }
            }
        }
        compose.runOnIdle { assertEquals(ThemeColors.White.secondary, snapshot.accent) }
        val initial = snapshot
        compose.runOnIdle { active.value = true }
        compose.runOnIdle {
            assertEquals(AppTheme.WHITE, snapshot.actual)
            assertEquals(NotflixVisualSpec.Accent, snapshot.accent)
            assertEquals(NotflixVisualSpec.Accent, snapshot.primary)
            assertEquals(NotflixVisualSpec.Background, snapshot.background)
            assertEquals(NotflixVisualSpec.Text, snapshot.text)
            assertEquals(initial.body, snapshot.body)
            assertSame(initial.marker, snapshot.marker)
        }
        assertEquals(AppTheme.WHITE, runBlocking { theme.getThemeForProfile(1) })
        // An official change while the overlay is active must survive reversion.
        runBlocking { theme.setTheme(AppTheme.OCEAN) }
        compose.waitUntil(10_000) { snapshot.actual == AppTheme.OCEAN }
        compose.runOnIdle { assertEquals(NotflixVisualSpec.Accent, snapshot.accent); active.value = false }
        compose.runOnIdle {
            assertEquals(ThemeColors.Ocean.secondary, snapshot.accent)
            assertEquals(ThemeColors.Ocean.background, snapshot.background)
            assertSame(initial.marker, snapshot.marker)
            assertEquals(initial.body, snapshot.body)
        }
        assertEquals(AppTheme.OCEAN, runBlocking { theme.getThemeForProfile(1) })
    }

    @Test fun ownLabelsFollowProvidedEnglishAndSpanishWithoutGlobalLocaleMutation() {
        val language = mutableStateOf("en")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val originalLocales = base.resources.configuration.locales.toLanguageTags()
        compose.setContent {
            val localized = remember(language.value) {
                base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.value)) })
            }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                NuvioTheme {
                    NotflixPresentationTheme(true) {
                        AppearanceSelectionContent(AppearanceState(context = AppearanceContext(1, 1),
                            requested = AppAppearance.NOTFLIX, effective = AppAppearance.NOTFLIX)) {}
                    }
                }
            }
        }
        compose.onNodeWithText("Appearance · this profile and device").assertExists()
        compose.onNodeWithText("Active: Notflix").assertExists()
        compose.runOnIdle { language.value = "es" }
        compose.onNodeWithText("Apariencia · este perfil y dispositivo").assertExists()
        compose.onNodeWithText("Activa: Notflix").assertExists()
        compose.runOnIdle { language.value = "en" }
        compose.onNodeWithText("Active: Notflix").assertExists()
        assertEquals(originalLocales, base.resources.configuration.locales.toLanguageTags())
    }
}
