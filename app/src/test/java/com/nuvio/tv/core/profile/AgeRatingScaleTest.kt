package com.nuvio.tv.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeRatingScaleTest {
    private fun level(raw: String?) = ageLevelOf(raw)

    @Test
    fun `spanish scale`() {
        assertEquals(AgeLevel.TP, level("TP"))
        assertEquals(AgeLevel.TP, level("APTA"))
        assertEquals(AgeLevel.TP, level("A"))
        assertEquals(AgeLevel.TP, level("ALL"))
        assertEquals(AgeLevel.A7, level("7"))
        assertEquals(AgeLevel.A12, level("12"))
        assertEquals(AgeLevel.A16, level("16"))
        assertEquals(AgeLevel.A18, level("18"))
        assertEquals(AgeLevel.X, level("X"))
    }

    @Test
    fun `united states movie ratings`() {
        assertEquals(AgeLevel.TP, level("G"))
        assertEquals(AgeLevel.A7, level("PG"))
        assertEquals(AgeLevel.A12, level("PG-13"))
        assertEquals(AgeLevel.A16, level("R"))
        assertEquals(AgeLevel.A18, level("NC-17"))
    }

    @Test
    fun `united states television ratings`() {
        assertEquals(AgeLevel.TP, level("TV-Y"))
        assertEquals(AgeLevel.TP, level("TV-G"))
        assertEquals(AgeLevel.A7, level("TV-Y7"))
        assertEquals(AgeLevel.A7, level("TV-Y7-FV"))
        assertEquals(AgeLevel.A7, level("TV-PG"))
        assertEquals(AgeLevel.A12, level("TV-14"))
        assertEquals(AgeLevel.A16, level("TV-MA"))
    }

    @Test
    fun `united kingdom ratings`() {
        assertEquals(AgeLevel.TP, level("U"))
        assertEquals(AgeLevel.A12, level("12A"))
        assertEquals(AgeLevel.A16, level("15"))
        assertEquals(AgeLevel.A18, level("R18"))
    }

    @Test
    fun `numeric fallback`() {
        assertEquals(AgeLevel.TP, level("0"))
        assertEquals(AgeLevel.A7, level("3"))
        assertEquals(AgeLevel.A7, level("7"))
        assertEquals(AgeLevel.A12, level("8"))
        assertEquals(AgeLevel.A12, level("12"))
        assertEquals(AgeLevel.A16, level("13"))
        assertEquals(AgeLevel.A16, level("16"))
        assertEquals(AgeLevel.A18, level("17"))
        assertEquals(AgeLevel.A18, level("18"))
        assertEquals(AgeLevel.X, level("19"))
        assertEquals(AgeLevel.X, level("25"))
    }

    @Test
    fun `normalizes spaces case prefixes and plus`() {
        assertEquals(AgeLevel.A12, level(" pg-13 "))
        assertEquals(AgeLevel.A16, level("es-16"))
        assertEquals(AgeLevel.A12, level("+12"))
        assertEquals(AgeLevel.A16, level("tv-ma"))
        assertEquals(AgeLevel.A7, level(" Es-7 "))
    }

    @Test
    fun `unknown and empty ratings have no level`() {
        assertNull(level(null))
        assertNull(level(""))
        assertNull(level("   "))
        assertNull(level("PEGI 3"))
        assertNull(level("No recomendada para menores de 7 años"))
    }

    @Test
    fun `display label round trips through the map`() {
        AgeLevel.entries.forEach { level ->
            assertEquals(level, ageLevelOf(level.displayRating()))
        }
    }
}
