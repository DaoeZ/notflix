package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixCardKeyTest {
    private val identity = "row|card"

    @Test
    fun `up from actions focuses the artwork only when the trailer is playing`() {
        val cursor = NotflixCardCursor(identity, 1)

        val (withoutTrailer, withoutResult) = notflixCardKey(
            cursor, identity, count = 4, key = NotflixCardKey.UP,
            down = true, repeat = 0, forward = true, trailerPlaying = false
        )
        assertNull(withoutTrailer)
        assertEquals(NotflixCardKeyResult.Consumed, withoutResult)

        val (withTrailer, withResult) = notflixCardKey(
            cursor, identity, count = 4, key = NotflixCardKey.UP,
            down = true, repeat = 0, forward = true, trailerPlaying = true
        )
        assertEquals(1, withTrailer?.index)
        assertTrue(withTrailer?.artwork == true)
        assertEquals(NotflixCardKeyResult.Consumed, withResult)
    }

    @Test
    fun `down from the artwork returns to the action that was focused`() {
        val artwork = NotflixCardCursor(identity, 2, artwork = true)

        val (next, result) = notflixCardKey(
            artwork, identity, count = 4, key = NotflixCardKey.DOWN,
            down = true, repeat = 0, forward = true, trailerPlaying = true
        )

        assertEquals(2, next?.index)
        assertTrue(next?.artwork == false)
        assertEquals(NotflixCardKeyResult.Consumed, result)
    }

    @Test
    fun `center on the artwork opens the fullscreen view once`() {
        val artwork = NotflixCardCursor(identity, 0, artwork = true)
        val (armed, armedResult) = notflixCardKey(
            artwork, identity, count = 4, key = NotflixCardKey.CENTER,
            down = true, repeat = 0, forward = true, trailerPlaying = true
        )
        assertEquals(NotflixCardKeyResult.Consumed, armedResult)

        val (_, result) = notflixCardKey(
            armed, identity, count = 4, key = NotflixCardKey.CENTER,
            down = false, repeat = 0, forward = true, trailerPlaying = true
        )
        assertEquals(NotflixCardKeyResult.ActivateArtwork, result)
    }

    @Test
    fun `left and right on the artwork return to normal row movement`() {
        val artwork = NotflixCardCursor(identity, 2, artwork = true)
        for (key in listOf(NotflixCardKey.LEFT, NotflixCardKey.RIGHT)) {
            val (next, result) = notflixCardKey(
                artwork, identity, count = 4, key = key,
                down = true, repeat = 0, forward = true, trailerPlaying = true
            )
            assertNull(next)
            assertEquals(NotflixCardKeyResult.Pass, result)
        }
        val (still, released) = notflixCardKey(
            artwork, identity, count = 4, key = NotflixCardKey.RIGHT,
            down = false, repeat = 0, forward = true, trailerPlaying = true
        )
        assertEquals(artwork, still)
        assertEquals(NotflixCardKeyResult.Consumed, released)
    }

    @Test
    fun `up from the artwork leaves the row without a second activation`() {
        val artwork = NotflixCardCursor(identity, 2, artwork = true)
        val (next, result) = notflixCardKey(
            artwork, identity, count = 4, key = NotflixCardKey.UP,
            down = true, repeat = 0, forward = true, trailerPlaying = true
        )
        assertNull(next)
        assertEquals(NotflixCardKeyResult.Pass, result)
    }

    @Test
    fun `action cursor ignores artwork focus`() {
        assertNull(
            notflixCardCursorIndex(
                withActions = true,
                cursor = { NotflixCardCursor(identity, 1, artwork = true) },
                identity = identity,
                actionCount = 4
            )
        )
        assertEquals(
            1,
            notflixCardCursorIndex(
                withActions = true,
                cursor = { NotflixCardCursor(identity, 1) },
                identity = identity,
                actionCount = 4
            )
        )
    }
}
