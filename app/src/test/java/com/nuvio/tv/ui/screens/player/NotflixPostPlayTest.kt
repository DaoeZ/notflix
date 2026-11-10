package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NotflixPostPlayTest {
    @Test fun `fill follows the official countdown and is full exactly when it reaches the last second`() {
        assertEquals(0f, notflixCountdownFill(null, 10), 0f)
        assertEquals(0f, notflixCountdownFill(5, 0), 0f)
        assertEquals(.1f, notflixCountdownFill(10, 10), 1e-6f)
        assertEquals(.6f, notflixCountdownFill(5, 10), 1e-6f)
        assertEquals(1f, notflixCountdownFill(1, 10), 0f)
        assertEquals(1f, notflixCountdownFill(0, 10), 0f)
        // A countdown larger than the remembered total (first tick) never overflows below zero.
        assertEquals(0f, notflixCountdownFill(30, 10), 0f)
    }
}
