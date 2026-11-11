package com.nuvio.tv.ui.appearance

import android.content.Context
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.*
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class NotflixHomeCollectionPresetTest {
    private val texts = notflixCollectionFixtureTexts
    private val year = 2031
    private val expected = listOf(
        listOf(texts.popularMovies, "movie", "top", null),
        listOf(texts.popularSeries, "series", "top", null),
        listOf(texts.animatedMovies, "movie", "top", "Animation"),
        listOf(texts.newMovies, "movie", "year", year.toString()),
        listOf(texts.newSeries, "series", "year", year.toString()),
        listOf(texts.familyMovies, "movie", "top", "Family"),
        listOf(texts.action, "movie", "top", "Action"),
        listOf(texts.comedy, "movie", "top", "Comedy"),
        listOf(texts.sciFi, "movie", "top", "Sci-Fi"),
        listOf(texts.dramaSeries, "series", "top", "Drama"),
        listOf(texts.featuredMovies, "movie", "imdbRating", null)
    )

    @Test fun approvedDefinitionHasExactOrderSourcesAndUsesReceivedTextsAndYear() {
        val preset = NotflixHomeCollectionPreset.create(texts, year)
        assertEquals("notflix-inicio", preset.id); assertEquals(texts.title, preset.title)
        assertTrue(preset.pinToTop); assertFalse(preset.showAllTab); assertEquals(FolderViewMode.ROWS, preset.viewMode)
        assertEquals(11, preset.folders.size); assertEquals(11, preset.folders.map { it.id }.toSet().size)
        preset.folders.zip(expected).forEach { (folder, row) ->
            assertEquals(row[0], folder.title); assertEquals(PosterShape.POSTER, folder.tileShape)
            val source = folder.sources.single() as AddonCatalogCollectionSource
            assertEquals("com.linvo.cinemeta", source.addonId)
            assertEquals(row[1], source.type); assertEquals(row[2], source.catalogId); assertEquals(row[3], source.genre)
        }
    }

    @Test fun aDifferentTextsAndYearProduceADifferentPreset() {
        val other = texts.copy(title = "Other fixture", popularMovies = "Other movies")
        val first = NotflixHomeCollectionPreset.create(texts, year)
        val second = NotflixHomeCollectionPreset.create(other, 2042)
        assertEquals("Other fixture", second.title); assertEquals("Other movies", second.folders.first().title)
        assertEquals(listOf("2042", "2042"), second.folders.filter { it.id.startsWith("novedades-") }
            .map { (it.sources.single() as AddonCatalogCollectionSource).genre })
        assertNotEquals(first, second)
    }

    @Test fun stableUniqueFolderIdentitiesAndNewInstancesDoNotUseClockOrMigration() {
        val a = NotflixHomeCollectionPreset.create(texts, year)
        val b = NotflixHomeCollectionPreset.create(texts, year)
        assertEquals(a, b); assertNotSame(a, b); assertTrue(a.folders.all { it.id.isNotBlank() })
        assertEquals(listOf(year.toString(), year.toString()), a.folders.filter { it.id.startsWith("novedades-") }
            .map { (it.sources.single() as AddonCatalogCollectionSource).genre })
    }

    @Test fun officialSerializationRoundTripRetainsAllElevenDefinitions() {
        val manager = mockk<ProfileManager>()
        every { manager.activeProfileId } returns MutableStateFlow(2)
        val store = CollectionsDataStore(mockk<Context>(relaxed=true), mockk<ProfileDataStoreFactory>(), manager)
        val original = NotflixHomeCollectionPreset.create(texts, year)
        val json = store.exportToJson(listOf(original))
        assertEquals(original, store.importFromJson(json).single())
        assertTrue(json.contains("\"provider\":\"addon\"")); assertFalse(json.contains("notflixVersion"))
    }
}
