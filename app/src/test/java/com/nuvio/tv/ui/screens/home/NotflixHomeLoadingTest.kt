package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotflixHomeLoadingTest {
    private val keys = (0..10).map { "row:$it" }

    @Test fun editorialPrefetchesThreeRowsBeforeAnyRowEntersTheViewport() {
        assertEquals(keys.take(3), notflixRowLoadWindow(keys, setOf("editorial")))
    }

    @Test fun visibleRowsThenNextThreeAndNoRowsBeyondTheEnd() {
        assertEquals(keys.subList(3, 8), notflixRowLoadWindow(keys, setOf(keys[3], keys[4])))
        assertEquals(keys.takeLast(2), notflixRowLoadWindow(keys, setOf(keys[9], keys[10])))
        assertTrue(notflixRowLoadWindow(keys, emptySet()).isEmpty())
        assertTrue(notflixRowLoadWindow(emptyList(), setOf("editorial")).isEmpty())
    }

    @Test fun firstPublicationHasNoDebounce() = runTest {
        val times = mutableListOf<Long>()
        val updates = NotflixCatalogUpdates(this) { times += currentTime }
        updates.request()
        runCurrent()
        assertEquals(listOf(0L), times)
    }

    @Test fun arrivalsDuringSuspendedPublicationDoNotCancelItAndPublishTheLatestBatch() = runTest {
        var version = 1
        val gate = CompletableDeferred<Unit>()
        val versions = mutableListOf<Int>()
        val updates = NotflixCatalogUpdates(this) {
            val captured = version
            if (captured == 1) gate.await()
            versions += captured
        }
        updates.request(); runCurrent()
        repeat(3) { version++; updates.request() }
        runCurrent()
        assertTrue(versions.isEmpty())
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(1, 4), versions)
        version = 5; updates.request(); runCurrent()
        assertEquals(listOf(1, 4, 5), versions)
    }

    @Test fun retirementCancelsPendingWorkAndANewVisitCanPublish() = runTest {
        val gate = CompletableDeferred<Unit>()
        var oldVisit = true
        val results = mutableListOf<String>()
        val updates = NotflixCatalogUpdates(this) {
            if (oldVisit) { gate.await(); results += "old" } else results += "new"
        }
        updates.request(); runCurrent(); updates.cancel()
        oldVisit = false; gate.complete(Unit); runCurrent()
        updates.request(); advanceUntilIdle()
        assertEquals(listOf("new"), results)
    }

    @Test fun retiredNonCooperativePassCannotConsumeTheNewVisitsPendingUpdate() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        var calls = 0
        val updates = NotflixCatalogUpdates(this) {
            when (++calls) {
                1 -> withContext(NonCancellable) { oldGate.await() }
                2 -> newGate.await()
            }
        }
        updates.request(); runCurrent(); updates.cancel()
        updates.request(); runCurrent()
        updates.request()
        oldGate.complete(Unit); runCurrent()
        assertEquals("A cancelled pass must not steal the dirty flag", 2, calls)
        newGate.complete(Unit); advanceUntilIdle()
        assertEquals(3, calls)
    }

    private fun meta(id: String = "one") = MetaPreview(id = id, type = ContentType.MOVIE, name = "Addon title",
        poster = null, posterShape = PosterShape.POSTER, background = "background:$id", logo = null,
        description = "Addon overview", releaseInfo = "2026", imdbRating = 7f, genres = listOf("Drama"), runtime = "100")

    @Test fun partialMetadataKeepsAddonTextAndAcceptsLaterEnrichmentOnlyForTheSameTitle() {
        val base = meta()
        val partial = base.copy(name = "", releaseInfo = null, genres = emptyList(), description = "", runtime = null, imdbRating = null)
        val merged = notflixCardMetadata(base, partial)
        assertEquals(base, merged)
        assertSame(base, notflixCardMetadata(base, meta("other")))
        assertEquals("Localized", notflixCardMetadata(base, partial.copy(name = "Localized")).name)
        assertEquals("2026", merged.releaseInfo)
    }

    @Test fun adjacentBackdropPrefetchIsBoundedDeduplicatedAndWrapsOnlyForTheBanner() {
        val items = (0..3).map { meta("$it") }
        assertEquals(listOf("background:0", "background:2"), notflixNeighbourBackdrops(items, 1))
        assertEquals(listOf("background:1"), notflixNeighbourBackdrops(items, 0))
        assertEquals(listOf("background:3", "background:1"), notflixNeighbourBackdrops(items, 0, true))
        assertTrue(notflixNeighbourBackdrops(items, -1).isEmpty())
        assertTrue(notflixNeighbourBackdrops(items.take(1), 0, true).isEmpty())
        assertEquals(1, notflixNeighbourBackdrops(items.map { it.copy(background = "same") }, 1).size)
    }

    @Test fun aLateImageSuccessCannotCommitForAnotherRequestedUrl() {
        assertTrue(notflixBackdropMatches("new", "new"))
        assertFalse(notflixBackdropMatches("new", "old"))
        assertFalse(notflixBackdropMatches(null, "old"))
        assertFalse(notflixBackdropMatches("", ""))
    }
}
