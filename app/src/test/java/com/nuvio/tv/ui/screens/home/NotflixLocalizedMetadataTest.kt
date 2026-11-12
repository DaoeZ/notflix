package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.disabledAgeGate
import com.nuvio.tv.core.tmdb.TmdbEnrichment
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * M61: Notflix chose its own presentation and no longer follows the official layout preset, so the
 * focus pipeline must localize titles and synopsis for it whatever `homeLayout` still says.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotflixLocalizedMetadataTest {

    private val itemId = "tt0077651"
    private val sourceUrl = "https://catalog.example"

    private val created = mutableListOf<HomeViewModel>()
    private val metadata = mutableMapOf<HomeViewModel, TmdbMetadataService>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = runBlocking {
        try {
            withTimeout(5_000) { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        } finally {
            created.clear()
            metadata.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the Notflix card takes the TMDB title and synopsis with the official layout on Modern`() = runBlocking {
        val viewModel = newViewModel(LocalizedEnrichment)
        viewModel.seedCatalog(item())
        viewModel.applyPresentation(homeLayout = HomeLayout.MODERN, notflix = true)

        viewModel.onItemFocusPipeline(item())
        val enriched = awaitEnriched(viewModel)

        assertEquals(LocalizedEnrichment.localizedTitle, enriched?.name)
        assertEquals(LocalizedEnrichment.description, enriched?.description)
    }

    @Test
    fun `the Notflix card still takes the TMDB title and synopsis when the stored layout is not Modern`() = runBlocking {
        val viewModel = newViewModel(LocalizedEnrichment)
        viewModel.seedCatalog(item())
        // The official preset is still stored in preferences, and M60 hides it from Settings.
        viewModel.applyPresentation(homeLayout = HomeLayout.CLASSIC, notflix = true)

        viewModel.onItemFocusPipeline(item())
        val enriched = awaitEnriched(viewModel)

        assertEquals(LocalizedEnrichment.localizedTitle, enriched?.name)
        assertEquals(LocalizedEnrichment.description, enriched?.description)
    }

    @Test
    fun `without a translation the original title and synopsis stay`() = runBlocking {
        val viewModel = newViewModel(null)
        viewModel.seedCatalog(item())
        viewModel.applyPresentation(homeLayout = HomeLayout.CLASSIC, notflix = true)

        viewModel.onItemFocusPipeline(item())
        val enriched = awaitEnriched(viewModel)

        assertEquals("Halloween", enriched?.name)
        assertEquals("Original English synopsis", enriched?.description)
    }

    @Test
    fun `a card lost from the cache is enriched again instead of falling back to the addon text`() = runBlocking {
        val viewModel = newViewModel(LocalizedEnrichment)
        viewModel.seedCatalog(item())
        viewModel.applyPresentation(homeLayout = HomeLayout.CLASSIC, notflix = true)
        viewModel.onItemFocusPipeline(item())
        awaitEnriched(viewModel)
        assertEquals(true, itemId in viewModel.prefetchedTmdbIds)

        // A reloaded row (or the 64-entry cache evicting the card) leaves the raw addon item while
        // the id stays marked as prefetched.
        viewModel._enrichedPreviews.update { it - itemId }
        viewModel.seedCatalog(item())

        viewModel.onItemFocusPipeline(item())
        val again = awaitEnriched(viewModel)

        assertEquals(LocalizedEnrichment.localizedTitle, again?.name)
        assertEquals(LocalizedEnrichment.description, again?.description)
    }

    private suspend fun awaitEnriched(viewModel: HomeViewModel): MetaPreview? {
        val reached = runCatching {
            withTimeout(5_000) {
                while (viewModel.enrichedPreviews.value[itemId] == null) delay(25)
            }
        }.isSuccess
        assertEquals("the focus pipeline must publish the enriched card", true, reached)
        withTimeout(5_000) { viewModel.tmdbEnrichFocusJob?.join() }
        return viewModel.enrichedPreviews.value[itemId]
    }

    @Test
    fun `resolved external metadata does not block restoring a card and makes no new fetch`() = runBlocking {
        val vm = newViewModel(LocalizedEnrichment)
        vm.seedCatalog(item())
        vm.applyPresentation(HomeLayout.CLASSIC, true)
        vm.onItemFocusPipeline(item())
        awaitEnriched(vm)
        vm.prefetchedExternalMetaIds.add(itemId)
        vm._enrichedPreviews.value = emptyMap()
        vm.onItemFocusPipeline(item())
        assertEquals(LocalizedEnrichment.localizedTitle, awaitEnriched(vm)?.name)
        coVerify(exactly = 1) { metadata.getValue(vm).fetchEnrichment(any(), any(), any()) }
        verify(exactly = 1) { metadata.getValue(vm).cachedEnrichment(any(), any(), "es") }
    }

    @Test
    fun `a lost presentation and a missing TMDB cache use addon text without fetching`() = runBlocking {
        val vm = newViewModel(null)
        vm.seedCatalog(item())
        vm.applyPresentation(HomeLayout.CLASSIC, true)
        vm.prefetchedTmdbIds.add(itemId)
        vm.enrichmentMergedIds.add(itemId)
        vm.onItemFocusPipeline(item())
        assertEquals(item().description, awaitEnriched(vm)?.description)
        coVerify(exactly = 0) { metadata.getValue(vm).fetchEnrichment(any(), any(), any()) }
    }

    @Test
    fun `Notflix preserves the addon text for a later language without a translation`() = runBlocking {
        val vm = newViewModel(LocalizedEnrichment)
        vm.seedCatalog(item())
        vm.applyPresentation(HomeLayout.CLASSIC, true)
        vm.onItemFocusPipeline(item())
        awaitEnriched(vm)
        assertEquals(item().name, vm.catalogsMap.getValue("seeded-row").items.single().name)
        assertEquals(item().description, vm.catalogsMap.getValue("seeded-row").items.single().description)

        vm.currentTmdbSettings = TmdbSettings(enabled = true, language = "fr")
        vm._enrichedPreviews.value = emptyMap()
        vm.prefetchedTmdbIds.clear()
        coEvery { metadata.getValue(vm).fetchEnrichment(any(), any(), "fr") } returns
            LocalizedEnrichment.copy(localizedTitle = null, description = null)
        vm.onItemFocusPipeline(item())
        assertEquals(item().name, awaitEnriched(vm)?.name)
        assertEquals(item().description, awaitEnriched(vm)?.description)
    }

    @Test
    fun `a collection item outside the catalog index still gets both translated fields`() = runBlocking {
        val vm = newViewModel(LocalizedEnrichment)
        vm.applyPresentation(HomeLayout.CLASSIC, true)
        vm.onItemFocusPipeline(item())
        assertEquals(LocalizedEnrichment.localizedTitle, awaitEnriched(vm)?.name)
        assertEquals(LocalizedEnrichment.description, awaitEnriched(vm)?.description)
    }

    @Test
    fun `Nuvio Classic keeps its original title and its existing prefetch gate`() = runBlocking {
        val vm = newViewModel(LocalizedEnrichment)
        vm.seedCatalog(item())
        vm.applyPresentation(HomeLayout.CLASSIC, false)
        vm.onItemFocusPipeline(item())
        assertEquals(item().name, awaitEnriched(vm)?.name)
        vm._enrichedPreviews.value = emptyMap()
        vm.onItemFocusPipeline(item())
        awaitEnriched(vm)
        coVerify(exactly = 1) { metadata.getValue(vm).fetchEnrichment(any(), any(), any()) }
        verify(exactly = 0) { metadata.getValue(vm).cachedEnrichment(any(), any(), any()) }
    }

    @Test
    fun `Nuvio still reads its current language after the focus debounce`() = runBlocking {
        val vm = newViewModel(LocalizedEnrichment)
        vm.seedCatalog(item())
        vm.applyPresentation(HomeLayout.CLASSIC, false)
        vm.onItemFocusPipeline(item())
        vm.currentTmdbSettings = TmdbSettings(enabled = true, language = "fr")
        awaitEnriched(vm)
        coVerify(exactly = 1) { metadata.getValue(vm).fetchEnrichment("948", ContentType.MOVIE, "fr") }
    }

    @Test
    fun `late results of a previous language are discarded in focus and adjacent prefetch`() = runBlocking {
        listOf(false, true).forEach { adjacent ->
            val vm = newViewModel(LocalizedEnrichment)
            vm.seedCatalog(item())
            vm.applyPresentation(HomeLayout.CLASSIC, true)
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val response = kotlinx.coroutines.CompletableDeferred<TmdbEnrichment>()
            coEvery { metadata.getValue(vm).fetchEnrichment(any(), any(), "es") } coAnswers {
                started.complete(Unit)
                response.await()
            }
            if (adjacent) vm.preloadAdjacentItemPipeline(item()) else vm.onItemFocusPipeline(item())
            withTimeout(5_000) { started.await() }
            vm.currentTmdbSettings = TmdbSettings(enabled = true, language = "fr")
            response.complete(LocalizedEnrichment)
            withTimeout(5_000) { (if (adjacent) vm.adjacentItemPrefetchJob else vm.tmdbEnrichFocusJob)?.join() }
            assertNull(vm.enrichedPreviews.value[itemId])
            assertEquals(false, itemId in vm.prefetchedTmdbIds)
        }
    }

    private fun HomeViewModel.applyPresentation(homeLayout: HomeLayout, notflix: Boolean) {
        _uiState.update { it.copy(homeLayout = homeLayout, notflixPresentationActive = notflix) }
    }

    private fun item() = MetaPreview(
        id = itemId,
        type = ContentType.MOVIE,
        name = "Halloween",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Original English synopsis",
        releaseInfo = "1978",
        imdbRating = null,
        genres = emptyList(),
        sourceAddonBaseUrl = sourceUrl
    )

    private fun HomeViewModel.seedCatalog(item: MetaPreview) {
        synchronized(catalogStateLock) {
            val rowKey = "seeded-row"
            catalogsMap[rowKey] = com.nuvio.tv.domain.model.CatalogRow(
                addonId = "catalog.addon",
                addonName = "Catalog Addon",
                addonBaseUrl = sourceUrl,
                catalogId = "test.catalog",
                catalogName = "Test Catalog",
                type = ContentType.MOVIE,
                items = listOf(item)
            )
            catalogItemKeyToRows(rowKey, item.id)
        }
    }

    private fun HomeViewModel.catalogItemKeyToRows(rowKey: String, id: String) {
        catalogItemKeyIndex.getOrPut(id) { mutableSetOf() }.add(rowKey)
    }

    private fun newViewModel(enrichment: TmdbEnrichment?): HomeViewModel {
        val profileManager = mockk<com.nuvio.tv.core.profile.ProfileManager>(relaxed = true) {
            every { activeProfileReady } returns MutableStateFlow(false)
            every { activeProfileId } returns MutableStateFlow(1)
        }
        val cwEnrichmentCache =
            mockk<com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache>(relaxed = true) {
                every { cacheCleared } returns MutableStateFlow(0)
            }
        val watchProgressRepository =
            mockk<com.nuvio.tv.domain.repository.WatchProgressRepository>(relaxed = true) {
                every { getAllEpisodeProgress(any()) } returns flowOf(emptyMap())
                every { observeWatchedMovieIds() } returns flowOf(emptySet())
            }
        val watchedSeriesStateHolder =
            mockk<com.nuvio.tv.data.local.WatchedSeriesStateHolder>(relaxed = true) {
                every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet())
            }
        val hiddenTitlesRepository = mockk<com.nuvio.tv.domain.repository.HiddenTitlesRepository> {
            every { observe(any()) } returns flowOf(emptySet())
        }
        val tmdbService = mockk<TmdbService>(relaxed = true) {
            coEvery { ensureTmdbId(any(), any(), any()) } returns "948"
            every { cachedTmdbId(any()) } returns 948
        }
        val tmdbMetadataService = mockk<TmdbMetadataService>(relaxed = true) {
            coEvery { fetchEnrichment(any(), any(), any()) } returns enrichment
            every { cachedEnrichment(any(), any(), any()) } returns enrichment
        }
        // The focus pipeline also warms the detail screen in the background; a flow that never
        // completes keeps that warm-up suspended instead of completing empty and throwing.
        val metaRepository = mockk<com.nuvio.tv.domain.repository.MetaRepository>(relaxed = true) {
            every { getMetaFromAllAddons(any(), any(), any()) } returns kotlinx.coroutines.flow.MutableSharedFlow()
        }
        val context = mockk<Context>(relaxed = true)
        val viewModel = HomeViewModel(
            appContext = context,
            addonRepository = mockk(relaxed = true),
            startupSyncService = mockk(relaxed = true),
            catalogRepository = mockk(relaxed = true),
            watchProgressRepository = watchProgressRepository,
            libraryRepository = mockk(relaxed = true),
            metaRepository = metaRepository,
            episodeShuffleStore = mockk(relaxed = true),
            episodeShuffle = com.nuvio.tv.domain.model.EpisodeShuffle(),
            collectionsDataStore = mockk(relaxed = true),
            layoutPreferenceDataStore = mockk(relaxed = true),
            playerSettingsDataStore = mockk(relaxed = true),
            tmdbSettingsDataStore = mockk(relaxed = true),
            mdbListSettingsDataStore = mockk(relaxed = true),
            traktSettingsDataStore = mockk(relaxed = true),
            authSessionNoticeDataStore = mockk(relaxed = true),
            tmdbService = tmdbService,
            tmdbMetadataService = tmdbMetadataService,
            mdbListRepository = mockk(relaxed = true),
            imdbEpisodeRatingsRepository = mockk(relaxed = true),
            trailerService = mockk(relaxed = true),
            watchedSeriesStateHolder = watchedSeriesStateHolder,
            cwEnrichmentCache = cwEnrichmentCache,
            profileManager = profileManager,
            ageGate = disabledAgeGate(),
            hiddenTitlesRepository = hiddenTitlesRepository,
            tmdbAvailability = mockk(relaxed = true),
            tvRecommendationManager = mockk(relaxed = true),
            serverCatalog = mockk(relaxed = true)
        )
        viewModel.startupGracePeriodActive = false
        viewModel.externalMetaPrefetchEnabled = false
        viewModel.currentTmdbSettings = TmdbSettings(enabled = true, language = "es")
        created += viewModel
        metadata[viewModel] = tmdbMetadataService
        return viewModel
    }

    private companion object {
        val LocalizedEnrichment = TmdbEnrichment(
            localizedTitle = "La noche de Halloween",
            description = "Quince años después de asesinar a su hermana...",
            genres = emptyList(),
            backdrop = null,
            logo = null,
            poster = null,
            directorMembers = emptyList(),
            writerMembers = emptyList(),
            castMembers = emptyList(),
            releaseInfo = "1978",
            rating = 7.6,
            runtimeMinutes = 91,
            director = emptyList(),
            writer = emptyList(),
            productionCompanies = emptyList(),
            networks = emptyList(),
            ageRating = null,
            status = null,
            countries = null,
            language = "en",
            collectionId = null,
            collectionName = null
        )
    }
}
