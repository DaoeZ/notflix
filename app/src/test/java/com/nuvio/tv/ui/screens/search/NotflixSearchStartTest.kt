package com.nuvio.tv.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.*
import com.nuvio.tv.data.local.*
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.*
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsController
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class NotflixSearchStartTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { owner.clear(); Dispatchers.resetMain() }
    private val addons = MutableStateFlow<List<Addon>>(emptyList())
    private val profile = MutableStateFlow(1)
    private val addon = Addon("cinemeta", "Cinemeta", version = "1", description = null, logo = null,
        baseUrl = "https://catalog.invalid", catalogs = listOf(CatalogDescriptor(ContentType.MOVIE, id = "top", name = "Top")),
        types = listOf(ContentType.MOVIE), resources = emptyList())
    private fun item(id: String, rating: String? = null) = MetaPreview(id = id, type = ContentType.MOVIE, name = id,
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null, description = null,
        releaseInfo = null, imdbRating = null, genres = emptyList(), ageRating = rating)
    private fun row(id: String) = CatalogRow(addon.id, addon.name, addon.baseUrl, "top", "Top", ContentType.MOVIE, items = listOf(item(id)))

    private fun model(load: () -> Flow<NetworkResult<CatalogRow>>): SearchViewModel {
        val catalog = mockk<CatalogRepository>()
        every { catalog.getCatalog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers { load() }
        val layout = mockk<LayoutPreferenceDataStore> {
            every { discoverLocation } returns flowOf(DiscoverLocation.IN_SEARCH)
            every { posterCardWidthDp } returns flowOf(126)
            every { posterLabelsEnabled } returns flowOf(true)
            every { catalogAddonNameEnabled } returns flowOf(true)
            every { posterCardHeightDp } returns flowOf(189)
            every { posterCardCornerRadiusDp } returns flowOf(12)
            every { catalogTypeSuffixEnabled } returns flowOf(true)
            every { hideUnreleasedContent } returns flowOf(false)
        }
        val model = SearchViewModel(
            addonRepository = mockk { every { getInstalledAddons() } returns addons }, catalogRepository = catalog,
            metaRepository = mockk(relaxed = true), discoverSelectionDataStore = mockk(relaxed = true),
            layoutPreferenceDataStore = layout,
            searchHistoryDataStore = mockk { every { recentSearches } returns flowOf(emptyList()) },
            watchProgressRepository = mockk { every { observeWatchedMovieIds() } returns flowOf(emptySet()) },
            watchedSeriesStateHolder = mockk { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
            posterOptions = mockk<PosterOptionsController>(relaxed = true),
            serverCatalog = mockk<ServerCatalog> { every { searchAddons } returns flowOf(emptyList()) },
            ageGate = disabledAgeGate().apply { coEvery { currentLimitFromStore() } returns null },
            profileManager = mockk<ProfileManager> { every { activeProfileId } returns profile },
            hiddenTitlesRepository = mockk { every { observe(any()) } returns flowOf(emptySet()) },
            tmdbSettingsDataStore = mockk(relaxed = true), tmdbService = mockk(relaxed = true),
            tmdbMetadataService = mockk(relaxed = true), context = mockk<Context>(relaxed = true))
        owner.put("search", model)
        return model
    }

    @Test fun initialEmptyAddonSnapshotDoesNotPreventExploreLoadingWithoutTyping() = runTest {
        val vm = model { flowOf(NetworkResult.Success(row("first"))) }
        vm.setNotflixSearchActive(true); advanceUntilIdle()
        assertTrue(vm.uiState.value.discoverResults.isEmpty())
        addons.value = listOf(addon); advanceUntilIdle()
        assertEquals("", vm.uiState.value.query)
        assertEquals(listOf("first"), vm.uiState.value.discoverResults.map { it.id })
    }

    @Test fun typingBeforeAddonsArriveCannotLeaveExploreEmptyAfterErasing() = runTest {
        val vm = model { flowOf(NetworkResult.Success(row("first"))) }
        vm.setNotflixSearchActive(true); runCurrent()
        vm.onEvent(SearchEvent.QueryChanged("M")); runCurrent()
        addons.value = listOf(addon); advanceUntilIdle()
        assertEquals("M", vm.uiState.value.query)
        vm.onEvent(SearchEvent.QueryChanged("")); advanceUntilIdle()
        assertEquals(listOf("first"), vm.uiState.value.discoverResults.map { it.id })
    }

    @Test fun aNonCooperativeOldProfileResponseCannotReplaceTheNewStartPage() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val vm = model {
            val call = ++calls
            object : Flow<NetworkResult<CatalogRow>> {
                override suspend fun collect(collector: FlowCollector<NetworkResult<CatalogRow>>) {
                    if (call == 1) withContext(NonCancellable) { gate.await(); collector.emit(NetworkResult.Success(row("old"))) }
                    else collector.emit(NetworkResult.Success(row("new")))
                }
            }
        }
        addons.value = listOf(addon); vm.setNotflixSearchActive(true); runCurrent()
        profile.value = 2; runCurrent(); advanceUntilIdle()
        assertEquals(listOf("new"), vm.uiState.value.discoverResults.map { it.id })
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("new"), vm.uiState.value.discoverResults.map { it.id })
    }

    @Test fun leavingSearchRejectsEvenANonCooperativeLateResponse() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = model {
            object : Flow<NetworkResult<CatalogRow>> {
                override suspend fun collect(collector: FlowCollector<NetworkResult<CatalogRow>>) {
                    withContext(NonCancellable) { gate.await(); collector.emit(NetworkResult.Success(row("late"))) }
                }
            }
        }
        addons.value = listOf(addon); vm.setNotflixSearchActive(true); runCurrent()
        vm.setNotflixSearchActive(false); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertTrue(vm.uiState.value.discoverResults.isEmpty())
    }

    @Test fun emptySearchRecommendationsAreStrictlyFilteredForAgeAndHiddenTitles() {
        val items = listOf(item("allowed", "G"), item("hidden", "G"), item("unknown"), item("adult", "18"))
        assertEquals(listOf("allowed"), notflixSearchStartItems(items, AgeGateSnapshot(AgeLevel.TP, emptyMap()),
            setOf(hiddenTitleKey("movie", "hidden"))).map { it.id })
        assertEquals(4, notflixSearchStartItems(items, AgeGateSnapshot(null, emptyMap()), emptySet()).size)
    }
}
