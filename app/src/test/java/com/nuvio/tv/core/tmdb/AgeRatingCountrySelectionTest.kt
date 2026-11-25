package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbMovieReleaseDateCountry
import com.nuvio.tv.data.remote.api.TmdbMovieReleaseDateItem
import com.nuvio.tv.data.remote.api.TmdbTvContentRatingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M68: after the preferred countries, the strictest certificate of any other country is used. */
class AgeRatingCountrySelectionTest {
    private fun movie(region: String, vararg certifications: String) = TmdbMovieReleaseDateCountry(
        iso31661 = region,
        releaseDates = certifications.map { TmdbMovieReleaseDateItem(it) }
    )

    private fun tv(region: String, rating: String) = TmdbTvContentRatingItem(iso31661 = region, rating = rating)

    @Test
    fun `the preferred country still wins over any other`() {
        val rating = selectMovieAgeRating(
            listOf(movie("ES", "TP"), movie("DE", "FSK 18"), movie("US", "R")),
            "es-ES"
        )

        assertEquals("TP", rating)
    }

    @Test
    fun `the language region wins before the United States and the United Kingdom`() {
        assertEquals("FSK 12", selectMovieAgeRating(
            listOf(movie("US", "PG-13"), movie("GB", "15"), movie("DE", "FSK 12")), "de-DE"
        ))
        assertEquals("PG-13", selectMovieAgeRating(
            listOf(movie("GB", "15"), movie("US", "PG-13"), movie("DE", "FSK 18")), "es-ES"
        ))
    }

    @Test
    fun `without a preferred certificate the strictest other country is used`() {
        val rating = selectMovieAgeRating(
            listOf(movie("BR", "L"), movie("FR", "12"), movie("IT", "VM18"), movie("NL", "AL")),
            "es-ES"
        )

        assertEquals("VM18", rating)
    }

    @Test
    fun `unknown labels never beat a known one`() {
        val rating = selectMovieAgeRating(
            listOf(movie("BR", "PEGI 3"), movie("NL", "9")), "es-ES"
        )

        assertEquals("9", rating)
        assertNull(selectMovieAgeRating(listOf(movie("BR", "PEGI 3")), "es-ES"))
        assertNull(selectMovieAgeRating(emptyList(), "es-ES"))
    }

    @Test
    fun `series follow the same strictest rule across countries`() {
        // The preferred countries are still checked first: the United States comes before the rest.
        assertEquals("TV-MA", selectTvAgeRating(
            listOf(tv("BR", "12"), tv("US", "TV-MA"), tv("DE", "FSK 16")), "es-ES"
        ))
        assertEquals("16", selectTvAgeRating(
            listOf(tv("BR", "12"), tv("GB", "16"), tv("FR", "18")), "en-GB"
        ))
        assertEquals("18", selectTvAgeRating(
            listOf(tv("BR", "12"), tv("FR", "18")), "es-ES"
        ))
        assertNull(selectTvAgeRating(listOf(tv("BR", "Livre")), "es-ES"))
    }
}
