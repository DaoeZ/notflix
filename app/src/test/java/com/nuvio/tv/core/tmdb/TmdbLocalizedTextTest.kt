package com.nuvio.tv.core.tmdb

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** M61: one rule for the card, the banner and the detail screen. */
class TmdbLocalizedTextTest {

    @Test
    fun `the translated title and synopsis replace the addon text`() {
        val card = card().withLocalizedTmdbText(enrichment("La noche de Halloween", "Quince años después de asesinar a su hermana..."))
        val detail = detail().withLocalizedTmdbText(enrichment("La noche de Halloween", "Quince años después de asesinar a su hermana..."))

        assertEquals("La noche de Halloween", card.name)
        assertEquals("Quince años después de asesinar a su hermana...", card.description)
        assertEquals("La noche de Halloween", detail.name)
        assertEquals("Quince años después de asesinar a su hermana...", detail.description)
    }

    @Test
    fun `a title TMDB has no translation for keeps the addon one and still translates the synopsis`() {
        val card = card().withLocalizedTmdbText(enrichment(localizedTitle = null, description = "Sinopsis en español"))

        assertEquals("Halloween", card.name)
        assertEquals("Sinopsis en español", card.description)
    }

    @Test
    fun `a missing synopsis keeps the addon one`() {
        val card = card().withLocalizedTmdbText(enrichment("La noche de Halloween", description = null))

        assertEquals("La noche de Halloween", card.name)
        assertEquals("Fifteen years after murdering his sister...", card.description)
    }

    @Test
    fun `no enrichment leaves the addon text untouched`() {
        val card = card().withLocalizedTmdbText(null)
        val detail = detail().withLocalizedTmdbText(null)

        assertEquals("Halloween", card.name)
        assertEquals("Fifteen years after murdering his sister...", card.description)
        assertEquals("Halloween", detail.name)
        assertEquals("Fifteen years after murdering his sister...", detail.description)
    }

    @Test
    fun `blank translated fields preserve the addon text on cards and details`() {
        val value = enrichment(" ", "\n")
        assertEquals(card().name, card().withLocalizedTmdbText(value).name)
        assertEquals(card().description, card().withLocalizedTmdbText(value).description)
        assertEquals(detail().name, detail().withLocalizedTmdbText(value).name)
        assertEquals(detail().description, detail().withLocalizedTmdbText(value).description)
    }

    private fun card() = MetaPreview(
        id = "tt0077651",
        type = ContentType.MOVIE,
        name = "Halloween",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Fifteen years after murdering his sister...",
        releaseInfo = "1978",
        imdbRating = 7.6f,
        genres = emptyList()
    )

    private fun detail() = Meta(
        id = "tt0077651",
        type = ContentType.MOVIE,
        name = "Halloween",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Fifteen years after murdering his sister...",
        releaseInfo = "1978",
        imdbRating = 7.6f,
        genres = emptyList(),
        runtime = "91 min",
        director = emptyList(),
        cast = emptyList(),
        videos = emptyList(),
        country = null,
        awards = null,
        language = "en",
        links = emptyList()
    )

    private fun enrichment(localizedTitle: String?, description: String?) = TmdbEnrichment(
        localizedTitle = localizedTitle,
        description = description,
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
}
