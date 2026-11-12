package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.MDBListRatingsResult
import com.nuvio.tv.domain.model.MDBListSettings
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.RatingSource
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** M57: an MDBList IMDb value wins and relabels; without it the addon origin survives. */
class MetaPreviewEnricherRatingSourceTest {
    private val tmdbItem = MetaPreview(
        id = "tmdb:123",
        type = ContentType.MOVIE,
        name = "Title",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = 8.5f,
        genres = emptyList(),
        ratingSource = RatingSource.TMDB
    )
    private val settings = MDBListSettings(enabled = true, showOnHero = true)

    private fun enricher(imdbFromMdbList: Double?): MetaPreviewEnricher {
        val tmdbService = mockk<TmdbService>()
        coEvery { tmdbService.ensureTmdbId(any(), any(), any()) } returns null
        val mdbList = mockk<MDBListRepository>()
        every { mdbList.isAvailable(any()) } returns true
        coEvery { mdbList.getRatingsForMeta(any(), any(), any()) } returns MDBListRatingsResult(
            ratings = MDBListRatings(imdb = imdbFromMdbList, tmdb = 8.5),
            hasImdbRating = imdbFromMdbList != null
        )
        return MetaPreviewEnricher(tmdbService, mockk(relaxed = true), mdbList)
    }

    @Test
    fun `an MDBList IMDb rating replaces the value and marks IMDb`() = runTest {
        val item = enricher(7.7).enrich(listOf(tmdbItem), TmdbSettings(), settings).single()

        assertEquals(7.7f, item.imdbRating!!, 0.001f)
        assertEquals(RatingSource.IMDB, item.ratingSource)
    }

    @Test
    fun `without an MDBList IMDb value the addon source stays TMDB`() = runTest {
        val item = enricher(null).enrich(listOf(tmdbItem), TmdbSettings(), settings).single()

        assertEquals(8.5f, item.imdbRating!!, 0.001f)
        assertEquals(RatingSource.TMDB, item.ratingSource)
    }
}
