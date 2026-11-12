package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.RatingSource
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** M57: card and banner take the label from the value's real origin. */
class NotflixRatingSourceTest {
    private fun preview(source: RatingSource) = MetaPreview(
        id = "tt0137523",
        type = ContentType.MOVIE,
        name = "Fight Club",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = 8.4f,
        genres = emptyList(),
        ratingSource = source
    )

    @Test
    fun `banner labels the value with its real origin`() {
        assertEquals("TMDB 8,4", notflixBillboardRatingText(RatingSource.TMDB, "8,4"))
        assertEquals("IMDb 7,7", notflixBillboardRatingText(RatingSource.IMDB, "7,7"))
    }

    @Test
    fun `card badge uses the same origin labels`() {
        assertEquals("TMDB", RatingSource.TMDB.label)
        assertEquals("IMDb", RatingSource.IMDB.label)
    }

    @Test
    fun `poster specs keep the source with the value`() {
        assertEquals(RatingSource.TMDB, preview(RatingSource.TMDB).toNotflixPosterSpec().ratingSource)
        assertEquals(RatingSource.IMDB, preview(RatingSource.IMDB).toNotflixPosterSpec().ratingSource)
    }

    @Test
    fun `a new preview without a source still reads IMDb`() {
        assertEquals(RatingSource.IMDB, preview(RatingSource.IMDB).copy(imdbRating = 9.1f).ratingSource)
    }
}
