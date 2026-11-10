package com.nuvio.tv.ui.screens.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * M51: reglas puras del salvapantallas Notflix (hora, fecha, desplazamiento antiquemado y línea
 * de metadatos). Se prueban sin Compose ni recursos.
 */
class NotflixAmbientTest {

    // ─── Hora ───

    @Test
    fun twentyFourHourClockPadsBothParts() {
        assertEquals("21:47", notflixClockLabel(21, 47, is24Hour = true))
        assertEquals("09:05", notflixClockLabel(9, 5, is24Hour = true))
        assertEquals("00:00", notflixClockLabel(0, 0, is24Hour = true))
        assertEquals("23:59", notflixClockLabel(23, 59, is24Hour = true))
    }

    @Test
    fun twelveHourClockUsesTwelveForNoonAndMidnight() {
        assertEquals("9:47", notflixClockLabel(21, 47, is24Hour = false))
        assertEquals("12:00", notflixClockLabel(12, 0, is24Hour = false))
        assertEquals("12:05", notflixClockLabel(0, 5, is24Hour = false))
        assertEquals("1:00", notflixClockLabel(13, 0, is24Hour = false))
    }

    // ─── Fecha ───

    @Test
    fun spanishDateFollowsTheDesignLine() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("domingo, 8 de noviembre", notflixAmbientDateLabel(date, Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun englishDateKeepsTheWeekdayFirstWithoutTheYear() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("Sunday, November 8", notflixAmbientDateLabel(date, Locale.ENGLISH))
    }

    @Test
    fun otherLocalesStillGetDayAndMonth() {
        val pattern = notflixAmbientDatePattern(Locale.FRENCH)
        assertTrue(pattern.contains("d"))
        assertTrue(pattern.contains("MMMM"))
    }

    // ─── Antiquemado ───

    @Test
    fun driftStartsFlushWithTheCorner() {
        assertEquals(NotflixClockDrift.None, notflixClockDrift(seed = 7, tick = 0))
    }

    @Test
    fun driftIsDeterministicForTheSameSeedAndMinute() {
        assertEquals(notflixClockDrift(seed = 1234, tick = 5), notflixClockDrift(seed = 1234, tick = 5))
        assertEquals(notflixClockDrift(seed = 99, tick = 42), notflixClockDrift(seed = 99, tick = 42))
    }

    @Test
    fun driftNeverLeavesTheTwelveDpBudget() {
        for (seed in 0 until 25) {
            for (tick in 0..40) {
                val drift = notflixClockDrift(seed, tick)
                assertTrue("x fuera de rango: $drift", abs(drift.xDp) <= 12f)
                assertTrue("y fuera de rango: $drift", abs(drift.yDp) <= 12f)
            }
        }
    }

    @Test
    fun driftMovesAfterTheFirstMinute() {
        val moves = (1..12).map { notflixClockDrift(seed = 3, tick = it) }
        assertTrue(moves.any { it != NotflixClockDrift.None })
        assertTrue(moves.map { it.xDp to it.yDp }.toSet().size > 1)
    }

    // ─── Línea «Película · 2019 · Drama» ───

    @Test
    fun metaLineJoinsTypeYearAndGenres() {
        assertEquals(
            "Película · 2019 · Drama · Crimen",
            notflixAmbientMeta("Película", "Serie", AmbientKind.MOVIE, 2019, listOf("Drama", "Crimen"))
        )
        assertEquals(
            "Serie · 2021",
            notflixAmbientMeta("Película", "Serie", AmbientKind.SERIES, 2021, emptyList())
        )
    }

    @Test
    fun metaLineNeverLeavesDanglingSeparators() {
        assertEquals("Drama", notflixAmbientMeta("Película", "Serie", null, null, listOf("Drama")))
        assertEquals("", notflixAmbientMeta(null, null, null, null, emptyList()))
        // Sin año ni géneros útiles queda solo la etiqueta de tipo, sin «·» suelto.
        assertEquals("Película", notflixAmbientMeta("Película", "Serie", AmbientKind.MOVIE, null, listOf(" ")))
        assertEquals("Serie · 2020", notflixAmbientMeta("Película", "Serie", AmbientKind.SERIES, 2020, listOf(" ")))
        assertEquals("2019 · Drama", notflixAmbientMeta("Película", "Serie", null, 2019, listOf(" ", "Drama")))
        for (line in listOf(
            notflixAmbientMeta("Película", "Serie", AmbientKind.MOVIE, null, listOf(" ")),
            notflixAmbientMeta("Película", "Serie", null, null, emptyList()),
            notflixAmbientMeta(null, null, AmbientKind.SERIES, null, emptyList())
        )) {
            assertTrue("separador suelto en «$line»", !line.startsWith("·") && !line.endsWith("·"))
            assertTrue("separador doble en «$line»", !line.contains("· ·"))
        }
    }
}
