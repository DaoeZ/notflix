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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

/**
 * Notflix search asks TMDB for the translated title and poster of the results it shows
 * ([TmdbService.searchArtwork]) and falls back to the enrichment title already in memory.
 * Translations follow the app language passed by the screen, not the TMDB setting.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchLocalizedTitlesTest {
    private val preferences = MutableStateFlow(TmdbSettings(enabled = true, language = "es"))
    private val titles = ConcurrentHashMap<String, String>()
    private val tmdb = mockk<TmdbService>(relaxed = true) {
        coEvery { searchArtwork(any(), any(), any()) } returns null
    }
    private val metadata = mockk<TmdbMetadataService>(relaxed = true) {
        every { enrichmentVersion } returns MutableStateFlow(0L)
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

    @Test fun tmdbArtworkTranslatesTitleAndPosterInTheAppLanguage() = runBlocking {
        coEvery { tmdb.searchArtwork("tmdb:1", "movie", "es-ES") } returns
            TmdbService.SearchArtwork("Espanol", "https://image.unit/es.jpg")
        vm.setNotflixSearchActive(true, "es-ES")
        vm.requestLocalizedTitles(listOf(item(1)))
        assertEquals("Espanol", awaitTitles { it.isNotEmpty() }["movie:tmdb:1"])
        assertEquals("https://image.unit/es.jpg", vm.localizedPosters.value["movie:tmdb:1"])
    }

    @Test fun withoutTmdbArtworkOnlyAnEnrichmentTitleAlreadyInMemoryIsUsed() = runBlocking {
        titles["1:en-US"] = "Cached"
        vm.requestLocalizedTitles(listOf(item(1), item(2)))
        assertEquals(mapOf("movie:tmdb:1" to "Cached"), awaitTitles { it.isNotEmpty() })
        assertTrue(vm.localizedPosters.value.isEmpty())
        coVerify(exactly = 0) { metadata.fetchEnrichment(any(), any(), any()) }
    }

    @Test fun changingTheAppLanguageDropsOldTranslationsAndAsksInTheNewOne() = runBlocking {
        coEvery { tmdb.searchArtwork("tmdb:1", "movie", "es-ES") } returns TmdbService.SearchArtwork("Espanol", null)
        coEvery { tmdb.searchArtwork("tmdb:1", "movie", "fr-FR") } returns TmdbService.SearchArtwork("Francais", null)
        vm.setNotflixSearchActive(true, "es-ES")
        vm.requestLocalizedTitles(listOf(item(1)))
        awaitTitles { it["movie:tmdb:1"] == "Espanol" }
        vm.setNotflixSearchActive(true, "fr-FR")
        assertTrue(vm.localizedTitles.value.isEmpty())
        vm.requestLocalizedTitles(listOf(item(1)))
        assertEquals("Francais", awaitTitles { it.isNotEmpty() }["movie:tmdb:1"])
    }

    @Test fun disabledTmdbMakesNoRequestsAndShowsNoTranslation() = runBlocking {
        preferences.value = preferences.value.copy(enabled = false)
        titles["1:en-US"] = "Cached"
        vm.requestLocalizedTitles(listOf(item(1)))
        assertTrue(vm.localizedTitles.value.isEmpty())
        coVerify(exactly = 0) { tmdb.searchArtwork(any(), any(), any()) }
    }

    @Test fun retentionIsBoundedToTheMostRecentEntries() = runBlocking {
        coEvery { tmdb.searchArtwork(any(), "movie", any()) } answers {
            TmdbService.SearchArtwork("T ${firstArg<String>()}", null)
        }
        vm.requestLocalizedTitles((0 until 200).map(::item))
        val kept = awaitTitles { it.containsKey("movie:tmdb:199") }
        assertEquals(160, kept.size)
        assertTrue("movie:tmdb:0" !in kept)
    }
}
