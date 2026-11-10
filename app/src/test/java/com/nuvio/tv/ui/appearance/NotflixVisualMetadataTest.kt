package com.nuvio.tv.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotflixVisualMetadataTest {
    @Test fun missingMalformedAndOutOfRangeRatingsDoNotBecomeBadges() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 10.1f, 11f).forEach {
            assertNull("Unexpected rating badge for $it", notflixRating(it))
        }
    }
    @Test fun suppliedBoundedRatingsPreserveTheirNumericValue() {
        listOf(0f, 7.1f, 7.5f, 7.123f, 10f).forEach { assertEquals(it.toString(), notflixRating(it)) }
    }
}
