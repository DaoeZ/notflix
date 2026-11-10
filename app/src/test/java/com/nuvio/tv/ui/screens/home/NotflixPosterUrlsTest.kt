package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotflixPosterUrlsTest {
    @Test
    fun `metahub posters are never swapped because large is a different crop`() {
        val small = "https://images.metahub.space/poster/small/tt1/img"
        val medium = "https://images.metahub.space/poster/medium/tt1/img"
        assertEquals(small, sharperPosterUrl(small))
        assertEquals(medium, sharperPosterUrl(medium))
    }

    @Test
    fun `tmdb small sizes become w500`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            sharperPosterUrl("https://image.tmdb.org/t/p/w185/abc.jpg")
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            sharperPosterUrl("https://image.tmdb.org/t/p/w342/abc.jpg")
        )
    }

    @Test
    fun `already sharp or original tmdb urls stay unchanged`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            sharperPosterUrl("https://image.tmdb.org/t/p/w500/abc.jpg")
        )
        assertEquals(
            "https://image.tmdb.org/t/p/original/abc.jpg",
            sharperPosterUrl("https://image.tmdb.org/t/p/original/abc.jpg")
        )
    }

    @Test
    fun `unknown urls and null stay unchanged`() {
        val unknown = "https://example.invalid/poster/small/tt1.jpg"
        assertEquals(unknown, sharperPosterUrl(unknown))
        assertNull(sharperPosterUrl(null))
    }
}
