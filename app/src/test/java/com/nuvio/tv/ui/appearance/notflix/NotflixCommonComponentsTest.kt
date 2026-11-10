package com.nuvio.tv.ui.appearance.notflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M46: header target, 250 ms dwell and the shared poster key are pure and clock driven. */
class NotflixCommonComponentsTest {
    private val keys = listOf("movie:1", "movie:2", "series:3")

    @Test
    fun headerShowsTheFocusedTitleWhenItBelongsToThePage() {
        assertEquals("movie:2", notflixHeaderKey("movie:2", keys))
    }

    @Test
    fun headerFallsBackToTheFirstTitle() {
        assertEquals("movie:1", notflixHeaderKey(null, keys))
        assertEquals("movie:1", notflixHeaderKey("missing", keys))
    }

    @Test
    fun headerIsEmptyWhenThereIsNoTitle() {
        assertNull(notflixHeaderKey(null, emptyList()))
        assertNull(notflixHeaderKey("movie:1", emptyList()))
    }

    @Test
    fun headerWaitsTheDwellBeforeChangingTitle() {
        assertFalse(
            notflixHeaderCommit(
                focusedKey = "movie:2",
                shownKey = "movie:1",
                elapsedSinceFocusMs = 249L
            )
        )
        assertTrue(
            notflixHeaderCommit(
                focusedKey = "movie:2",
                shownKey = "movie:1",
                elapsedSinceFocusMs = 250L
            )
        )
    }

    @Test
    fun sameTitleNeverReloads() {
        assertFalse(
            notflixHeaderCommit(
                focusedKey = "movie:1",
                shownKey = "movie:1",
                elapsedSinceFocusMs = 10_000L
            )
        )
    }

    @Test
    fun anInjectedClockCanProveTheDelayBoundary() {
        var now = 0L
        val clock = { now }
        val focusedAt = clock()
        now = 100L
        assertFalse(notflixHeaderCommit("movie:2", "movie:1", clock() - focusedAt))
        now = 250L
        assertTrue(notflixHeaderCommit("movie:2", "movie:1", clock() - focusedAt))
    }
}
