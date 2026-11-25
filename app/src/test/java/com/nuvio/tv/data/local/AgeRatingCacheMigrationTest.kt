package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeRatingCacheMigrationTest {
    @Test
    fun legacyNegativesAreDroppedAndRatingsKept() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("r:movie:tt1") to "NONE",
            longPreferencesKey("t:movie:tt1") to 1L,
            stringPreferencesKey("r:movie:tt2") to "TP",
            longPreferencesKey("t:movie:tt2") to 2L
        )

        prefs.dropLegacyNegatives()

        assertNull(prefs[stringPreferencesKey("r:movie:tt1")])
        assertNull(prefs[longPreferencesKey("t:movie:tt1")])
        assertEquals("TP", prefs[stringPreferencesKey("r:movie:tt2")])
        assertEquals(2L, prefs[longPreferencesKey("t:movie:tt2")])
        assertEquals(AGE_CACHE_SCHEMA_VERSION, prefs[AGE_CACHE_SCHEMA])
    }
}
