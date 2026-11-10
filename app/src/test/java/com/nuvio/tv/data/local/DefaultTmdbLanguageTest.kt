package com.nuvio.tv.data.local

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTmdbLanguageTest {
    @Test
    fun `a Spanish TV gets Spanish TMDB metadata`() {
        assertEquals("es", defaultTmdbLanguage(Locale("es", "ES")))
    }

    @Test
    fun `an unknown language falls back to English`() {
        assertEquals("en", defaultTmdbLanguage(Locale("xx")))
    }
}
