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
        mapOf(0f to "0.0", 7.1f to "7.1", 7.5f to "7.5", 7.123f to "7.1", 8.332f to "8.3", 7.96f to "8.0", 10f to "10.0")
            .forEach { (value, shown) -> assertEquals(shown, notflixRating(value)) }
    }
}
