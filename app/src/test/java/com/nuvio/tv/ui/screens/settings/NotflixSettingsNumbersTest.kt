package com.nuvio.tv.ui.screens.settings

import org.junit.Assert.*
import org.junit.Test

class NotflixSettingsNumbersTest {
    @Test fun boundedListsKeepEndpointsStepAndAnExistingValueWithoutAllocatingAHugeRange() {
        assertEquals(listOf(3, 4, 5, 6), notflixNumberOptions(3, 6, 1, 4))
        val huge = notflixNumberOptions(Int.MIN_VALUE, Int.MAX_VALUE, 1, 17)
        assertTrue(huge.size <= 43)
        assertEquals(Int.MIN_VALUE, huge.first()); assertEquals(Int.MAX_VALUE, huge.last())
        assertTrue(17 in huge); assertEquals(huge.distinct().sorted(), huge)
        assertEquals(listOf(-1, 0, 5, 10), notflixNumberOptions(0, 10, 5, -1))
        assertEquals(listOf(7), notflixNumberOptions(10, 0, 0, 7))
    }
    @Test fun unitsAndExplicitHalfStepsDoNotChangeTheStoredMeaning() {
        assertEquals("10s", notflixNumberLabel(10, 3, "3s"))
        assertEquals("128 MB", notflixNumberLabel(128, 256, "256+64 MB"))
        assertEquals("256+64 MB", notflixNumberLabel(256, 256, "256+64 MB"))
        assertEquals("2.5 min", notflixNumberLabel(5, 2, "1 min", .5f))
        assertEquals("2,5 %", notflixNumberLabel(5, 3, "1,5 %", .5f))
        assertEquals("7", notflixNumberLabel(7, 0, "Off"))
    }
}
