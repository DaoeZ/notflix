package com.nuvio.tv.data.local

import android.content.ComponentCallbacks
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nuvio.tv.core.profile.ProfileManager
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual settings flow with isolated in-memory stores; no locale mutation or disk access. */
class TmdbAppLanguageTest {
    private var appTag = "pt-BR"
    private val profile = MutableStateFlow(1)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val callbacks = java.util.concurrent.CopyOnWriteArrayList<ComponentCallbacks>()
    private val stores = mutableMapOf<Int, DataStore<Preferences>>()
    private lateinit var settings: TmdbSettingsDataStore

    private fun configuration(locale: Locale): Configuration {
        val locales = mockk<LocaleList> { every { get(0) } returns locale }
        return mockk { every { this@mockk.locales } returns locales }
    }

    private fun start() {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString("locale_tag", null) } answers { appTag }
            every { registerOnSharedPreferenceChangeListener(any()) } answers { listeners.add(firstArg()); Unit }
            every { unregisterOnSharedPreferenceChangeListener(any()) } answers { listeners.remove(firstArg()); Unit }
        }
        val resources = mockk<Resources> { every { configuration } returns configuration(Locale.ENGLISH) }
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences("app_locale", Context.MODE_PRIVATE) } returns prefs
            every { this@mockk.resources } returns resources
            every { registerComponentCallbacks(any()) } answers { callbacks.add(firstArg()); Unit }
            every { unregisterComponentCallbacks(any()) } answers { callbacks.remove(firstArg()); Unit }
        }
        val factory = mockk<ProfileDataStoreFactory> {
            every { get(any(), "tmdb_settings") } answers {
                stores.getOrPut(firstArg()) {
                    object : DataStore<Preferences> {
                        override val data = MutableStateFlow(emptyPreferences())
                        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                            transform(data.value).also { data.value = it }
                    }
                }
            }
        }
        val manager = mockk<ProfileManager> { every { activeProfileId } returns profile }
        settings = TmdbSettingsDataStore(factory, manager, context)
    }

    private suspend fun awaitLanguage(language: String) = withTimeout(5_000) {
        settings.settings.first { it.language == language }
    }

    private fun selectAppLanguage(tag: String) {
        appTag = tag
        listeners.toList().forEach { it.onSharedPreferenceChanged(mockk(relaxed = true), "locale_tag") }
    }

    @After fun stop() = runBlocking {
        if (::settings.isInitialized) {
            val scope = settings.javaClass.getDeclaredField("scope").apply { isAccessible = true }.get(settings) as CoroutineScope
            scope.cancel()
            withTimeout(5_000) { while (listeners.isNotEmpty() || callbacks.isNotEmpty()) delay(10) }
        }
    }

    @Test fun persistedAppLanguageWinsOverApplicationContextSystemResources() = runBlocking {
        start()
        assertEquals("pt-br", awaitLanguage("pt-br").language)
    }

    @Test fun changingAppLanguageEmitsWithoutWritingTmdbPreferences() = runBlocking {
        start()
        awaitLanguage("pt-br")
        selectAppLanguage("zh-TW")
        assertEquals("zh-TW", awaitLanguage("zh-TW").language)
    }

    @Test fun explicitTmdbLanguageSurvivesAppChangesAndIsScopedToItsProfile() = runBlocking {
        start()
        awaitLanguage("pt-br")
        settings.setLanguage("fr")
        awaitLanguage("fr")
        selectAppLanguage("es")
        settings.setUseBasicInfo(false)
        val updated = withTimeout(5_000) { settings.settings.first { !it.useBasicInfo } }
        assertEquals("fr", updated.language)
        profile.value = 2
        assertEquals("es", awaitLanguage("es").language)
        profile.value = 1
        assertEquals("fr", awaitLanguage("fr").language)
    }

    @Test fun systemConfigurationChangesApplyOnlyWhenTheAppUsesSystemLanguage() = runBlocking {
        appTag = ""
        start()
        withTimeout(5_000) { while (callbacks.isEmpty()) delay(10) }
        callbacks.toList().forEach { it.onConfigurationChanged(configuration(Locale.GERMAN)) }
        assertEquals("de", awaitLanguage("de").language)
        selectAppLanguage("pt-PT")
        awaitLanguage("pt")
        callbacks.toList().forEach { it.onConfigurationChanged(configuration(Locale.FRENCH)) }
        settings.setUseBasicInfo(false)
        assertEquals("pt", withTimeout(5_000) { settings.settings.first { !it.useBasicInfo } }.language)
    }
}
