package com.nuvio.tv.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.*
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbBuiltInSettingsStateTest {
    private fun store(state: MutableStateFlow<NotflixIntegrationCredentials>) = mockk<NotflixIntegrationStore> {
        every { values } returns state
        every { read() } answers { state.value }
    }

    private suspend fun close(model: ViewModel) { model.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }

    @Test fun builtInIndicatorFollowsTheRealViewModelWhenAUserKeyIsAddedAndCleared() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values = MutableStateFlow(NotflixIntegrationCredentials())
        val config = NotflixIntegrationConfig(builtInTmdbApiKey = "synthetic-build-key") { values.value }
        val model = NotflixProviderSettingsViewModel(store(values), mockk<Context>(), config)
        try {
            runCurrent(); assertTrue(model.builtInTmdbKeyInUse.value)
            values.value = values.value.copy(tmdbApiKey = "synthetic-user-key")
            runCurrent(); assertFalse(model.builtInTmdbKeyInUse.value)
            values.value = NotflixIntegrationCredentials()
            runCurrent(); assertTrue(model.builtInTmdbKeyInUse.value)
        } finally { close(model); Dispatchers.resetMain() }
    }

    @Test fun noBuiltInKeyNeverShowsTheBuiltInIndicator() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values = MutableStateFlow(NotflixIntegrationCredentials())
        val config = NotflixIntegrationConfig(builtInTmdbApiKey = "") { values.value }
        val model = NotflixProviderSettingsViewModel(store(values), mockk<Context>(), config)
        try { runCurrent(); assertFalse(model.builtInTmdbKeyInUse.value) }
        finally { close(model); Dispatchers.resetMain() }
    }

    @Test fun builtInAvailabilityDoesNotEnableTheUsersDisabledTmdbOptions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values = MutableStateFlow(NotflixIntegrationCredentials())
        val settingsState = MutableStateFlow(TmdbSettings(enabled = false, modernHomeEnabled = false))
        val dataStore = mockk<TmdbSettingsDataStore> { every { this@mockk.settings } returns settingsState }
        val config = NotflixIntegrationConfig(builtInTmdbApiKey = "synthetic-build-key") { values.value }
        val model = TmdbSettingsViewModel(dataStore, mockk(), mockk(), mockk(), store(values), config)
        try {
            runCurrent()
            assertTrue(model.uiState.value.apiKeyConfigured)
            assertFalse(model.uiState.value.enabled)
            assertFalse(model.uiState.value.modernHomeEnabled)
            settingsState.value = settingsState.value.copy(language = "es")
            runCurrent()
            assertTrue(model.uiState.value.apiKeyConfigured)
            assertEquals("es", model.uiState.value.language)
            assertFalse(model.uiState.value.enabled)
        } finally { close(model); Dispatchers.resetMain() }
    }
}
