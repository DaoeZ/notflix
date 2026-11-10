package com.nuvio.tv.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** M46: the «Filtros (n)» counter is a pure rule, so it can be proved without Compose. */
class NotflixLibraryFiltersTest {
    @Test
    fun noActiveFilterLeavesTheLabelBare() {
        assertEquals(0, notflixActiveFilterCount(null, null, LibraryWatchedFilter.ALL))
        assertEquals("Filters", notflixFiltersLabel("Filters", 0))
    }

    @Test
    fun everyActiveFilterAddsToTheCount() {
        assertEquals(1, notflixActiveFilterCount("action", null, LibraryWatchedFilter.ALL))
        assertEquals(2, notflixActiveFilterCount("action", "2019", LibraryWatchedFilter.ALL))
        assertEquals(3, notflixActiveFilterCount("action", "2019", LibraryWatchedFilter.WATCHED))
        assertEquals("Filters (2)", notflixFiltersLabel("Filters", 2))
    }

    @Test
    fun blankValuesDoNotCount() {
        assertEquals(0, notflixActiveFilterCount("", "  ", LibraryWatchedFilter.ALL))
        assertEquals(1, notflixActiveFilterCount("  ", null, LibraryWatchedFilter.UNWATCHED))
    }
}
