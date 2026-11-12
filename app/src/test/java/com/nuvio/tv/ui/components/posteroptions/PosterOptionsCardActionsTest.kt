package com.nuvio.tv.ui.components.posteroptions

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.tracking.TrackingMembershipApplyResult
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class PosterOptionsCardActionsTest {
    @Before fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
    }
    @After fun cleanup() { unmockkStatic(Log::class) }

    private class Fixture(scope: CoroutineScope, mode: LibrarySourceMode = LibrarySourceMode.LOCAL) {
        val libraryValue = MutableStateFlow(false)
        val watchedValue = MutableStateFlow(false)
        val seriesIds = MutableStateFlow<Set<String>>(emptySet())
        val library = mockk<LibraryRepository>(relaxed = true) {
            every { sourceMode } returns flowOf(mode)
            every { membershipListTabs } returns flowOf(emptyList())
            every { isInLibrary(any(), any()) } returns libraryValue
            coEvery { getMembershipSnapshot(any()) } returns ListMembershipSnapshot()
            coEvery { toggleDefault(any(), any()) } coAnswers {
                libraryValue.value = !libraryValue.value
                TrackingMembershipApplyResult()
            }
            coEvery { applyMembershipChanges(any(), any(), any()) } returns TrackingMembershipApplyResult()
        }
        val progress = mockk<WatchProgressRepository>(relaxed = true) {
            every { isWatched(any(), any(), any(), any()) } returns watchedValue
        }
        val holder = mockk<WatchedSeriesStateHolder> {
            every { fullyWatchedSeriesIds } returns seriesIds
            every { update(any()) } answers { seriesIds.value = firstArg() }
        }
        val metadata = mockk<MetaRepository>(relaxed = true)
        val tmdb = mockk<TmdbService> { coEvery { tmdbToImdb(any(), any()) } returns "tt123" }
        val controller = PosterOptionsController(mockk<Context>(relaxed = true), library, progress,
            metadata, holder, tmdb).also { it.bind(scope) }
    }

    private fun item(id: String = "tt123", type: ContentType = ContentType.MOVIE) = MetaPreview(
        id, type, name = id, poster = null, posterShape = PosterShape.POSTER, background = null,
        logo = null, description = null, releaseInfo = null, imdbRating = null, genres = emptyList())

    @Test fun canonicalIdsAreUsedForLiveCardReadsAndUpdatesFromTheLongMenu() = runTest {
        val f = Fixture(backgroundScope)
        f.libraryValue.value = true
        f.watchedValue.value = true
        val raw = item("tmdb:123")
        f.controller.refreshCardStatus(raw); runCurrent()
        val key = posterCardStatusKey("movie", raw.id)
        assertEquals(PosterCardStatus(inLibrary = true, watched = true), f.controller.cardStatus.value[key])
        verify { f.library.isInLibrary("tt123", "movie") }
        verify(exactly = 0) { f.library.isInLibrary("tmdb:123", any()) }
        f.controller.show(raw, null); runCurrent()
        f.controller.toggleLibrary(); runCurrent()
        f.watchedValue.value = false; runCurrent()
        assertEquals(PosterCardStatus(inLibrary = false, watched = false), f.controller.cardStatus.value[key])
    }

    @Test fun pendingWatchedIgnoresStaleReadsAndRepeatedOkAndWritesAllCanonicalAliases() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.progress.markAsCompleted(any()) } coAnswers { gate.await(); f.watchedValue.value = true }
        val raw = item("tmdb:123")
        f.controller.refreshCardStatus(raw); runCurrent()
        try {
            f.controller.toggleWatchedFor(raw); runCurrent()
            f.watchedValue.value = true; runCurrent()
            f.watchedValue.value = false; runCurrent()
            val key = posterCardStatusKey("movie", raw.id)
            assertEquals(PosterCardStatus(inLibrary = false, watched = true, watchedPending = true), f.controller.cardStatus.value[key])
            f.controller.toggleWatchedFor(raw); runCurrent()
            coVerify(exactly = 1) { f.progress.markAsCompleted(match { it.contentId == "tt123" }) }
            verify { f.progress.applyOptimisticWatchedMovie(setOf("tmdb:123", "tt123"), add = true) }
            gate.complete(Unit); runCurrent()
            assertEquals(true, f.controller.cardStatus.value[key]?.watched)
            assertEquals(false, f.controller.cardStatus.value[key]?.watchedPending)
        } finally { gate.complete(Unit) }
    }

    @Test fun localLibraryUsesTheCanonicalWriterAndRejectsADuplicatePendingPress() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.library.toggleDefault(any(), any()) } coAnswers {
            gate.await(); f.libraryValue.value = true; TrackingMembershipApplyResult()
        }
        val raw = item("tmdb:123")
        f.controller.refreshCardStatus(raw); runCurrent()
        try {
            f.controller.toggleLibraryFor(raw, "https://addon"); runCurrent()
            f.controller.toggleLibraryFor(raw, "https://addon"); runCurrent()
            val key = posterCardStatusKey("movie", raw.id)
            assertTrue(f.controller.cardStatus.value.getValue(key).libraryPending)
            coVerify(exactly = 1) { f.library.toggleDefault(match { it.itemId == "tt123" && it.addonBaseUrl == "https://addon" }, any()) }
            gate.complete(Unit); runCurrent()
            assertEquals(true, f.controller.cardStatus.value[key]?.inLibrary)
            assertFalse(f.controller.cardStatus.value.getValue(key).libraryPending)
        } finally { gate.complete(Unit) }
    }

    @Test fun failedSeriesWriteDoesNotEraseAnotherTitlesOptimisticUpdate() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        every { f.metadata.getMetaFromPrimaryAddon(any(), any()) } returns flow { gate.await() }
        try {
            f.controller.toggleWatchedFor(item("tt-series", ContentType.SERIES)); runCurrent()
            assertTrue("tt-series" in f.seriesIds.value)
            f.seriesIds.value = f.seriesIds.value + "tt-other-series"
            gate.complete(Unit); runCurrent()
            assertEquals(setOf("tt-other-series"), f.seriesIds.value)
            assertFalse(f.controller.cardStatus.value.getValue(posterCardStatusKey("series", "tt-series")).watchedPending)
            coVerify(exactly = 0) { f.progress.markAsCompletedBatch(any()) }
        } finally { gate.complete(Unit) }
    }

    @Test fun aCancelledPickerCannotReplaceTheNextCanonicalInput() = runTest {
        val f = Fixture(backgroundScope, LibrarySourceMode.TRAKT)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.tmdb.tmdbToImdb(123, "movie") } coAnswers {
            withContext(NonCancellable) { gate.await() }; "tt-old"
        }
        runCurrent()
        try {
            f.controller.toggleLibraryFor(item("tmdb:123"), null); runCurrent()
            f.controller.dismissListPicker()
            f.controller.toggleLibraryFor(item("tt-new"), null); runCurrent()
            gate.complete(Unit); runCurrent()
            f.controller.saveListPicker(); runCurrent()
            coVerify(exactly = 1) { f.library.applyMembershipChanges(match { it.itemId == "tt-new" }, any(), any()) }
            coVerify(exactly = 0) { f.library.applyMembershipChanges(match { it.itemId == "tt-old" }, any(), any()) }
        } finally { gate.complete(Unit) }
    }

    @Test fun anOldPickerSaveCannotCloseANewPicker() = runTest {
        val f = Fixture(backgroundScope, LibrarySourceMode.TRAKT)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.library.applyMembershipChanges(any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { gate.await() }; TrackingMembershipApplyResult()
        }
        runCurrent()
        try {
            f.controller.toggleLibraryFor(item("tt-old"), null); runCurrent()
            f.controller.saveListPicker(); runCurrent()
            f.controller.dismissListPicker()
            f.controller.toggleLibraryFor(item("tt-new"), null); runCurrent()
            gate.complete(Unit); runCurrent()
            assertTrue(f.controller.state.value.listPickerActive)
            assertEquals("tt-new", f.controller.state.value.listPickerTitle)
            assertFalse(f.controller.state.value.listPickerPending)
        } finally { gate.complete(Unit) }
    }

    @Test fun cardHistoryIsBoundedAndOnlyTheExpandedTitleIsObserved() = runTest {
        val f = Fixture(backgroundScope)
        repeat(80) { f.controller.refreshCardStatus(item("tt$it")); runCurrent() }
        assertEquals(64, f.controller.cardStatus.value.size)
        assertEquals(1, f.libraryValue.subscriptionCount.value)
        assertEquals(1, f.watchedValue.subscriptionCount.value)
        f.controller.stopCardStatus(); runCurrent()
        assertEquals(0, f.libraryValue.subscriptionCount.value)
        assertEquals(0, f.watchedValue.subscriptionCount.value)
    }
    @Test fun longPressAndCardShareThePendingGuardForTheSameCanonicalTitle() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.library.toggleDefault(any(), any()) } coAnswers {
            gate.await(); f.libraryValue.value = true; TrackingMembershipApplyResult()
        }
        val raw = item("tmdb:123")
        f.controller.refreshCardStatus(raw); runCurrent()
        f.controller.show(raw, null); runCurrent()
        try {
            f.controller.toggleLibrary(); runCurrent()
            f.controller.dismiss()
            val key = posterCardStatusKey("movie", raw.id)
            assertTrue(f.controller.cardStatus.value.getValue(key).libraryPending)
            f.controller.toggleLibraryFor(raw, null); runCurrent()
            coVerify(exactly = 1) { f.library.toggleDefault(any(), any()) }
            gate.complete(Unit); runCurrent()
            assertEquals(PosterCardStatus(inLibrary = true, watched = false), f.controller.cardStatus.value[key])
        } finally { gate.complete(Unit) }
    }

    @Test fun failedLongPressSeriesWriteAlsoPreservesOtherTitles() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        every { f.metadata.getMetaFromPrimaryAddon(any(), any()) } returns flow { gate.await() }
        f.controller.show(item("tt-series", ContentType.SERIES), null); runCurrent()
        try {
            f.controller.toggleSeriesWatched(); runCurrent()
            assertTrue("tt-series" in f.seriesIds.value)
            f.controller.dismiss()
            f.seriesIds.value = f.seriesIds.value + "tt-other-series"
            gate.complete(Unit); runCurrent()
            assertEquals(setOf("tt-other-series"), f.seriesIds.value)
            assertFalse(f.controller.state.value.isWatchedPending)
        } finally { gate.complete(Unit) }
    }

}
