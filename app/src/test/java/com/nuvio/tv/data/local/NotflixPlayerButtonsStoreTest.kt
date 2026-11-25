package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.NotflixPlayerActions
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NotflixPlayerButtonsStoreTest {
    private class Memory : DataStore<Preferences> {
        // Avoid querying Android's device locale in a plain JVM; every profile has an existing language preference.
        override val data = MutableStateFlow<Preferences>(preferencesOf(stringPreferencesKey("subtitle_preferred_language") to "en"))
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    private val profile = MutableStateFlow(1)
    private val profiles = mutableMapOf<Int, Memory>()
    private val factory = mockk<ProfileDataStoreFactory> {
        every { get(any(), "player_settings") } answers { synchronized(profiles) { profiles.getOrPut(firstArg()) { Memory() } } }
    }
    private fun store() = PlayerSettingsDataStore(factory,
        mockk<ProfileManager> { every { activeProfileId } returns profile }, mockk<Context>(relaxed = true))

    @Test fun orderSurvivesRecreationAndLateSaveKeepsItsOriginalProfile() = runTest {
        val first = store()
        first.setNotflixPlayerButtons(listOf("sources", "speed"), 1)
        profile.value = 2
        assertEquals(NotflixPlayerActions.defaults, first.playerSettings.first().notflixPlayerButtons)
        first.setNotflixPlayerButtons(listOf("chapters"), 1) // an editor opened in profile 1
        assertEquals(NotflixPlayerActions.defaults, first.playerSettings.first().notflixPlayerButtons)
        first.setNotflixPlayerButtons(emptyList(), 2)
        assertTrue(store().playerSettings.first().notflixPlayerButtons.isEmpty())
        profile.value = 1
        assertEquals(listOf("chapters"), store().playerSettings.first().notflixPlayerButtons)
    }

    @Test fun existingPreferencesArePreservedAndOnlyTheNewKeyIsAdded() = runTest {
        val data = Memory(); synchronized(profiles) { profiles[1] = data }
        data.edit { it[stringPreferencesKey("preferred_audio_language")] = "es" }
        val settings = store()
        assertEquals(NotflixPlayerActions.defaults, settings.playerSettings.first().notflixPlayerButtons)
        settings.setNotflixPlayerButtons(listOf("sources", "sources", "unknown", "more"), 1)
        assertEquals("es", data.data.value[stringPreferencesKey("preferred_audio_language")])
        assertEquals(listOf("sources"), settings.playerSettings.first().notflixPlayerButtons)
    }
}
