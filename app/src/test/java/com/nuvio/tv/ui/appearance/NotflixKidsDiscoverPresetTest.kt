package com.nuvio.tv.ui.appearance

import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.FolderViewMode
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.domain.model.TmdbCollectionSort
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TmdbCollectionSourceType
import com.nuvio.tv.ui.screens.home.notflixCollectionSpecsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M68: the KIDS rows come from TMDB Discover and keep their M66 Cinemeta fallback. */
class NotflixKidsDiscoverPresetTest {
    private val texts = NotflixKidsHomeTexts("Anim movies", "Family movies", "Anim series", "Family series")
    private val addon = Addon("com.linvo.cinemeta", "Cinemeta", version = "1", description = null, logo = null,
        baseUrl = "https://cinemeta.invalid", catalogs = emptyList(),
        types = listOf(ContentType.MOVIE, ContentType.SERIES), resources = emptyList())

    @Test fun discoverRowsKeepTheM66IdsTitlesAndOrder() {
        val collection = NotflixKidsHomePreset.createDiscover(texts)

        assertEquals("notflix-kids", collection.id)
        assertEquals(FolderViewMode.ROWS, collection.viewMode)
        assertTrue(collection.pinToTop)
        assertEquals(listOf("kids-animacion", "kids-familia", "kids-series-animacion", "kids-series-familia"),
            collection.folders.map { it.id })
        assertEquals(listOf("Anim movies", "Family movies", "Anim series", "Family series"),
            collection.folders.map { it.title })
        assertTrue(collection.folders.all { it.tileShape == PosterShape.POSTER })
    }

    @Test fun eachDiscoverRowAsksForItsGenreCertificatesAndSort() {
        val sources = NotflixKidsHomePreset.createDiscover(texts).folders.map {
            it.sources.single() as TmdbCollectionSource
        }

        assertTrue(sources.all { it.sourceType == TmdbCollectionSourceType.DISCOVER })
        assertTrue(sources.all { it.sortBy == TmdbCollectionSort.POPULAR_DESC.value })
        assertEquals(listOf(TmdbCollectionMediaType.MOVIE, TmdbCollectionMediaType.MOVIE,
            TmdbCollectionMediaType.TV, TmdbCollectionMediaType.TV), sources.map { it.mediaType })
        assertEquals(listOf("16", "10751", "16", "10762"), sources.map { it.filters.withGenres })
        assertEquals("80,18,10768", sources[2].filters.withoutGenres)
        // Movies are filtered by Spain's all-ages certificate; series have no server-side rating.
        assertEquals(listOf("ES", "ES", null, null), sources.map { it.filters.certificationCountry })
        assertEquals(listOf("A|Ai", "A|Ai", null, null), sources.map { it.filters.certification })
        assertEquals(listOf(50, 50, null, null), sources.map { it.filters.voteCountGte })
    }

    @Test fun theCinemetaFallbackOfEachRowKeepsTheSameFolderIdAndGenre() {
        val movies = NotflixKidsHomePreset.fallbackSource("kids-animacion")!!
        val family = NotflixKidsHomePreset.fallbackSource("kids-familia")!!
        val animatedSeries = NotflixKidsHomePreset.fallbackSource("kids-series-animacion")!!
        val kidsSeries = NotflixKidsHomePreset.fallbackSource("kids-series-familia")!!

        assertEquals(listOf("com.linvo.cinemeta", "com.linvo.cinemeta", "com.linvo.cinemeta", "com.linvo.cinemeta"),
            listOf(movies, family, animatedSeries, kidsSeries).map { it.addonId })
        assertEquals(listOf("top", "top", "top", "top"), listOf(movies, family, animatedSeries, kidsSeries).map { it.catalogId })
        assertEquals(listOf("movie", "movie", "series", "series"),
            listOf(movies, family, animatedSeries, kidsSeries).map { it.type })
        assertEquals(listOf("Animation", "Family", "Animation", "Family"),
            listOf(movies, family, animatedSeries, kidsSeries).map { it.genre })
        assertNull(NotflixKidsHomePreset.fallbackSource("unknown"))
    }

    @Test fun theFolderRouteChoosesTheVariantAndStillNeedsTheKidsLimit() {
        val discover = requireNotNull(notflixKidsHomeCollection(NotflixKidsHomePreset.ID, AgeLevel.TP, texts, discover = true))
        val cinemeta = requireNotNull(notflixKidsHomeCollection(NotflixKidsHomePreset.ID, AgeLevel.TP, texts))

        assertTrue(discover.folders.all { it.sources.single() is TmdbCollectionSource })
        assertTrue(cinemeta.folders.all { it.sources.single() is AddonCatalogCollectionSource })
        assertNull(notflixKidsHomeCollection(NotflixKidsHomePreset.ID, AgeLevel.A7, texts, discover = true))
        assertNull(notflixKidsHomeCollection("notflix-inicio", AgeLevel.TP, texts, discover = true))
    }

    @Test fun discoverSpecsCarryTheTmdbSourceAndTheCinemetaFallbackRequest() {
        val specs = notflixCollectionSpecsFor(
            NotflixKidsHomePreset.createDiscover(texts), listOf(addon), emptySet(),
            fallbackSource = { NotflixKidsHomePreset.fallbackSource(it) }
        )

        assertEquals(4, specs.size)
        assertTrue(specs.all { it.tmdb != null && it.request == null && it.source == null })
        assertEquals(listOf("movie", "movie", "series", "series"), specs.map { it.fallbackRequest!!.type })
        assertEquals(listOf("Animation", "Family", "Animation", "Family"),
            specs.map { it.fallbackRequest!!.extraArgs["genre"] })
        assertNotNull(specs.first().tmdb)
    }
}
