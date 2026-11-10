package com.nuvio.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class NotflixCardActionsTest {
    private val id = "rowA|tt1"
    private fun key(cursor: NotflixCardCursor?, k: NotflixCardKey, down: Boolean = true, repeat: Int = 0, count: Int = 5, forward: Boolean = true, identity: String = id) =
        notflixCardKey(cursor, identity, count, k, down, repeat, forward)

    @Test fun `only a fresh down on an expanded card enters the actions`() {
        assertEquals(NotflixCardCursor(id, 0) to NotflixCardKeyResult.Consumed, key(null, NotflixCardKey.DOWN))
        // Held Down keeps travelling rows, a collapsed card has no actions, other keys keep their row meaning.
        assertEquals(null to NotflixCardKeyResult.Pass, key(null, NotflixCardKey.DOWN, repeat = 1))
        assertEquals(null to NotflixCardKeyResult.Pass, key(null, NotflixCardKey.DOWN, count = 0))
        assertEquals(null to NotflixCardKeyResult.Pass, key(null, NotflixCardKey.DOWN, down = false))
        for (k in listOf(NotflixCardKey.UP, NotflixCardKey.LEFT, NotflixCardKey.RIGHT, NotflixCardKey.CENTER))
            assertEquals(null to NotflixCardKeyResult.Pass, key(null, k))
    }

    @Test fun `left and right walk inside bounds and follow layout direction`() {
        var cursor: NotflixCardCursor? = NotflixCardCursor(id, 0)
        repeat(7) { cursor = key(cursor, NotflixCardKey.RIGHT).first }
        assertEquals(4, cursor?.index)
        cursor = key(cursor, NotflixCardKey.RIGHT, down = false).first
        assertEquals(4, cursor?.index)
        repeat(9) { cursor = key(cursor, NotflixCardKey.LEFT).first }
        assertEquals(0, cursor?.index)
        assertEquals(1, key(NotflixCardCursor(id, 0), NotflixCardKey.LEFT, forward = false).first?.index)
    }

    @Test fun `up and down leave the actions without moving the title`() {
        val inside = NotflixCardCursor(id, 2)
        assertEquals(null to NotflixCardKeyResult.Consumed, key(inside, NotflixCardKey.UP))
        assertEquals(null to NotflixCardKeyResult.ExitDown, key(inside, NotflixCardKey.DOWN))
        // The release of the entering Down is not a second exit.
        assertEquals(inside to NotflixCardKeyResult.Consumed, key(inside, NotflixCardKey.DOWN, down = false))
    }

    @Test fun `center activates once on its own release and ignores repeats`() {
        val inside = NotflixCardCursor(id, 1)
        val armed = key(inside, NotflixCardKey.CENTER).first!!
        assertTrue(armed.armed)
        assertEquals(armed to NotflixCardKeyResult.Consumed, key(armed, NotflixCardKey.CENTER, repeat = 1))
        val (released, result) = key(armed, NotflixCardKey.CENTER, down = false)
        assertEquals(NotflixCardKeyResult.Activate(1), result)
        assertFalse(released!!.armed)
        // A release without our own fresh press (held from the card or another title) does nothing.
        assertEquals(inside to NotflixCardKeyResult.Consumed, key(inside, NotflixCardKey.CENTER, down = false))
        val moved = key(armed, NotflixCardKey.RIGHT).first!!
        assertFalse(moved.armed)
        assertEquals(NotflixCardKeyResult.Consumed, key(moved, NotflixCardKey.CENTER, down = false).second)
    }

    @Test fun `cursor of another title or a shrunk list is never inherited blindly`() {
        assertEquals(null to NotflixCardKeyResult.Pass, key(NotflixCardCursor("rowB|tt1", 3), NotflixCardKey.CENTER))
        // Sound disappears while selected: activation clamps to the last real action.
        val armed = key(NotflixCardCursor(id, 4), NotflixCardKey.CENTER, count = 4).first!!
        assertEquals(NotflixCardKeyResult.Activate(3), key(armed, NotflixCardKey.CENTER, down = false, count = 4).second)
    }

    @Test fun `runtime and ends at use official parsing and omit invalid values`() {
        assertEquals(126, notflixRuntimeMinutes("126"))
        assertEquals(126, notflixRuntimeMinutes("2h 6min"))
        assertEquals(45, notflixRuntimeMinutes("45 min"))
        for (bad in listOf(null, "", "  ", "0", "abc", "2000")) assertNull(bad, notflixRuntimeMinutes(bad))
        val now = 1_700_000_000_000L
        assertEquals(now + 126 * 60_000L, notflixEndsAt(now, 126))
        assertNull(notflixEndsAt(now, null))
    }
}
