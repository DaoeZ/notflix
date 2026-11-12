package com.nuvio.tv.ui.appearance

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.util.withAppLocale
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Test

class NotflixKidsHomeResourcesTest {
    @Test fun injectedContextUsesTheAppLocaleForAllFourNames() {
        val system = mockk<Context>()
        val app = mockk<Context>()
        mockkStatic("com.nuvio.tv.core.util.AppLocaleContextKt")
        try {
            every { system.withAppLocale() } returns app
            every { app.getString(any()) } answers { "localized:${firstArg<Int>()}" }
            val texts = notflixKidsHomeTexts(system)
            assertEquals("localized:${R.string.notflix_home_folder_animated_movies}", texts.animatedMovies)
            assertEquals("localized:${R.string.notflix_home_folder_family_movies}", texts.familyMovies)
            assertEquals("localized:${R.string.notflix_kids_folder_animated_series}", texts.animatedSeries)
            assertEquals("localized:${R.string.notflix_kids_folder_family_series}", texts.familySeries)
            verify(exactly = 0) { system.getString(any()) }
        } finally {
            unmockkStatic("com.nuvio.tv.core.util.AppLocaleContextKt")
        }
    }
}
