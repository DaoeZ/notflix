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
    private val expected = listOf(
        listOf("Películas populares", "movie", "top", null),
        listOf("Series populares", "series", "top", null),
        listOf("Películas de animación", "movie", "top", "Animation"),
        listOf("Novedades en películas", "movie", "year", "2026"),
        listOf("Novedades en series", "series", "year", "2026"),
        listOf("Para ver en familia", "movie", "top", "Family"),
        listOf("Acción", "movie", "top", "Action"),
        listOf("Comedia", "movie", "top", "Comedy"),
        listOf("Ciencia ficción", "movie", "top", "Sci-Fi"),
        listOf("Series de drama", "series", "top", "Drama"),
        listOf("Películas destacadas", "movie", "imdbRating", null)
    )

    @Test fun approvedDefinitionHasExactOrderSourcesAndNoInventedRows() {
        val preset = NotflixHomeCollectionPreset.create()
        assertEquals("notflix-inicio", preset.id); assertEquals("Inicio Notflix", preset.title)
        assertTrue(preset.pinToTop); assertFalse(preset.showAllTab); assertEquals(FolderViewMode.ROWS, preset.viewMode)
        assertEquals(11, preset.folders.size); assertEquals(11, preset.folders.map { it.id }.toSet().size)
        preset.folders.zip(expected).forEach { (folder, row) ->
            assertEquals(row[0], folder.title); assertEquals(PosterShape.POSTER, folder.tileShape)
            val source = folder.sources.single() as AddonCatalogCollectionSource
            assertEquals("com.linvo.cinemeta", source.addonId)
            assertEquals(row[1], source.type); assertEquals(row[2], source.catalogId); assertEquals(row[3], source.genre)
        }
    }

    @Test fun stableUniqueFolderIdentitiesAndNewInstancesDoNotUseClockOrMigration() {
        val a = NotflixHomeCollectionPreset.create(); val b = NotflixHomeCollectionPreset.create()
        assertEquals(a, b); assertNotSame(a, b); assertTrue(a.folders.all { it.id.isNotBlank() })
        assertEquals(listOf("2026", "2026"), a.folders.filter { it.id.startsWith("novedades-") }
            .map { (it.sources.single() as AddonCatalogCollectionSource).genre })
    }

    @Test fun officialSerializationRoundTripRetainsAllElevenDefinitions() {
        val manager = mockk<ProfileManager>()
        every { manager.activeProfileId } returns MutableStateFlow(2)
        val store = CollectionsDataStore(mockk<Context>(relaxed=true), mockk<ProfileDataStoreFactory>(), manager)
        val original = NotflixHomeCollectionPreset.create()
        val json = store.exportToJson(listOf(original))
        assertEquals(original, store.importFromJson(json).single())
        assertTrue(json.contains("\"provider\":\"addon\"")); assertFalse(json.contains("notflixVersion"))
    }
}
