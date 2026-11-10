package com.nuvio.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class NotflixHomeStateTest {
    private fun row(key: String, vararg items: String, phase: NotflixRowPhase = NotflixRowPhase.READY) =
        NotflixFocusRow(key, phase, items.map { NotflixFocusItem(it, it) })
    private val saved = NotflixFocusRecord("a", "two", "two", 1, 0, expanded = true)

    @Test fun identityFollowsReorderWithoutPickingAnotherTitle() {
        assertEquals(NotflixFocusResolution.Item(0, 2), resolveNotflixFocus(saved, listOf(row("a", "three", "one", "two")), true))
    }
    @Test fun removedItemUsesNeighbourAtPreviousValidIndex() {
        assertEquals(NotflixFocusResolution.Item(0, 1), resolveNotflixFocus(saved, listOf(row("a", "one", "three")), true))
    }
    @Test fun removedLastItemClampsToLastNeighbour() {
        assertEquals(NotflixFocusResolution.Item(0, 0), resolveNotflixFocus(saved, listOf(row("a", "one")), true))
    }
    @Test fun loadingAndErrorDoNotProveRemoval() {
        for (phase in listOf(NotflixRowPhase.PENDING, NotflixRowPhase.ERROR)) {
            assertEquals(NotflixFocusResolution.Wait, resolveNotflixFocus(saved, listOf(row("a", phase = phase)), true))
        }
        assertEquals(NotflixFocusResolution.Wait, resolveNotflixFocus(saved, emptyList(), false))
    }
    @Test fun removedRowChoosesNextThenPreviousNonEmptyRow() {
        assertEquals(NotflixFocusResolution.Item(0, 1), resolveNotflixFocus(saved, listOf(row("b", "x", "y")), true))
        assertEquals(NotflixFocusResolution.Item(0, 0), resolveNotflixFocus(saved.copy(rowIndex = 1), listOf(row("previous", "x")), true))
    }
    @Test fun pendingNeighbourWaitsInsteadOfJumpingPastIt() {
        assertEquals(NotflixFocusResolution.Wait, resolveNotflixFocus(saved,
            listOf(row("pending", phase = NotflixRowPhase.PENDING), row("later", "x")), true))
    }
    @Test fun allDefinitivelyEmptyReturnsToTop() {
        assertEquals(NotflixFocusResolution.Top, resolveNotflixFocus(saved, listOf(row("a")), true))
        assertEquals(NotflixFocusResolution.Top, resolveNotflixFocus(saved, emptyList(), true))
    }
    @Test fun cwEpisodeTransitionMatchesOnlyUniqueCanonicalContent() {
        val cw = saved.copy(rowKey = "cw", itemKey = "inprogress:show:1:1", canonicalKey = "series:show")
        val unique = NotflixFocusRow("cw", NotflixRowPhase.READY, listOf(NotflixFocusItem("nextup:show:1:2", "series:show")))
        assertEquals(NotflixFocusResolution.Item(0, 0), resolveNotflixFocus(cw, listOf(unique), true))
        val ambiguous = unique.copy(items = listOf(NotflixFocusItem("other", "series:other")) + unique.items +
            NotflixFocusItem("another-state", "series:show"))
        // Fallback is index, not an arbitrary same-series match.
        assertEquals(NotflixFocusResolution.Item(0, 0), resolveNotflixFocus(cw.copy(index = 0), listOf(ambiguous), true))
    }
    @Test fun profileMemoryClearDoesNotCarryIdentityOrScroll() {
        val memory = NotflixHomeMemory().apply { focus = saved; columnIndex = 3; editorialKey = "movie:x"; rowScroll["a"] = 2 to 5 }
        memory.clear()
        assertNull(memory.focus); assertNull(memory.editorialKey); assertEquals(0, memory.columnIndex); assertTrue(memory.rowScroll.isEmpty())
    }
    @Test fun disappearingStatusNodeNeedsRepairWhenItsContentArrives() {
        val status = saved.copy(itemKey = "status", canonicalKey = "", index = 0)
        assertFalse(notflixFocusNeedsRepair(status, listOf(row("a", phase = NotflixRowPhase.PENDING)), true))
        assertTrue(notflixFocusNeedsRepair(status, listOf(row("a", "one", "two")), true))
    }
    @Test fun automaticTopCannotCancelPendingNeighbourButDefinitiveEmptyAllowsIt() {
        assertTrue(notflixFocusNeedsRepair(saved, listOf(row("a", phase = NotflixRowPhase.PENDING)), true))
        assertTrue(notflixFocusNeedsRepair(saved, listOf(row("b", "x")), true))
        assertFalse(notflixFocusNeedsRepair(saved, emptyList(), true))
    }
}
