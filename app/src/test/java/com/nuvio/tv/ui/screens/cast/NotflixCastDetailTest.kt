package com.nuvio.tv.ui.screens.cast

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** M47: reglas de la página de persona. Son puras para poder probarlas sin Compose ni recursos. */
class NotflixCastDetailTest {

    private val roleLabels: (NotflixPersonRole) -> String = { role ->
        when (role) {
            NotflixPersonRole.ACTING -> "Interpretación"
            NotflixPersonRole.DIRECTING -> "Dirección"
            NotflixPersonRole.WRITING -> "Guion"
            NotflixPersonRole.PRODUCTION -> "Producción"
        }
    }
    private val ageLabel: (Int) -> String = { years -> "$years años" }
    private val lifespanLabel: (String, String, String) -> String = { birth, death, age ->
        "$birth – $death ($age)"
    }

    private fun line(
        knownFor: String? = null,
        birthday: String? = null,
        deathday: String? = null,
        placeOfBirth: String? = null,
        today: LocalDate = LocalDate.of(2026, 11, 8)
    ): String = notflixPersonDataLine(
        notflixPersonFactParts(
            knownFor = knownFor,
            birthday = birthday,
            deathday = deathday,
            placeOfBirth = placeOfBirth,
            roleLabel = roleLabels,
            ageLabel = ageLabel,
            lifespanLabel = lifespanLabel,
            today = today
        )
    )

    // ─── Línea de datos ───

    @Test
    fun withDeathdayTheLineShowsTheLifeYearsInsteadOfTheAge() {
        assertEquals(
            "Dirección · 1940 – 2016 (76 años) · Madrid, España",
            line(
                knownFor = "Directing",
                birthday = "1940-05-06",
                deathday = "2016-05-06",
                placeOfBirth = "Madrid, España"
            )
        )
    }

    @Test
    fun ageAtDeathIsTheLastCompletedYear() {
        assertEquals(
            "1940 – 2016 (75 años)",
            line(birthday = "1940-05-06", deathday = "2016-05-05")
        )
    }

    @Test
    fun withoutDeathdayTheLineShowsTheCurrentAge() {
        assertEquals(
            "Interpretación · 30 años",
            line(knownFor = "Acting", birthday = "1996-11-08")
        )
    }

    @Test
    fun missingFactsAreOmittedWithoutDanglingSeparators() {
        assertEquals("30 años", line(knownFor = "Crew", birthday = "1996-11-08"))
        assertEquals("Madrid, España", line(placeOfBirth = "Madrid, España"))
        assertEquals("Guion · México", line(knownFor = "Writing", placeOfBirth = "México"))
        assertEquals("", line(knownFor = "Camera", placeOfBirth = "   "))
    }

    @Test
    fun unknownDepartmentIsOmitted() {
        assertNull(notflixPersonRole(null))
        assertNull(notflixPersonRole("Editing"))
        assertNull(notflixPersonRole("  "))
        assertEquals(NotflixPersonRole.ACTING, notflixPersonRole("Acting"))
        assertEquals(NotflixPersonRole.DIRECTING, notflixPersonRole("directing"))
        assertEquals(NotflixPersonRole.WRITING, notflixPersonRole("Writing"))
        assertEquals(NotflixPersonRole.PRODUCTION, notflixPersonRole("Production"))
    }

    @Test
    fun dayFirstDatesAreStillAccepted() {
        assertEquals(LocalDate.of(1940, 5, 6), notflixParsePersonDate("06-05-1940"))
        assertEquals(LocalDate.of(1940, 5, 6), notflixParsePersonDate("1940-05-06"))
        assertNull(notflixParsePersonDate(""))
        assertNull(notflixParsePersonDate(null))
    }

    // ─── Edad en los límites ───

    @Test
    fun theAgeTurnsOnTheBirthdayItself() {
        val today = LocalDate.of(2026, 11, 8)
        assertEquals(30, notflixPersonAge("1996-11-08", today))
        assertEquals(29, notflixPersonAge("1996-11-09", today))
        assertEquals(31, notflixPersonAge("1995-11-07", today))
    }

    @Test
    fun theBirthdayAfterTodayDoesNotAddAYearYet() {
        val today = LocalDate.of(2026, 11, 8)
        assertEquals("29 años", line(birthday = "1996-11-09", today = today))
        assertEquals("30 años", line(birthday = "1996-11-08", today = today))
    }

    // ─── Conocida por ───

    @Test
    fun knownForMixesMoviesAndSeriesByRatingAndCapsAtTwelve() {
        val movies = (1..8).map { index ->
            credit(
                id = "movie:$index",
                type = ContentType.MOVIE,
                year = (2000 + index).toString(),
                rating = 10f - index
            )
        }
        val series = (1..8).map { index ->
            credit(
                id = "series:$index",
                type = ContentType.SERIES,
                year = (2010 + index).toString(),
                rating = 9.5f - index
            )
        }

        val knownFor = notflixKnownForCredits(movies, series)

        assertEquals(12, knownFor.size)
        assertEquals(knownFor.sortedByDescending { it.imdbRating }, knownFor)
        assertTrue(knownFor.any { it.type == ContentType.MOVIE })
        assertTrue(knownFor.any { it.type == ContentType.SERIES })
        assertEquals("movie:1", knownFor.first().id)
    }

    @Test
    fun knownForKeepsTheReceivedOrderWhenRatingsTie() {
        val movies = listOf(
            credit(id = "movie:a", type = ContentType.MOVIE, rating = 8f),
            credit(id = "movie:b", type = ContentType.MOVIE, rating = 7f)
        )
        val series = listOf(
            credit(id = "series:c", type = ContentType.SERIES, rating = 8f),
            credit(id = "series:d", type = ContentType.SERIES, rating = null)
        )

        val knownFor = notflixKnownForCredits(movies, series)

        assertEquals(listOf("movie:a", "series:c", "movie:b", "series:d"), knownFor.map { it.id })
    }

    @Test
    fun knownForDoesNotRepeatTheSameCredit() {
        val shared = credit(id = "movie:same", type = ContentType.MOVIE, rating = 8f)
        assertEquals(
            listOf("movie:same"),
            notflixKnownForCredits(listOf(shared), listOf(shared)).map { it.id }
        )
    }

    // ─── Año descendente, sin año al final ───

    @Test
    fun creditsGoByYearDescendingWithMissingYearsAtTheEnd() {
        val credits = listOf(
            credit(id = "a", year = "1999"),
            credit(id = "b", year = null),
            credit(id = "c", year = "2019"),
            credit(id = "d", year = "  "),
            credit(id = "e", year = "1999")
        )

        assertEquals(
            listOf("c", "a", "e", "b", "d"),
            notflixCreditsNewestFirst(credits).map { it.id }
        )
    }

    @Test
    fun releaseInfoIsReadAsAFourDigitYear() {
        assertEquals(2019, notflixReleaseYear("2019"))
        assertEquals(2019, notflixReleaseYear("2019-05-01"))
        assertNull(notflixReleaseYear(null))
        assertNull(notflixReleaseYear("S1"))
    }

    // ─── Iniciales del hueco sin foto ───

    @Test
    fun initialsUseTheFirstLettersOfTwoWords() {
        assertEquals("MS", notflixPersonInitials("Meryl Streep"))
        assertEquals("M", notflixPersonInitials("Meryl"))
        assertEquals("?", notflixPersonInitials("   "))
    }

    private fun credit(
        id: String,
        type: ContentType = ContentType.MOVIE,
        year: String? = null,
        rating: Float? = null
    ) = MetaPreview(
        id = id,
        type = type,
        name = id,
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = year,
        imdbRating = rating,
        genres = emptyList()
    )
}
