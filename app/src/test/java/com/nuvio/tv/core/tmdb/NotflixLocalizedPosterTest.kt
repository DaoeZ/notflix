package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotflixLocalizedPosterTest {
    @Test
    fun `poster in the app language wins over original language and textless artwork`() {
        val images = listOf(TmdbImage("/en.jpg", "en"), TmdbImage("/neutral.jpg"), TmdbImage("/es.jpg", "es"))
        assertEquals(searchImageUrl("/es.jpg"), selectNotflixPoster(images, "es-ES"))
        assertEquals(searchImageUrl("/es.jpg"), selectNotflixPoster(images, "es-MX"))
    }

    @Test
    fun `textless artwork is preferred when the localized poster is missing`() {
        assertEquals(searchImageUrl("/neutral.jpg"),
            selectNotflixPoster(listOf(TmdbImage("/en.jpg", "en"), TmdbImage("/neutral.jpg")), "es"))
        assertNull(selectNotflixPoster(listOf(TmdbImage("/en.jpg", "en")), "es"))
    }
}
