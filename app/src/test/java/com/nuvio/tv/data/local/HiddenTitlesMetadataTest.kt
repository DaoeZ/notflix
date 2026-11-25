package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.nuvio.tv.ui.screens.settings.hiddenTitleLabel
import com.nuvio.tv.ui.screens.settings.cachedHiddenTmdbTitle
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.domain.model.ContentType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HiddenTitlesMetadataTest {
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    private val profiles = mutableMapOf<Int, Memory>()
    private val factory = mockk<ProfileDataStoreFactory> {
        every { get(any(), HiddenTitlesStore.FEATURE) } answers { profiles.getOrPut(firstArg()) { Memory() } }
    }

    @Test fun namesAndPostersSurviveStoreRecreationAndRemainProfileScoped() = runTest {
        val key = hiddenTitleKey("movie", "tmdb:316918")
        HiddenTitlesStore(factory).hide(1, key, "Título español", "https://poster.invalid/1")
        HiddenTitlesStore(factory).hide(2, key, "Other profile", null)
        val restored = HiddenTitlesStore(factory)
        assertEquals("Título español", restored.observeMetadata(1).first()[key]?.title)
        assertEquals("https://poster.invalid/1", restored.observeMetadata(1).first()[key]?.poster)
        assertEquals("Other profile", restored.observeMetadata(2).first()[key]?.title)
        restored.show(1, key)
        assertTrue(restored.observe(1).first().isEmpty())
        assertTrue(restored.observeMetadata(1).first().isEmpty())
        assertTrue(restored.observe(2).first().contains(key))
    }

    @Test fun oldHiddenKeysRemainHiddenAndCorruptOptionalMetadataCannotEraseThem() = runTest {
        val key = hiddenTitleKey("movie", "tmdb:316918")
        val prefs = Memory()
        prefs.edit { it[stringSetPreferencesKey("hidden_keys")] = setOf(key)
            it[stringPreferencesKey("hidden_metadata")] = "not json" }
        profiles[1] = prefs
        val store = HiddenTitlesStore(factory)
        assertEquals(setOf(key), store.observe(1).first())
        assertNull(store.observeMetadata(1).first()[key]?.title)
        store.hide(1, key, "Recovered name", null)
        store.hide(1, key) // legacy callers must not remove known metadata
        assertEquals("Recovered name", store.observeMetadata(1).first()[key]?.title)
    }

    @Test fun displayPrefersSavedNameThenExistingCacheThenTheOriginalId() {
        val key = "movie:tmdb:316918"
        assertEquals("Saved", hiddenTitleLabel(key, "Saved", "Cached"))
        assertEquals("Cached", hiddenTitleLabel(key, " ", "Cached"))
        assertEquals("tmdb:316918", hiddenTitleLabel(key, null, null))
        assertEquals("malformed", hiddenTitleLabel("malformed", null, ""))
    }

    @Test fun legacyTmdbKeysUseTheExistingLocalizedCacheWithoutRequestingAMapping() {
        val tmdb = mockk<TmdbService>()
        val metadata = mockk<TmdbMetadataService> {
            every { cachedLocalizedTitle("316918", ContentType.MOVIE, "es") } returns "Nombre en caché"
        }
        assertEquals("Nombre en caché", cachedHiddenTmdbTitle("movie:tmdb:316918", "es", tmdb, metadata))
        verify(exactly = 0) { tmdb.cachedTmdbId(any()) }
        verify(exactly = 0) { metadata.cachedOriginalTitle(any(), any()) }
    }

    @Test fun legacyImdbKeysUseOnlyACachedMappingAndAnOriginalCachedTitleOrKeepTheId() {
        val tmdb = mockk<TmdbService> {
            every { cachedTmdbId("tt1") } returns 21
            every { cachedTmdbId("tt-missing") } returns null
        }
        val metadata = mockk<TmdbMetadataService> {
            every { cachedLocalizedTitle("21", ContentType.SERIES, "es") } returns null
            every { cachedOriginalTitle("21", ContentType.SERIES) } returns "Original name"
        }
        assertEquals("Original name", cachedHiddenTmdbTitle("series:tt1", "es", tmdb, metadata))
        assertEquals("tt-missing", hiddenTitleLabel("movie:tt-missing", null,
            cachedHiddenTmdbTitle("movie:tt-missing", "es", tmdb, metadata)))
    }
}
