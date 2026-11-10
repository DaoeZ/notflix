package com.nuvio.tv.ui.screens.tmdb

import com.nuvio.tv.core.tmdb.TmdbEntityKind
import com.nuvio.tv.core.tmdb.TmdbEntityMediaType
import com.nuvio.tv.core.tmdb.TmdbEntityRail
import com.nuvio.tv.core.tmdb.TmdbEntityRailType
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M48: reglas de la página de estudio/cadena. Son puras para probarlas sin Compose ni recursos. */
class NotflixEntityBrowseTest {

    // ─── Línea secundaria ───

    private val kindLabels: (TmdbEntityKind) -> String = { kind ->
        when (kind) {
            TmdbEntityKind.COMPANY -> "Productora"
            TmdbEntityKind.NETWORK -> "Cadena"
        }
    }

    private fun line(
        kind: TmdbEntityKind,
        originCountry: String? = null,
        secondaryLabel: String? = null,
        country: (String) -> String? = { code -> if (code == "ES") "España" else null }
    ): String = notflixEntitySecondaryLine(
        kind = kind,
        originCountry = originCountry,
        secondaryLabel = secondaryLabel,
        kindLabel = kindLabels,
        countryLabel = country
    )

    @Test
    fun companyLineShowsKindAndCountry() {
        assertEquals(
            "Productora · España",
            line(TmdbEntityKind.COMPANY, originCountry = "ES")
        )
    }

    @Test
    fun networkLineWithoutCountryOmitsIt() {
        assertEquals("Cadena", line(TmdbEntityKind.NETWORK))
        assertEquals(
            "Cadena · España",
            line(TmdbEntityKind.NETWORK, originCountry = " ES ")
        )
    }

    @Test
    fun secondaryLabelIsKeptWhenItAddsInformation() {
        assertEquals(
            "Productora · España · Burbank, California",
            line(
                TmdbEntityKind.COMPANY,
                originCountry = "ES",
                secondaryLabel = "Burbank, California"
            )
        )
    }

    @Test
    fun aSecondaryLabelThatRepeatsTheCountryIsNotDuplicated() {
        assertEquals(
            "Productora · España",
            line(
                TmdbEntityKind.COMPANY,
                originCountry = "ES",
                secondaryLabel = "  españa "
            )
        )
        assertEquals(
            "Cadena",
            line(TmdbEntityKind.NETWORK, secondaryLabel = "   ")
        )
    }

    @Test
    fun unknownCountryFallsBackToTheCode() {
        assertEquals("US", notflixEntityCountryLabel("US") { null })
        assertEquals("US", notflixEntityCountryLabel(" US ") { "" })
        assertEquals("MX", notflixEntityCountryLabel("MX") { "MX" })
        assertNull(notflixEntityCountryLabel(null) { "España" })
        assertNull(notflixEntityCountryLabel("   ") { "España" })
    }

    @Test
    fun aResolvedCountryUsesTheLocaleName() {
        assertEquals(
            "Estados Unidos",
            notflixEntityCountryLabel("US") { "Estados Unidos" }
        )
        assertEquals(
            "Productora · Estados Unidos",
            line(TmdbEntityKind.COMPANY, originCountry = "US") { "Estados Unidos" }
        )
    }

    // ─── Tipo por defecto ───

    private fun meta(id: String): MetaPreview = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        name = "Título $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    private fun rail(
        mediaType: TmdbEntityMediaType,
        railType: TmdbEntityRailType,
        size: Int
    ): TmdbEntityRail = TmdbEntityRail(
        mediaType = mediaType,
        railType = railType,
        items = List(size) { index -> meta("${mediaType.value}-${railType.value}-$index") }
    )

    @Test
    fun theTypeWithMostPopularItemsIsSelectedByDefault() {
        val rails = listOf(
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.POPULAR, 3),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 8)
        )
        assertEquals(TmdbEntityMediaType.TV, notflixEntityDefaultMediaType(rails))
    }

    @Test
    fun tiesKeepTheRailOrderOfTheService() {
        val movieFirst = listOf(
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.POPULAR, 5),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 5)
        )
        assertEquals(TmdbEntityMediaType.MOVIE, notflixEntityDefaultMediaType(movieFirst))

        val tvFirst = listOf(
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 5),
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.POPULAR, 5)
        )
        assertEquals(TmdbEntityMediaType.TV, notflixEntityDefaultMediaType(tvFirst))
    }

    @Test
    fun withoutPopularRailsTheTotalItemsDecide() {
        val rails = listOf(
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.TOP_RATED, 2),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.TOP_RATED, 7),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.RECENT, 4)
        )
        assertEquals(TmdbEntityMediaType.TV, notflixEntityDefaultMediaType(rails))
    }

    @Test
    fun aSingleTypeIsAlwaysSelectedAndNoRailsGiveNull() {
        val rails = listOf(rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 1))
        assertEquals(TmdbEntityMediaType.TV, notflixEntityDefaultMediaType(rails))
        assertNull(notflixEntityDefaultMediaType(emptyList()))
    }

    // ─── Selector y filas ───

    @Test
    fun theTypeSelectorOnlyAppearsWithRowsOfBothTypes() {
        val both = listOf(
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.POPULAR, 4),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 6)
        )
        assertTrue(notflixEntityHasBothTypes(both))

        val emptyTv = listOf(
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.POPULAR, 4),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 0)
        )
        assertFalse(notflixEntityHasBothTypes(emptyTv))
        assertFalse(notflixEntityHasBothTypes(emptyList()))
    }

    @Test
    fun rowsOfTheSelectedTypeDropEmptyOnes() {
        val rails = listOf(
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.POPULAR, 4),
            rail(TmdbEntityMediaType.MOVIE, TmdbEntityRailType.TOP_RATED, 0),
            rail(TmdbEntityMediaType.TV, TmdbEntityRailType.POPULAR, 2)
        )
        val movies = notflixEntityRailsOf(rails, TmdbEntityMediaType.MOVIE)
        assertEquals(1, movies.size)
        assertEquals(TmdbEntityRailType.POPULAR, movies.first().railType)
        assertEquals(
            listOf(TmdbEntityMediaType.TV),
            notflixEntityRailsOf(rails, TmdbEntityMediaType.TV).map { it.mediaType }
        )
    }

    // ─── Títulos de fila ───

    private fun title(
        railType: TmdbEntityRailType,
        mediaType: TmdbEntityMediaType,
        name: String = "Warner Bros."
    ): String = notflixEntityRowTitle(
        railType = railType,
        mediaType = mediaType,
        entityName = name,
        popularLabel = { entity -> "Lo más popular de $entity" },
        topRatedMoviesLabel = { "Mejor valoradas" },
        topRatedSeriesLabel = { "Mejor valoradas series" },
        recentLabel = { "Novedades" }
    )

    @Test
    fun popularRowsUseTheEntityName() {
        assertEquals(
            "Lo más popular de Warner Bros.",
            title(TmdbEntityRailType.POPULAR, TmdbEntityMediaType.MOVIE)
        )
        assertEquals(
            "Lo más popular de Warner Bros.",
            title(TmdbEntityRailType.POPULAR, TmdbEntityMediaType.TV)
        )
    }

    @Test
    fun topRatedChangesWithTheMediaType() {
        assertEquals(
            "Mejor valoradas",
            title(TmdbEntityRailType.TOP_RATED, TmdbEntityMediaType.MOVIE)
        )
        assertEquals(
            "Mejor valoradas series",
            title(TmdbEntityRailType.TOP_RATED, TmdbEntityMediaType.TV)
        )
    }

    @Test
    fun recentRowsAreNewReleases() {
        assertEquals(
            "Novedades",
            title(TmdbEntityRailType.RECENT, TmdbEntityMediaType.MOVIE)
        )
    }

    @Test
    fun englishWordingIsAlsoPossibleThroughTheSameRule() {
        assertEquals(
            "Top rated series",
            notflixEntityRowTitle(
                railType = TmdbEntityRailType.TOP_RATED,
                mediaType = TmdbEntityMediaType.TV,
                entityName = "HBO",
                popularLabel = { "Most popular from $it" },
                topRatedMoviesLabel = { "Top rated movies" },
                topRatedSeriesLabel = { "Top rated series" },
                recentLabel = { "New releases" }
            )
        )
    }

    // ─── Umbral de carga de la página siguiente ───

    private fun loadMore(
        lastVisible: Int,
        total: Int,
        hasMore: Boolean = true,
        isLoading: Boolean = false
    ): Boolean = notflixEntityShouldLoadMore(
        lastVisibleIndex = lastVisible,
        totalItems = total,
        hasMore = hasMore,
        isLoading = isLoading
    )

    @Test
    fun fourItemsFromTheEndRequestsTheNextPage() {
        assertFalse(loadMore(lastVisible = 15, total = 20))
        assertTrue(loadMore(lastVisible = 16, total = 20))
        assertTrue(loadMore(lastVisible = 19, total = 20))
    }

    @Test
    fun noMorePagesOrAnInFlightPageDoNotRequestAgain() {
        assertFalse(loadMore(lastVisible = 19, total = 20, hasMore = false))
        assertFalse(loadMore(lastVisible = 19, total = 20, isLoading = true))
    }

    @Test
    fun emptyOrNotMeasuredRowsNeverRequest() {
        assertFalse(loadMore(lastVisible = 0, total = 0))
        assertFalse(loadMore(lastVisible = -1, total = 20))
    }
}
