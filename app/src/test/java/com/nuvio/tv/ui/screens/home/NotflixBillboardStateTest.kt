package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NotflixBillboardStateTest {
    private fun item(id: String, type: ContentType = ContentType.MOVIE) = MetaPreview(id, type, name = id,
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = emptyList())
    @Test fun projectionKeepsOfficialOrderDistinctIdentitiesAndNeverFillsSix() {
        val official = listOf(item("a"), item("a").copy(name = "enriched"), item("b", ContentType.SERIES)) + (2..8).map { item("$it") }
        assertEquals(listOf("movie:a", "series:b", "movie:2", "movie:3", "movie:4", "movie:5"), notflixBillboardItems(official).map(::notflixMetaKey))
        assertTrue(notflixBillboardItems(emptyList()).isEmpty())
        assertEquals(1, notflixBillboardItems(listOf(item("one"))).size)
    }
    @Test fun enrichmentAndReorderKeepCanonicalSelectionThenRemovalUsesValidNeighbour() {
        val old = listOf(item("a"), item("b"), item("c"))
        val fresh = listOf(old[2], old[1].copy(name = "new metadata"), old[0])
        assertEquals(1, notflixBillboardIndex(fresh, "movie:b", 2))
        assertEquals(0, notflixBillboardIndex(listOf(old[0]), "movie:b", 2))
        assertEquals(0, notflixBillboardIndex(emptyList(), "movie:b", 2))
    }
    @Test fun periodUsesTenVisibleSecondsAndEmitsOnlyOnceEvenAfterLargeGap() {
        val clock = NotflixBillboardClock()
        assertEquals(0f, clock.frame(700L).fraction, 0f)
        assertFalse(clock.frame(700L + 9_999_999_999L).advance)
        assertTrue(clock.frame(700L + 10_000_000_000L).advance)
        assertFalse(clock.frame(700L + 100_000_000_000L).advance)
    }
    @Test fun pauseResumesRemainingVisibleFractionAndFreshVisitDoesNotCatchUp() {
        val before = NotflixBillboardClock()
        before.frame(0);val fraction = before.frame(4_000_000_000L).fraction
        val resumed = NotflixBillboardClock(fraction)
        assertFalse(resumed.frame(800_000_000_000L).advance)
        assertFalse(resumed.frame(805_000_000_000L).advance)
        assertTrue(resumed.frame(806_000_000_000L).advance)
        assertEquals(0f, NotflixBillboardClock().frame(1_000_000_000_000L).fraction, 0f)
    }
    @Test fun previewPreferenceDoesNotControlBillboardButEveryHostGateStillBlocksIt() {
        val ready = PreviewEnvironment(PreviewBinding(AppearanceContext(2, 1), 3), enabled = false, valid = true,
            reduced = false, resumed = true, windowFocused = true, introActive = false)
        assertTrue(ready.billboardReady(false))
        for (blocked in listOf(ready.copy(binding = null), ready.copy(valid = false), ready.copy(resumed = false),
            ready.copy(windowFocused = false), ready.copy(introActive = true), ready.copy(blocked = true), ready.copy(otherVideo = true))) {
            assertFalse(blocked.billboardReady(false))
        }
        assertFalse(ready.billboardReady(true))
        assertTrue(ready.copy(enabled = true).billboardReady(false))
    }
    @Test fun playRouteUsesOfficialPlayOnLoadAndManualPolicyWithoutInventingAnEpisode() {
        val route = notflixBillboardPlayRoute("fixture:series/one", "series", "https://example.invalid/addon", true)
        assertTrue(route.startsWith("detail/fixture%3Aseries%2Fone/series?"))
        assertTrue(route.contains("playOnLoad=true") && route.contains("manualSelection=true"))
        assertTrue(route.contains("returnFocusSeason=&returnFocusEpisode="))
        assertTrue(notflixBillboardPlayRoute("fixture:movie", "movie", "", false).contains("manualSelection=false"))
    }
}