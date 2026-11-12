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
        coEvery { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any()) } returns
            TrailerPlaybackSource("https://unit.invalid/video", "https://unit.invalid/audio")
        model = FolderDetailViewModel(
            appContext = mockk(), savedStateHandle = SavedStateHandle(),
            collectionsDataStore = mockk { every { collections } returns flowOf(emptyList()) },
            addonRepository = mockk(), catalogRepository = mockk(), layoutPreferenceDataStore = mockk(relaxed = true),
            watchProgressRepository = mockk { every { observeWatchedMovieIds() } returns MutableStateFlow(emptySet()) },
            watchedSeriesStateHolder = mockk { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
            tmdbService = tmdb, tmdbMetadataService = mockk(), tmdbSettingsDataStore = mockk(),
            mdbListRepository = mockk(),
            mdbListSettingsDataStore = mockk { every { settings } returns flowOf(MDBListSettings()) },
            metaRepository = mockk(), trailerService = trailers, tmdbCollectionSourceResolver = mockk(),
            traktPublicListSourceResolver = mockk(), ageGate = mockk(),
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
        coVerify(exactly = 1) { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any()) }
    }

    @Test fun cancelledVisitCannotClearTheLoadingStateOrSourceOfTheNextVisit() = runTest(dispatcher) {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any()) } coAnswers {
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
        coVerify(exactly = 1) { trailers.getTrailerPlaybackSource(any(), any(), any(), any(), any()) }
    }
    @Test fun undoWaitsForTheHideAndUsesTheCapturedProfileEvenAfterSwitching() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        coEvery { hidden.hide(any(), any()) } coAnswers {
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
        coVerify(exactly = 1) { hidden.hide(1, "movie:id") }
        coVerify(exactly = 0) { hidden.show(any(), any()) }
    }

}
