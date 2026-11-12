package com.nuvio.tv.ui.screens.search

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.disabledAgeGate
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchLocalizedTitlesTest {
    private val preferences = MutableStateFlow(TmdbSettings(enabled = true, language = "es"))
    private val version = MutableStateFlow(0L)
    private val titles = ConcurrentHashMap<String, String>()
    private val tmdb = mockk<TmdbService>(relaxed = true)
    private val metadata = mockk<TmdbMetadataService>(relaxed = true) {
        every { enrichmentVersion } returns version
        every { cachedLocalizedTitle(any(), any(), any()) } answers { titles["${firstArg<String>()}:${thirdArg<String>()}"] }
    }
    private lateinit var vm: SearchViewModel

    @Before fun start() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val layout = mockk<LayoutPreferenceDataStore>(relaxed = true) {
            every { discoverLocation } returns flowOf(DiscoverLocation.OFF)
            every { posterCardWidthDp } returns flowOf(126)
            every { posterLabelsEnabled } returns flowOf(true)
            every { catalogAddonNameEnabled } returns flowOf(true)
            every { posterCardHeightDp } returns flowOf(189)
            every { posterCardCornerRadiusDp } returns flowOf(12)
            every { catalogTypeSuffixEnabled } returns flowOf(true)
            every { hideUnreleasedContent } returns flowOf(false)
        }
        vm = SearchViewModel(
            addonRepository = mockk(relaxed = true), catalogRepository = mockk(relaxed = true),
            metaRepository = mockk(relaxed = true), discoverSelectionDataStore = mockk(relaxed = true),
            layoutPreferenceDataStore = layout,
            searchHistoryDataStore = mockk(relaxed = true) { every { recentSearches } returns flowOf(emptyList()) },
            watchProgressRepository = mockk(relaxed = true) { every { observeWatchedMovieIds() } returns flowOf(emptySet()) },
            watchedSeriesStateHolder = mockk(relaxed = true) { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
            posterOptions = mockk(relaxed = true),
            serverCatalog = mockk { every { searchAddons } returns flowOf(emptyList()) },
            ageGate = disabledAgeGate(),
            profileManager = mockk(relaxed = true) { every { activeProfileId } returns MutableStateFlow(1) },
            hiddenTitlesRepository = mockk { every { observe(any()) } returns flowOf(emptySet()) },
            tmdbSettingsDataStore = mockk<TmdbSettingsDataStore> { every { settings } returns preferences },
            tmdbService = tmdb, tmdbMetadataService = metadata, context = mockk<Context>(relaxed = true)
        )
    }

    @After fun stop() = runBlocking {
        try {
            if (::vm.isInitialized) withTimeout(5_000) { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun item(id: Int) = MetaPreview(
        id = "tmdb:$id", type = ContentType.MOVIE, name = "Original $id", poster = null,
        posterShape = PosterShape.POSTER, background = null, logo = null,
        description = "Original description", releaseInfo = null, imdbRating = null, genres = emptyList()
    )

    private suspend fun awaitTitles(predicate: (Map<String, String>) -> Boolean) =
        withTimeout(5_000) { vm.localizedTitles.first(predicate) }

    @Test fun cachedTitlesArrivingLaterAndLanguageChangesUpdateWithoutRequests() = runBlocking {
        vm.requestLocalizedTitles(listOf(item(1)))
        titles["1:es"] = "Español"
        version.value++
        assertEquals("Español", awaitTitles { it.isNotEmpty() }["movie:tmdb:1"])
        preferences.value = preferences.value.copy(language = "fr")
        awaitTitles { it.isEmpty() }
        titles["1:fr"] = "Français"
        version.value++
        assertEquals("Français", awaitTitles { it.isNotEmpty() }["movie:tmdb:1"])
        coVerify(exactly = 0) { metadata.fetchEnrichment(any(), any(), any()) }
        coVerify(exactly = 0) { tmdb.ensureTmdbId(any(), any(), any()) }
    }

    @Test fun disablingBasicInfoAndStoppingTheScreenRemoveTranslatedTitles() = runBlocking {
        titles["1:es"] = "Español"
        vm.requestLocalizedTitles(listOf(item(1)))
        awaitTitles { it.isNotEmpty() }
        preferences.value = preferences.value.copy(useBasicInfo = false)
        awaitTitles { it.isEmpty() }
        preferences.value = preferences.value.copy(useBasicInfo = true)
        awaitTitles { it.isNotEmpty() }
        preferences.value = preferences.value.copy(enabled = false)
        awaitTitles { it.isEmpty() }
        preferences.value = preferences.value.copy(enabled = true)
        awaitTitles { it.isNotEmpty() }
        vm.requestLocalizedTitles(emptyList())
        assertTrue(vm.localizedTitles.value.isEmpty())
    }

    @Test fun lookupAndRetentionAreBoundedToTheCurrentWindow() = runBlocking {
        (0..149).forEach { titles["$it:es"] = "Title $it" }
        vm.requestLocalizedTitles((0..149).map(::item))
        assertEquals(96, awaitTitles { it.isNotEmpty() }.size)
        verify(exactly = 96) { metadata.cachedLocalizedTitle(any(), ContentType.MOVIE, "es") }
        vm.requestLocalizedTitles(listOf(item(120)))
        assertEquals(setOf("movie:tmdb:120"), awaitTitles { it.isNotEmpty() }.keys)
    }

    @Test fun anUnknownOrUntranslatedTitleDoesNotCreateWorkOrARow() = runBlocking {
        titles["1:es"] = "Español"
        vm.requestLocalizedTitles(listOf(item(1), item(2)))
        assertEquals(setOf("movie:tmdb:1"), awaitTitles { it.isNotEmpty() }.keys)
        coVerify(exactly = 0) { metadata.fetchEnrichment(any(), any(), any()) }
        coVerify(exactly = 0) { tmdb.ensureTmdbId(any(), any(), any()) }
    }
}
