package com.nuvio.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M35: optional provider keys are off when empty and read on every call when present. */
class NotflixIntegrationConfigTest {
    private fun config(tmdb: String = "", introDb: String = "") =
        NotflixIntegrationConfig { NotflixIntegrationCredentials(tmdb, introDb) }

    @Test
    fun emptyTmdbKeyDisablesTheProvider() {
        val config = config(tmdb = "   ")

        assertFalse(config.isTmdbEnabled())
        assertEquals("", config.tmdbApiKey())
    }

    @Test
    fun presentTmdbKeyIsTrimmedAndRead() {
        val config = config(tmdb = "  key-from-settings  ")

        assertTrue(config.isTmdbEnabled())
        assertEquals("key-from-settings", config.tmdbApiKey())
    }

    @Test
    fun emptyOrInvalidIntroDbUrlDisablesTheProvider() {
        assertEquals(DEFAULT_INTRODB_BASE_URL, config().introDbBaseUrl())
        assertFalse(config(introDb = "ftp://example.invalid").isIntroDbEnabled())
        assertTrue(config(introDb = "   ").isIntroDbEnabled())
    }

    // M68: the build can carry Notflix's own TMDB key; the user's key always wins and they never mix.
    private fun buildConfig(tmdb: String = "", builtIn: String = "") = NotflixIntegrationConfig(
        builtInTraktClientId = "",
        builtInTraktClientSecret = "",
        builtInTmdbApiKey = builtIn,
        source = NotflixIntegrationValuesSource { NotflixIntegrationCredentials(tmdbApiKey = tmdb) }
    )

    @Test
    fun theBuiltInTmdbKeyIsUsedWhenTheUserHasNotTypedOne() {
        val config = buildConfig(tmdb = "  ", builtIn = "  build-key  ")

        assertEquals("build-key", config.tmdbApiKey())
        assertTrue(config.isTmdbEnabled())
        assertTrue(config.isTmdbBuiltInKeyInUse())
        assertTrue(config.hasBuiltInTmdbKey())
    }

    @Test
    fun theUserTmdbKeyAlwaysWinsOverTheBuiltInOne() {
        val config = buildConfig(tmdb = " user-key ", builtIn = "build-key")

        assertEquals("user-key", config.tmdbApiKey())
        assertFalse(config.isTmdbBuiltInKeyInUse())
        assertTrue(config.hasBuiltInTmdbKey())
    }

    @Test
    fun withoutAnyTmdbKeyTheProviderStaysOff() {
        val config = buildConfig()

        assertEquals("", config.tmdbApiKey())
        assertFalse(config.isTmdbEnabled())
        assertFalse(config.isTmdbBuiltInKeyInUse())
        assertFalse(config.hasBuiltInTmdbKey())
    }

    @Test
    fun introDbUrlIsNormalizedWithoutTrailingSlash() {
        assertEquals("https://example.invalid/api", config(introDb = " example.invalid/api/ ").introDbBaseUrl())
        assertEquals("http://192.0.2.1:8080", config(introDb = "http://192.0.2.1:8080/").introDbBaseUrl())
        assertTrue(config(introDb = "example.invalid").isIntroDbEnabled())
    }
}
