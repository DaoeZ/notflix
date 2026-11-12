package com.nuvio.tv.ui.appearance.notflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M67: al ocultar el título enfocado de una rejilla, el foco pasa al vecino que ocupa su sitio y
 * nunca se queda sin destino.
 */
class NotflixPosterFocusTest {

    private val keys = listOf("movie:1", "movie:2", "movie:3", "movie:4")

    @Test
    fun `the poster that takes the place of the hidden one inherits the focus`() {
        assertEquals("movie:3", notflixPosterFocusAfterRemoval(1, keys - "movie:2"))
    }

    @Test
    fun `hiding the last poster keeps the focus on the new last one`() {
        assertEquals("movie:3", notflixPosterFocusAfterRemoval(3, keys - "movie:4"))
        assertEquals("movie:3", notflixPosterFocusAfterRemoval(9, keys - "movie:4"))
    }

    @Test
    fun `an emptied grid has nowhere to put the focus`() {
        assertNull(notflixPosterFocusAfterRemoval(0, emptyList()))
        assertNull(notflixPosterFocusAfterRemoval(-1, emptyList()))
        assertEquals("movie:1", notflixPosterFocusAfterRemoval(-1, listOf("movie:1")))
    }
}
