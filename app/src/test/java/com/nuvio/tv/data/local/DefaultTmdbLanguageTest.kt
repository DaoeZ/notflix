package com.nuvio.tv.data.local

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M61: TMDB has to answer in the language the app is read in. */
class DefaultTmdbLanguageTest {
    @Test
    fun `a Spanish TV gets Spanish TMDB metadata`() {
        assertEquals("es", defaultTmdbLanguage(Locale("es", "ES")))
    }

    @Test
    fun `an unknown language falls back to English`() {
        assertEquals("en", defaultTmdbLanguage(Locale("xx")))
    }

    @Test
    fun `the languages the app splits by region stay apart`() {
        assertEquals("pt-br", defaultTmdbLanguage(Locale("pt", "BR")))
        assertEquals("pt", defaultTmdbLanguage(Locale("pt", "PT")))
        assertEquals("zh-CN", defaultTmdbLanguage(Locale("zh", "CN")))
        assertEquals("zh-TW", defaultTmdbLanguage(Locale("zh", "TW")))
        assertEquals("es-419", defaultTmdbLanguage(Locale("es", "419")))
    }

    @Test
    fun `a device language with no region still gets its TMDB code`() {
        assertEquals("pt", defaultTmdbLanguage(Locale("pt")))
        assertEquals("zh-CN", defaultTmdbLanguage(Locale("zh")))
        assertEquals("he", defaultTmdbLanguage(Locale("iw", "IL")))
        assertEquals("id", defaultTmdbLanguage(Locale("in", "ID")))
    }

    @Test
    fun `Chinese script takes precedence over region`() {
        assertEquals("zh-TW", defaultTmdbLanguage(Locale.forLanguageTag("zh-Hant-CN")))
        assertEquals("zh-CN", defaultTmdbLanguage(Locale.forLanguageTag("zh-Hans-TW")))
    }

    /** Every folder the app ships is a language a user can read the app in. */
    @Test
    fun `every app language resolves to a TMDB language`() {
        val res = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("main resources not found from ${File(".").absolutePath}")
        val folders = res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .filter { File(it, "strings.xml").isFile }
            .map { it.name }
        assertTrue("only ${folders.size} values-* folders with strings.xml", folders.size >= 35)

        folders.forEach { folder ->
            val code = defaultTmdbLanguage(localeOf(folder))
            assertTrue("$folder has no TMDB language (got $code)", code != "en")
            assertTrue(
                "$folder maps to $code, which TMDB does not offer",
                AVAILABLE_TMDB_LANGUAGES.any { it.code.equals(code, ignoreCase = true) }
            )
        }
    }

    private fun localeOf(folder: String): Locale {
        val qualifier = folder.removePrefix("values-")
        return when (qualifier) {
            "pt-rBR" -> Locale("pt", "BR")
            "pt-rPT" -> Locale("pt", "PT")
            "zh-rCN" -> Locale("zh", "CN")
            "zh-rTW" -> Locale("zh", "TW")
            "b+es+419" -> Locale("es", "419")
            "b+sr+Latn" -> Locale("sr")
            else -> Locale(qualifier)
        }
    }
}
