package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaPreviewDto
import com.nuvio.tv.data.remote.dto.CatalogResponseDto
import com.nuvio.tv.data.remote.dto.MetaResponseDto
import com.nuvio.tv.domain.model.RatingSource
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

/** M57: old data reads as IMDb and only the verified TMDB addon marks its catalog items. */
class CatalogMapperRatingSourceTest {
    private val dto = MetaPreviewDto(
        id = "tt0137523",
        type = "movie",
        name = "Fight Club",
        imdbRating = "8.4"
    )

    @Test
    fun `old catalog data without an origin reads IMDb`() {
        assertEquals(RatingSource.IMDB, dto.toDomainOrNull("movie")!!.ratingSource)
    }

    @Test
    fun `cached catalog JSON is relabeled from its manifest without changing its value`() {
        val adapter = Moshi.Builder().build().adapter(CatalogResponseDto::class.java)
        val json = """{"metas":[{"id":"tmdb:155","type":"movie","name":"Title","imdbRating":"8.5"}]}"""
        val cached = requireNotNull(adapter.fromJson(json)?.metas?.single())

        val original = requireNotNull(cached.toDomainOrNull("movie"))
        val tmdb = requireNotNull(cached.toDomainOrNull("movie", ratingSource = catalogRatingSource(TMDB_ADDON_MANIFEST_ID)))
        assertEquals(RatingSource.IMDB, original.ratingSource)
        assertEquals(original.imdbRating, tmdb.imdbRating)
        assertEquals(RatingSource.TMDB, tmdb.ratingSource)
    }

    @Test
    fun `old stored meta json reads IMDb`() {
        val adapter = Moshi.Builder().build().adapter(MetaResponseDto::class.java)
        val json = """
            {"meta":{"id":"tt0137523","type":"movie","name":"Fight Club","imdbRating":"8.4"}}
        """.trimIndent()

        val meta = requireNotNull(adapter.fromJson(json)?.meta).toDomain()

        assertEquals(RatingSource.IMDB, meta.ratingSource)
        assertEquals(8.4f, meta.imdbRating!!, 0.001f)
        val tmdb = meta.copy(ratingSource = RatingSource.TMDB)
        assertEquals(RatingSource.TMDB, tmdb.copy(name = "Renamed").ratingSource)
    }

    @Test
    fun `catalog origin is decided by the verified manifest id`() {
        assertEquals("tmdb-addon", TMDB_ADDON_MANIFEST_ID)
        assertEquals(RatingSource.TMDB, catalogRatingSource(TMDB_ADDON_MANIFEST_ID))
        assertEquals(RatingSource.IMDB, catalogRatingSource("com.linvo.cinemeta"))
        assertEquals(RatingSource.IMDB, catalogRatingSource(""))
    }

    @Test
    fun `tmdb mapped items keep their origin and value`() {
        val item = dto.toDomainOrNull("movie", ratingSource = RatingSource.TMDB)!!

        assertEquals(RatingSource.TMDB, item.ratingSource)
        assertEquals(8.4f, item.imdbRating!!, 0.001f)
        assertEquals(RatingSource.TMDB, item.copy(name = "Renamed").ratingSource)
    }
}
