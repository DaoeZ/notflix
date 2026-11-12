package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.NotflixCardOpening
import org.junit.Assert.assertEquals
import org.junit.Test

/** M56: the chosen opening drives both the Home delay and the earliest trailer start. */
class NotflixCardOpeningTest {
    @Test fun `the three approved choices keep their exact waits`() {
        assertEquals(150L, NotflixVisualSpec.cardOpenDelayMs(NotflixCardOpening.INSTANT))
        assertEquals(1200L, NotflixVisualSpec.cardOpenDelayMs(NotflixCardOpening.NORMAL))
        assertEquals(2000L, NotflixVisualSpec.cardOpenDelayMs(NotflixCardOpening.SLOW))
    }

    @Test fun `the trailer never starts before the card opens plus its beat`() {
        // Normal remains exactly today's 2 s dwell; only a slower opening pushes the clip.
        assertEquals(2000L, NotflixVisualSpec.previewDwellMs(NotflixCardOpening.INSTANT))
        assertEquals(2000L, NotflixVisualSpec.previewDwellMs(NotflixCardOpening.NORMAL))
        assertEquals(2800L, NotflixVisualSpec.previewDwellMs(NotflixCardOpening.SLOW))
    }

    @Test fun `the settings row cycles instant normal slow and back`() {
        assertEquals(NotflixCardOpening.NORMAL, NotflixCardOpening.INSTANT.next)
        assertEquals(NotflixCardOpening.SLOW, NotflixCardOpening.NORMAL.next)
        assertEquals(NotflixCardOpening.INSTANT, NotflixCardOpening.SLOW.next)
    }

    @Test fun `unknown or corrupt stored values keep the normal default`() {
        assertEquals(NotflixCardOpening.NORMAL, NotflixCardOpening.decode(null))
        assertEquals(NotflixCardOpening.NORMAL, NotflixCardOpening.decode(0))
        assertEquals(NotflixCardOpening.NORMAL, NotflixCardOpening.decode("2000"))
        assertEquals(NotflixCardOpening.NORMAL, NotflixCardOpening.decode(false))
        assertEquals(NotflixCardOpening.SLOW, NotflixCardOpening.decode(2000))
        assertEquals(NotflixCardOpening.INSTANT, NotflixCardOpening.decode(150L))
    }
}
