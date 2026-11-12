package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** M61: the billboard is enriched in a batch, and it localizes like the cards do. */
class MetaPreviewEnricherLocalizationTest {

    @Test
    fun `the banner takes the TMDB title and synopsis of the app language`() = runTest {
        val item = enricher(tmdbTitle = "La noche de Halloween", synopsis = "Quince años después...")
            .enrich(listOf(banner()), TmdbSettings(enabled = true, language = "es"))
            .single()

        assertEquals("La noche de Halloween", item.name)
        assertEquals("Quince años después...", item.description)
    }

    @Test
    fun `without a translation the banner keeps the addon text`() = runTest {
        val item = enricher(tmdbTitle = null, synopsis = null)
            .enrich(listOf(banner()), TmdbSettings(enabled = true, language = "es"))
            .single()

        assertEquals("Halloween", item.name)
        assertEquals("Fifteen years after murdering his sister...", item.description)
    }

    /** The enrichment only exists when TMDB already resolved the item as a TMDB entry. */
    private fun enricher(tmdbTitle: String?, synopsis: String?): MetaPreviewEnricher {
        val tmdbService = mockk<TmdbService>()
        coEvery { tmdbService.ensureTmdbId(any(), any(), any()) } returns "948"
        val metadata = mockk<TmdbMetadataService>()
        coEvery { metadata.fetchEnrichment(any(), any(), any()) } returns TmdbEnrichment(
            localizedTitle = tmdbTitle,
            description = synopsis,
            genres = emptyList(),
            backdrop = null,
            logo = null,
            poster = null,
            directorMembers = emptyList(),
            writerMembers = emptyList(),
            castMembers = emptyList(),
            releaseInfo = "1978",
            rating = 7.6,
            runtimeMinutes = 91,
            director = emptyList(),
            writer = emptyList(),
            productionCompanies = emptyList(),
            networks = emptyList(),
            ageRating = null,
            status = null,
            countries = null,
            language = "en",
            collectionId = null,
            collectionName = null
        )
        return MetaPreviewEnricher(tmdbService, metadata, mockk<MDBListRepository>(relaxed = true))
    }

    private fun banner() = MetaPreview(
        id = "tt0077651",
        type = ContentType.MOVIE,
        name = "Halloween",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Fifteen years after murdering his sister...",
        releaseInfo = "1978",
        imdbRating = null,
        genres = emptyList()
    )
}
