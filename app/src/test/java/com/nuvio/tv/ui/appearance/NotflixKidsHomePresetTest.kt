package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.FolderViewMode
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M66: the kids home is a derived row group built from the «Inicio Notflix» pattern. */
class NotflixKidsHomePresetTest {
    private val texts = NotflixKidsHomeTexts(
        animatedMovies = "Anim movies",
        familyMovies = "Family movies",
        animatedSeries = "Anim series",
        familySeries = "Family series"
    )

    @Test fun fourKidsRowsKeepTheApprovedOrderSourcesAndReceivedTexts() {
        val collection = NotflixKidsHomePreset.create(texts)
        assertEquals("notflix-kids", collection.id)
        assertNotEquals(NotflixHomeCollectionPreset.ID, collection.id)
        assertEquals(FolderViewMode.ROWS, collection.viewMode)
        assertTrue(collection.pinToTop)
        assertEquals(
            listOf("Anim movies", "Family movies", "Anim series", "Family series"),
            collection.folders.map { it.title }
        )
        assertEquals(listOf("kids-animacion", "kids-familia", "kids-series-animacion", "kids-series-familia"),
            collection.folders.map { it.id })
        collection.folders.forEach { folder ->
            assertEquals(PosterShape.POSTER, folder.tileShape)
            val source = folder.sources.single() as AddonCatalogCollectionSource
            assertEquals("com.linvo.cinemeta", source.addonId)
            assertEquals("top", source.catalogId)
        }
        assertEquals(listOf("movie", "movie", "series", "series"),
            collection.folders.map { (it.sources.single() as AddonCatalogCollectionSource).type })
        assertEquals(listOf("Animation", "Family", "Animation", "Family"),
            collection.folders.map { (it.sources.single() as AddonCatalogCollectionSource).genre })
    }

    @Test fun newTextsProduceNewRowsAndIdentitiesStayStable() {
        val other = texts.copy(animatedSeries = "Other series")
        assertEquals("Other series", NotflixKidsHomePreset.create(other).folders[2].title)
        assertEquals(NotflixKidsHomePreset.create(texts), NotflixKidsHomePreset.create(texts))
    }
    @Test fun builtInFolderRouteResolvesOnlyForTheKidsPresetAndNeverNeedsAStoredCollection() {
        val derived = requireNotNull(notflixKidsHomeCollection(NotflixKidsHomePreset.ID,
            com.nuvio.tv.core.profile.AgeLevel.TP, texts))
        assertEquals("Anim series", derived.folders.single { it.id == "kids-series-animacion" }.title)
        org.junit.Assert.assertNull(notflixKidsHomeCollection("notflix-inicio", com.nuvio.tv.core.profile.AgeLevel.TP, texts))
        org.junit.Assert.assertNull(notflixKidsHomeCollection(NotflixKidsHomePreset.ID, null, texts))
        org.junit.Assert.assertNull(notflixKidsHomeCollection(NotflixKidsHomePreset.ID, com.nuvio.tv.core.profile.AgeLevel.A7, texts))
    }
}
