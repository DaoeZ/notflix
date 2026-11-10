package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M32: a card subscribes to the virtual cursor only when it can actually show actions. */
class NotflixHomeContentIsolationTest {
    private val identity = "rowA|tt1"

    @Test fun `collapsed card never reads the cursor`() {
        var reads = 0
        val cursor: () -> NotflixCardCursor? = { reads++; NotflixCardCursor(identity, 2) }
        assertNull(notflixCardCursorIndex(withActions = false, cursor = cursor, identity = identity, actionCount = 4))
        assertEquals(0, reads)
    }

    @Test fun `card without actions never reads the cursor`() {
        var reads = 0
        val cursor: () -> NotflixCardCursor? = { reads++; NotflixCardCursor(identity, 0) }
        assertNull(notflixCardCursorIndex(withActions = true, cursor = cursor, identity = identity, actionCount = 0))
        assertEquals(0, reads)
    }

    @Test fun `expanded cursor matches its own card and clamps to the action range`() {
        assertEquals(3, notflixCardCursorIndex(true, { NotflixCardCursor(identity, 9) }, identity, 4))
        assertEquals(0, notflixCardCursorIndex(true, { NotflixCardCursor(identity, -1) }, identity, 4))
        assertEquals(1, notflixCardCursorIndex(true, { NotflixCardCursor(identity, 1) }, identity, 4))
    }

    @Test fun `another card cursor is ignored`() {
        assertNull(notflixCardCursorIndex(true, { NotflixCardCursor("rowA|tt2", 1) }, identity, 4))
    }
}
