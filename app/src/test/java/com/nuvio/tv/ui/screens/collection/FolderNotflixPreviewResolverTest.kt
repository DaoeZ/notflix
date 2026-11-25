package com.nuvio.tv.ui.screens.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.trailer.TrailerPlaybackSource
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.home.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Real folder request and real adapter; only repositories and trailer transport are replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderNotflixPreviewResolverTest {
    private val dispatcher = StandardTestDispatcher()
    private val tmdb = mockk<TmdbService>()
    private val trailers = mockk<TrailerService>()
    private val activeProfileIdFlow = MutableStateFlow(1)
    private val hidden = mockk<com.nuvio.tv.domain.repository.HiddenTitlesRepository>(relaxed = true) {
        every { observe(any()) } returns MutableStateFlow(emptySet())
    }
    private val kidsSources = mockk<com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolver>()
    private val kidsAddons = mockk<com.nuvio.tv.domain.repository.AddonRepository>()
    private val kidsCatalogs = mockk<com.nuvio.tv.domain.repository.CatalogRepository>()
    private val kidsAgeGate = mockk<com.nuvio.tv.core.profile.ActiveProfileAgeGate>()
    private val kidsLayout = mockk<com.nuvio.tv.data.local.LayoutPreferenceDataStore>(relaxed = true) {
        every { customPosterUrlPattern } returns flowOf("")
        every { customPosterEnabledScreens } returns flowOf(emptySet())
    }
    private lateinit var model: FolderDetailViewModel
    private val item = MetaPreview("id", ContentType.MOVIE, name = "Synthetic", poster = null,
        posterShape = PosterShape.POSTER, background = null, logo = null, description = null,
        releaseInfo = "2026", imdbRating = null, genres = emptyList())

    private fun lease(serial: Long = 1) = PreviewLease(
        PreviewVisit(PreviewBinding(AppearanceContext(1, 1), 1), "tab|movie:id", "movie:id", serial),
        serial, item, false)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { tmdb.ensureTmdbId(any(), any(), any()) } returns "123"
        coEvery { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any(), any()) } returns
            TrailerPlaybackSource("https://unit.invalid/video", "https://unit.invalid/audio")
        model = FolderDetailViewModel(
            appContext = mockk {
                every { resources.configuration.locales } returns mockk {
                    every { get(0) } returns java.util.Locale.forLanguageTag("es-ES")
                }
            },
            savedStateHandle = SavedStateHandle(),
            collectionsDataStore = mockk { every { collections } returns flowOf(emptyList()) },
            addonRepository = kidsAddons, catalogRepository = kidsCatalogs, layoutPreferenceDataStore = kidsLayout,
            watchProgressRepository = mockk { every { observeWatchedMovieIds() } returns MutableStateFlow(emptySet()) },
            watchedSeriesStateHolder = mockk { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
            tmdbService = tmdb, tmdbMetadataService = mockk(), tmdbSettingsDataStore = mockk(),
            mdbListRepository = mockk(),
            mdbListSettingsDataStore = mockk { every { settings } returns flowOf(MDBListSettings()) },
            metaRepository = mockk(), trailerService = trailers, tmdbCollectionSourceResolver = kidsSources,
            traktPublicListSourceResolver = mockk(), ageGate = kidsAgeGate,
            playerSettingsDataStore = mockk { every { playerSettings } returns flowOf(PlayerSettings()) },
            hiddenTitlesRepository = hidden,
            profileManager = mockk { every { activeProfileId } returns activeProfileIdFlow },
            posterOptions = mockk(relaxed = true)
        )
    }

    @After fun tearDown() = runTest(dispatcher) {
        model.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        Dispatchers.resetMain()
    }

    @Test fun coldFolderRequestPublishesVideoAndAudioAfterTheResolverClaimsIt() = runTest(dispatcher) {
        val resolver = OfficialNotflixPreviewResolver(FolderNotflixPreviewRequestHost(model))
        val result = async { resolver.resolve(lease()) { true } }
        advanceUntilIdle()
        assertEquals(PreviewSource("https://unit.invalid/video", "https://unit.invalid/audio"), result.await())
        assertFalse(model.trailerPreviewLoading("id"))
        coVerify(exactly = 1) { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any(), "es-ES") }
    }

    @Test fun cancelledVisitCannotClearTheLoadingStateOrSourceOfTheNextVisit() = runTest(dispatcher) {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any(), any()) } coAnswers {
            if (++calls == 1) {
                withContext(NonCancellable) { firstGate.await() }
                TrailerPlaybackSource("https://unit.invalid/old")
            } else {
                secondGate.await()
                TrailerPlaybackSource("https://unit.invalid/new")
            }
        }
        try {
            val resolver = OfficialNotflixPreviewResolver(FolderNotflixPreviewRequestHost(model))
            val old = lease()
            val first = async { resolver.resolve(old) { true } }
            runCurrent(); advanceTimeBy(181); runCurrent()
            assertTrue(model.trailerPreviewLoading("id"))
            resolver.cancel(old)
            assertFalse(model.trailerPreviewLoading("id"))
            val next = async { resolver.resolve(lease(2)) { true } }
            runCurrent(); advanceTimeBy(181); runCurrent()
            assertTrue(model.trailerPreviewLoading("id"))
            firstGate.complete(Unit); runCurrent()
            assertNull(first.await())
            assertTrue(model.trailerPreviewLoading("id"))
            resolver.cancel(old)
            assertTrue(requireNotNull(model.trailerPreviewJobRef).isActive)
            secondGate.complete(Unit); advanceUntilIdle()
            assertEquals("https://unit.invalid/new", next.await()?.video)
            assertEquals("https://unit.invalid/new", model.trailerPreviewVideoUrl("id"))
        } finally {
            firstGate.complete(Unit)
            secondGate.complete(Unit)
        }
    }

    @Test fun originalNuvioFolderRequestStillResolvesWithoutANotflixVisit() = runTest(dispatcher) {
        model.requestTrailerPreview("id", "Synthetic", "2026", "movie")
        advanceUntilIdle()
        assertEquals("https://unit.invalid/video", model.trailerPreviewVideoUrl("id"))
    }

    @Test fun cachedReentryDoesNotResolveTheTrailerAgain() = runTest(dispatcher) {
        val resolver = OfficialNotflixPreviewResolver(FolderNotflixPreviewRequestHost(model))
        assertNotNull(async { resolver.resolve(lease()) { true } }.also { advanceUntilIdle() }.await())
        resolver.cancel(lease())
        assertNotNull(async { resolver.resolve(lease(2)) { true } }.also { advanceUntilIdle() }.await())
        coVerify(exactly = 1) { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any(), "es-ES") }
    }
    @Test fun undoWaitsForTheHideAndUsesTheCapturedProfileEvenAfterSwitching() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        coEvery { hidden.hide(any(), any(), any(), any()) } coAnswers { // the folder now saves title and poster for the hidden list
            gate.await(); writes += "hide:${firstArg<Int>()}:${secondArg<String>()}"
        }
        coEvery { hidden.show(any(), any()) } coAnswers {
            writes += "show:${firstArg<Int>()}:${secondArg<String>()}"
        }
        try {
            model.hideTitle(item); runCurrent()
            assertEquals("movie:id", model.uiState.value.hiddenTitleUndo?.key)
            model.undoHideTitle(); runCurrent()
            assertNull(model.uiState.value.hiddenTitleUndo)
            activeProfileIdFlow.value = 2; runCurrent()
            assertTrue(writes.isEmpty())
            gate.complete(Unit); runCurrent()
            assertEquals(listOf("hide:1:movie:id", "show:1:movie:id"), writes)
        } finally { gate.complete(Unit) }
    }

    @Test fun hiddenTitleUndoExpiresAfterFiveSeconds() = runTest(dispatcher) {
        model.hideTitle(item); runCurrent()
        advanceTimeBy(4_999); runCurrent()
        assertNotNull(model.uiState.value.hiddenTitleUndo)
        advanceTimeBy(1); runCurrent()
        assertNull(model.uiState.value.hiddenTitleUndo)
        coVerify(exactly = 1) { hidden.hide(1, "movie:id", any(), any()) }
        coVerify(exactly = 0) { hidden.show(any(), any()) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedDiscoverTab(kids: Boolean): TmdbCollectionSource {
        val source = TmdbCollectionSource(sourceType = TmdbCollectionSourceType.DISCOVER,
            title = "Kids", mediaType = TmdbCollectionMediaType.MOVIE)
        val fields = FolderDetailViewModel::class.java
        fields.getDeclaredField("derivedKidsFolder").apply { isAccessible = true }.setBoolean(model, kids)
        fields.getDeclaredField("folderId").apply { isAccessible = true }.set(model, "kids-animacion")
        val state = fields.getDeclaredField("_uiState").apply { isAccessible = true }.get(model)
            as MutableStateFlow<FolderDetailUiState>
        state.value = state.value.copy(folder = CollectionFolder("kids-animacion", "Kids", sources = listOf(source)),
            tabs = listOf(FolderTab("Kids", source = source)))
        return source
    }

    private fun loadSource(source: CollectionSource) {
        FolderDetailViewModel::class.java.getDeclaredMethod("loadSourceForTab", Int::class.javaPrimitiveType,
            CollectionSource::class.java).apply { isAccessible = true }.invoke(model, 0, source)
    }

    /** First pages of a KIDS tab go through the progressive filter; it publishes the final page at once here. */
    private fun stubKidsProgressiveFilter(filter: (List<MetaPreview>) -> List<MetaPreview>) {
        coEvery { kidsAgeGate.filterKidsProgressively(any(), com.nuvio.tv.core.profile.AgeLevel.TP, any()) } coAnswers {
            thirdArg<(List<MetaPreview>, Boolean) -> Unit>().invoke(filter(firstArg()), true)
        }
    }

    @Test fun kidsSeeAllFailureFallsBackAndContinuesWithTheOfficialAddonPaginator() = runTest(dispatcher) {
        runCurrent() // Complete the unrelated empty-folder initialization before seeding the route.
        val source = seedDiscoverTab(kids = true)
        every { kidsSources.resolve(source, any()) } returns flowOf(com.nuvio.tv.core.network.NetworkResult.Error("down"))
        val addon = Addon("com.linvo.cinemeta", "Cinemeta", version = "1", description = null,
            logo = null, baseUrl = "https://unit.invalid", catalogs = emptyList(),
            types = listOf(ContentType.MOVIE), resources = emptyList())
        every { kidsAddons.getInstalledAddons() } returns flowOf(listOf(addon))
        val skips = mutableListOf<Int>()
        every { kidsCatalogs.getCatalog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            skips += arg<Int>(6)
            flowOf(com.nuvio.tv.core.network.NetworkResult.Success(CatalogRow(addon.id, addon.name,
                addon.baseUrl, "top", "Kids", ContentType.MOVIE,
                items = listOf(item.copy(id = "item-${skips.size}", ageRating = "TP")),
                hasMore = true, supportsSkip = true, skipStep = 100)))
        }
        stubKidsProgressiveFilter { it }
        coEvery { kidsAgeGate.filterForLimit(any(), com.nuvio.tv.core.profile.AgeLevel.TP) } coAnswers { firstArg() }
        loadSource(source); runCurrent()
        var tab = model.uiState.value.tabs.single()
        assertTrue(tab.source is AddonCatalogCollectionSource)
        assertEquals("item-1", tab.catalogRow?.items?.single()?.id)
        assertNull(tab.error)
        model.loadMoreItems(0); runCurrent()
        tab = model.uiState.value.tabs.single()
        assertEquals(listOf("item-1", "item-2"), tab.catalogRow?.items?.map { it.id })
        assertEquals(2, skips.size)
        assertEquals(0, skips.first())
        assertTrue(skips.last() > 0)
        verify(exactly = 1) { kidsSources.resolve(source, any()) }
        // Every page is age-filtered: the first progressively, the next one by the official paginator.
        coVerify(exactly = 1) { kidsAgeGate.filterKidsProgressively(any(), com.nuvio.tv.core.profile.AgeLevel.TP, any()) }
        coVerify(exactly = 1) { kidsAgeGate.filterForLimit(any(), com.nuvio.tv.core.profile.AgeLevel.TP) }
    }

    // M68 reserve 1: a page the age filter empties used to end paging although later pages had titles.
    @Test fun kidsPagingSkipsPagesTheAgeFilterEmptiesUpToALimit() = runTest(dispatcher) {
        runCurrent()
        val source = seedDiscoverTab(kids = true)
        val pages = mutableListOf<Int>()
        every { kidsSources.resolve(source, any()) } answers {
            val page = secondArg<Int>(); pages += page
            val rating = if (page == 1 || page == 3) "TP" else "18"
            flowOf(com.nuvio.tv.core.network.NetworkResult.Success(CatalogRow("tmdb", "TMDB", "", "kids", "Kids",
                ContentType.MOVIE, items = listOf(item.copy(id = "p$page", ageRating = rating)),
                hasMore = true, currentPage = page)))
        }
        stubKidsProgressiveFilter { items -> items.filter { it.ageRating == "TP" } }
        loadSource(source); runCurrent()
        model.loadMoreItems(0); runCurrent()
        assertEquals(listOf("p1", "p3"), model.uiState.value.tabs.single().catalogRow?.items?.map { it.id })
        assertEquals(listOf(1, 2, 3), pages)
        // From here every page is adult-only: paging stops after EMPTY_PAGE_SKIPS empty pages.
        every { kidsSources.resolve(source, any()) } answers {
            val page = secondArg<Int>(); pages += page
            flowOf(com.nuvio.tv.core.network.NetworkResult.Success(CatalogRow("tmdb", "TMDB", "", "kids", "Kids",
                ContentType.MOVIE, items = listOf(item.copy(id = "p$page", ageRating = "18")), hasMore = true, currentPage = page)))
        }
        model.loadMoreItems(0); runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), pages)
        assertFalse(model.uiState.value.tabs.single().catalogRow!!.hasMore)
    }

    @Test fun anOrdinaryFolderKeepsItsExistingTmdbErrorInsteadOfSwitchingSources() = runTest(dispatcher) {
        runCurrent()
        val source = seedDiscoverTab(kids = false)
        every { kidsSources.resolve(source, any()) } returns flowOf(com.nuvio.tv.core.network.NetworkResult.Error("down"))
        loadSource(source); runCurrent()
        assertEquals(source, model.uiState.value.tabs.single().source)
        assertEquals("down", model.uiState.value.tabs.single().error)
        verify(exactly = 0) { kidsAddons.getInstalledAddons() }
    }

}
