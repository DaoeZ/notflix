package com.nuvio.tv.ui.appearance

import androidx.compose.ui.unit.dp
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.ui.appearance.notflix.NotflixPosterMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M64: the three approved card sizes and the expanded panel minimums. */
class NotflixCardSizeTest {
    @Test fun `large keeps the approved current measurements`() {
        assertEquals(210, NotflixVisualSpec.posterWidth(NotflixCardSize.LARGE))
        assertEquals(311, NotflixVisualSpec.posterHeight(NotflixCardSize.LARGE))
        assertEquals(378, NotflixVisualSpec.expandedWidth(NotflixCardSize.LARGE))
        assertEquals(311, NotflixVisualSpec.expandedHeight(NotflixCardSize.LARGE))
        assertEquals(248, NotflixVisualSpec.wideWidth(NotflixCardSize.LARGE))
        assertEquals(140, NotflixVisualSpec.wideHeight(NotflixCardSize.LARGE))
    }

    @Test fun `medium is 87 percent of the poster and continue watching`() {
        assertEquals(183, NotflixVisualSpec.posterWidth(NotflixCardSize.MEDIUM))
        assertEquals(271, NotflixVisualSpec.posterHeight(NotflixCardSize.MEDIUM))
        assertEquals(216, NotflixVisualSpec.wideWidth(NotflixCardSize.MEDIUM))
        assertEquals(122, NotflixVisualSpec.wideHeight(NotflixCardSize.MEDIUM))
    }

    @Test fun `small is 75 percent of the poster and continue watching`() {
        assertEquals(158, NotflixVisualSpec.posterWidth(NotflixCardSize.SMALL))
        assertEquals(233, NotflixVisualSpec.posterHeight(NotflixCardSize.SMALL))
        assertEquals(186, NotflixVisualSpec.wideWidth(NotflixCardSize.SMALL))
        assertEquals(105, NotflixVisualSpec.wideHeight(NotflixCardSize.SMALL))
    }

    @Test fun `expanded card never drops below the panel minimum`() {
        assertEquals(330, NotflixVisualSpec.expandedWidth(NotflixCardSize.SMALL))
        assertEquals(330, NotflixVisualSpec.expandedWidth(NotflixCardSize.MEDIUM))
        assertEquals(378, NotflixVisualSpec.expandedWidth(NotflixCardSize.LARGE))
        assertEquals(300, NotflixVisualSpec.expandedHeight(NotflixCardSize.SMALL))
        assertEquals(300, NotflixVisualSpec.expandedHeight(NotflixCardSize.MEDIUM))
        assertEquals(311, NotflixVisualSpec.expandedHeight(NotflixCardSize.LARGE))
    }

    @Test fun `expanded panel keeps room for every row at the current text sizes`() {
        NotflixCardSize.entries.forEach { size ->
            assertTrue("$size clips the panel", NotflixVisualSpec.expandedPanelHeight(size) >=
                NotflixVisualSpec.PANEL_MIN_CONTENT_HEIGHT)
        }
    }

    @Test fun `category grid uses seven columns only when the scaled posters fit`() {
        assertEquals(7, NotflixPosterMetrics.columns(NotflixCardSize.SMALL))
        assertEquals(6, NotflixPosterMetrics.columns(NotflixCardSize.MEDIUM))
        assertEquals(6, NotflixPosterMetrics.columns(NotflixCardSize.LARGE))
        val viewport = 960.dp
        val gutters = (2 * NotflixVisualSpec.GUTTER).dp
        NotflixCardSize.entries.forEach { size ->
            val width = NotflixPosterMetrics.width(size)
            val columns = NotflixPosterMetrics.columns(size)
            val total = width * columns.toFloat() +
                NotflixPosterMetrics.Spacing * (columns - 1).toFloat() + gutters
            assertTrue("$size grid does not fit: $total", total <= viewport)
        }
    }

    @Test fun `the size cycles and unknown stored values keep Large`() {
        assertEquals(NotflixCardSize.MEDIUM, NotflixCardSize.SMALL.next)
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.MEDIUM.next)
        assertEquals(NotflixCardSize.SMALL, NotflixCardSize.LARGE.next)
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.DEFAULT)
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.decode(null))
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.decode(0))
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.decode("75"))
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.decode(false))
        assertEquals(NotflixCardSize.SMALL, NotflixCardSize.decode(75))
        assertEquals(NotflixCardSize.MEDIUM, NotflixCardSize.decode(87L))
        assertEquals(NotflixCardSize.LARGE, NotflixCardSize.decode(100))
    }
}
