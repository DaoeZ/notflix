package com.nuvio.tv.ui.screens.collection

import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** M67: los títulos ocultos del perfil desaparecen de la rejilla de una carpeta de colección. */
class NotflixFolderHiddenTitlesTest {

    private fun meta(id: String, type: String = "movie"): MetaPreview = MetaPreview(
        id = id,
        type = ContentType.fromString(type),
        rawType = type,
        name = "Título $id",
        poster = "https://example.com/$id.jpg",
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = 7f,
        genres = emptyList()
    )

    private fun tab(vararg items: MetaPreview): FolderTab = FolderTab(
        label = "Popular",
        catalogRow = CatalogRow(
            addonId = "com.example",
            addonName = "Ejemplo",
            addonBaseUrl = "https://addon.example",
            catalogId = "popular",
            catalogName = "Popular",
            type = ContentType.MOVIE,
            items = items.toList()
        )
    )

    @Test
    fun `hidden titles leave the folder grid and its metadata`() {
        val folderTab = tab(meta("tt1"), meta("tt2"), meta("tt3", "series"))

        val visible = notflixFolderTabData(0, folderTab, emptyMap())
        assertEquals(listOf("movie:tt1", "movie:tt2", "series:tt3"), visible.tab.items.map { it.key })

        val hidden = notflixFolderTabData(
            0, folderTab, emptyMap(), setOf(hiddenTitleKey("movie", "tt1"))
        )
        assertEquals(listOf("movie:tt2", "series:tt3"), hidden.tab.items.map { it.key })
        assertEquals(setOf("movie:tt2", "series:tt3"), hidden.itemsByKey.keys)
    }

    @Test
    fun `keys of other types or profiles do not take anything away`() {
        val folderTab = tab(meta("tt1"), meta("tt2", "series"))
        val hidden = notflixFolderTabData(
            0, folderTab, emptyMap(), setOf(hiddenTitleKey("series", "tt1"))
        )
        assertEquals(listOf("movie:tt1", "series:tt2"), hidden.tab.items.map { it.key })
    }
}
