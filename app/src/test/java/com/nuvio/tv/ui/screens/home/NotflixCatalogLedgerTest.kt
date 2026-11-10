package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.profile.disabledAgeGate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*

class NotflixCatalogLedgerTest {
    private val descriptor = NotflixCatalogLedger.Descriptor("a_movie_popular", "Popular", "stable-row")
    private fun catalog(items: List<MetaPreview>) = CatalogRow("a", "Addon", "https://example.invalid", "popular", "Popular", ContentType.MOVIE, items = items)
    private fun item(id: String) = MetaPreview(id, ContentType.MOVIE, name = id, poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = null, releaseInfo = null, imdbRating = null, genres = emptyList())
    private fun ledger() = NotflixCatalogLedger().apply { begin(1); configure(1, listOf(descriptor)) }

    @Test fun pendingSnapshotContainsDescriptorAndNoInventedContent() {
        val snapshot = ledger().snapshot(1, 2, emptyMap(), NotflixCatalogSnapshot())
        assertTrue(snapshot.configured); assertEquals(2, snapshot.context); assertEquals(1L, snapshot.generation)
        assertEquals(NotflixRowPhase.PENDING, snapshot.rows.single().phase)
        assertNull(snapshot.rows.single().catalog)
    }
    @Test fun successPublishesExactDataAndTerminalPhaseTogether() {
        val l = ledger(); val data = catalog(listOf(item("one")))
        l.outcome(1, descriptor.key, NotflixRowPhase.READY)
        val snapshot = l.snapshot(1, 1, mapOf(descriptor.key to data), NotflixCatalogSnapshot())
        assertEquals(NotflixRowPhase.READY, snapshot.rows.single().phase)
        assertSame(data, snapshot.rows.single().catalog)
    }
    @Test fun failedRefreshRetainsDataButSuccessfulEmptyIsDefinitive() {
        val l = ledger(); val data = catalog(listOf(item("one")))
        l.outcome(1, descriptor.key, NotflixRowPhase.READY)
        val previous = l.snapshot(1, 1, mapOf(descriptor.key to data), NotflixCatalogSnapshot())
        l.outcome(1, descriptor.key, NotflixRowPhase.ERROR)
        val failed = l.snapshot(1, 1, emptyMap(), previous)
        assertEquals(listOf("one"), failed.rows.single().catalog?.items?.map { it.id })
        assertEquals(NotflixRowPhase.ERROR, failed.rows.single().phase)
        l.outcome(1, descriptor.key, NotflixRowPhase.READY)
        val empty = l.snapshot(1, 1, mapOf(descriptor.key to catalog(emptyList())), failed)
        assertTrue(empty.rows.single().catalog!!.items.isEmpty())
    }
    @Test fun newGenerationDoesNotTreatUnconfiguredGapOrOldResultAsRemoval() {
        val l = ledger(); val previous = l.snapshot(1, 1, emptyMap(), NotflixCatalogSnapshot())
        l.begin(2)
        val gap = l.snapshot(2, 1, emptyMap(), previous)
        assertFalse(gap.configured)
        l.configure(2, listOf(descriptor))
        l.outcome(1, descriptor.key, NotflixRowPhase.READY)
        val current = l.snapshot(2, 1, emptyMap(), previous)
        assertEquals(2L, current.generation); assertEquals(NotflixRowPhase.PENDING, current.rows.single().phase)
    }
    @Test fun explicitEmptyConfigurationIsAuthoritative() {
        val l = ledger(); l.begin(2); l.configure(2, emptyList())
        val empty = l.snapshot(2, 1, emptyMap(), NotflixCatalogSnapshot())
        assertTrue(empty.configured); assertTrue(empty.rows.isEmpty())
    }
    @Test fun returnedSnapshotIsImmutableWhenNewOutcomeArrives() {
        val l = ledger(); val captured = l.snapshot(1, 1, emptyMap(), NotflixCatalogSnapshot())
        l.outcome(1, descriptor.key, NotflixRowPhase.READY)
        assertEquals(NotflixRowPhase.PENDING, captured.rows.single().phase)
        assertNull(captured.rows.single().catalog)
    }

    @Test fun presentationRetainsExactPreviousDataUntilRowOutcomeIsTerminal() {
        val original = NotflixCatalogRow("stable", descriptor.key, "Popular", NotflixRowPhase.READY, catalog(listOf(item("one"), item("two"))))
        val memory = NotflixHomeMemory().apply { retainedCatalog = NotflixCatalogSnapshot(1, 1, true, listOf(original)) }
        for (phase in listOf(NotflixRowPhase.PENDING, NotflixRowPhase.ERROR)) {
            val incoming = HomeUiState(notflixCatalog = NotflixCatalogSnapshot(1, 2, true,
                listOf(original.copy(phase = phase, catalog = catalog(listOf(item("one")))))))
            val retained = retainNotflixPresentation(incoming, memory)
            assertEquals(listOf("one", "two"), retained.notflixCatalog.rows.single().catalog!!.items.map { it.id })
            assertEquals(phase, retained.notflixCatalog.rows.single().phase)
            assertEquals(listOf("one"), incoming.notflixCatalog.rows.single().catalog!!.items.map { it.id })
        }
        val terminal = HomeUiState(notflixCatalog = NotflixCatalogSnapshot(1, 2, true,
            listOf(original.copy(catalog = catalog(emptyList())))))
        assertTrue(retainNotflixPresentation(terminal, memory).notflixCatalog.rows.single().catalog!!.items.isEmpty())
    }

    @Test fun presentationNeverRetainsDataAcrossProfileContextsOrRemovedDescriptors() {
        val original = NotflixCatalogRow("stable", descriptor.key, "Popular", NotflixRowPhase.READY, catalog(listOf(item("one"))))
        val memory = NotflixHomeMemory().apply { retainedCatalog = NotflixCatalogSnapshot(1, 1, true, listOf(original)) }
        val newProfile = HomeUiState(notflixCatalog = NotflixCatalogSnapshot(2, 2, false))
        assertTrue(retainNotflixPresentation(newProfile, memory).notflixCatalog.rows.isEmpty())
        val removed = HomeUiState(notflixCatalog = NotflixCatalogSnapshot(1, 2, true))
        assertTrue(retainNotflixPresentation(removed, memory).notflixCatalog.rows.isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun realOfficialPipelineProvidesFullRowsForEveryLayoutWithoutChangingLayout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (layout in HomeLayout.entries) {
                val vm = model()
                try {
                    val data = catalog((0..29).map { item("item$it") })
                    val key = data.legacyKey()
                    vm._uiState.value = HomeUiState(homeLayout = layout, layoutPreferencesReady = true)
                    vm.catalogLoadGeneration = 1
                    synchronized(vm.catalogStateLock) {
                        vm.catalogOrder.add(key)
                        vm.catalogsMap[key] = data
                        vm.notflixCatalogLedger.begin(1)
                        vm.notflixCatalogLedger.configure(1, listOf(NotflixCatalogLedger.Descriptor(key, data.catalogName, data.stableKey())))
                        vm.notflixCatalogLedger.outcome(1, key, NotflixRowPhase.READY)
                    }
                    vm.currentTmdbSettings = TmdbSettings(enabled = false)
                    vm.updateCatalogRowsPipeline()
                    val result = vm._uiState.value
                    assertEquals(layout, result.homeLayout)
                    assertEquals(30, result.notflixCatalog.rows.single().catalog!!.items.size)
                    assertEquals(NotflixRowPhase.READY, result.notflixCatalog.rows.single().phase)
                    assertEquals(1, result.notflixCatalog.context)
                    assertEquals(if (layout == HomeLayout.MODERN) 30 else 24, result.catalogRows.single().items.size)
                } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            }
        } finally { Dispatchers.resetMain() }
    }

    private fun model(): HomeViewModel {
        val manager = mockk<ProfileManager>(relaxed = true) {
            every { activeProfileReady } returns MutableStateFlow(false)
            every { activeProfileId } returns MutableStateFlow(1)
        }
        val watchProgress = mockk<com.nuvio.tv.domain.repository.WatchProgressRepository>(relaxed = true) {
            every { getAllEpisodeProgress(any()) } returns flowOf(emptyMap())
            every { observeWatchedMovieIds() } returns flowOf(emptySet())
        }
        val watchedSeries = mockk<com.nuvio.tv.data.local.WatchedSeriesStateHolder>(relaxed = true) {
            every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet())
        }
        val hiddenTitlesRepository = mockk<com.nuvio.tv.domain.repository.HiddenTitlesRepository> {
            every { observe(any()) } returns flowOf(emptySet())
        }
        return HomeViewModel(
            appContext = mockk(relaxed = true), addonRepository = mockk(relaxed = true),
            startupSyncService = mockk(relaxed = true), catalogRepository = mockk(relaxed = true),
            watchProgressRepository = watchProgress,
            libraryRepository = mockk(relaxed = true), metaRepository = mockk(relaxed = true),
            episodeShuffleStore = mockk(relaxed = true), episodeShuffle = EpisodeShuffle(),
            collectionsDataStore = mockk(relaxed = true), layoutPreferenceDataStore = mockk(relaxed = true),
            playerSettingsDataStore = mockk(relaxed = true), tmdbSettingsDataStore = mockk(relaxed = true),
            mdbListSettingsDataStore = mockk(relaxed = true), traktSettingsDataStore = mockk(relaxed = true),
            authSessionNoticeDataStore = mockk(relaxed = true), tmdbService = mockk(relaxed = true),
            tmdbMetadataService = mockk(relaxed = true), mdbListRepository = mockk(relaxed = true),
            imdbEpisodeRatingsRepository = mockk(relaxed = true), trailerService = mockk(relaxed = true),
            watchedSeriesStateHolder = watchedSeries,
            cwEnrichmentCache = mockk(relaxed = true) { every { cacheCleared } returns MutableStateFlow(0) },
            profileManager = manager, tvRecommendationManager = mockk(relaxed = true),
            ageGate = disabledAgeGate(), tmdbAvailability = mockk(relaxed = true),
            hiddenTitlesRepository = hiddenTitlesRepository,
            serverCatalog = mockk(relaxed = true)
        )
    }
}
