package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.FolderViewMode
import com.nuvio.tv.domain.model.PosterShape

/** Product definition approved 2026-11-02. Existing collections are never migrated here. */
internal object NotflixHomeCollectionPreset {
    const val ID = "notflix-inicio"
    const val TITLE = "Inicio Notflix"
    private const val ADDON = "com.linvo.cinemeta"

    fun create(): Collection = Collection(
        id = ID, title = TITLE, pinToTop = true, viewMode = FolderViewMode.ROWS, showAllTab = false,
        folders = listOf(
            folder("peliculas-populares", "Películas populares", "movie", "top"),
            folder("series-populares", "Series populares", "series", "top"),
            folder("animacion", "Películas de animación", "movie", "top", "Animation"),
            folder("novedades-peliculas", "Novedades en películas", "movie", "year", "2026"),
            folder("novedades-series", "Novedades en series", "series", "year", "2026"),
            folder("familia", "Para ver en familia", "movie", "top", "Family"),
            folder("accion", "Acción", "movie", "top", "Action"),
            folder("comedia", "Comedia", "movie", "top", "Comedy"),
            folder("ciencia-ficcion", "Ciencia ficción", "movie", "top", "Sci-Fi"),
            folder("series-drama", "Series de drama", "series", "top", "Drama"),
            folder("peliculas-destacadas", "Películas destacadas", "movie", "imdbRating")
        )
    )

    private fun folder(id: String, title: String, type: String, catalog: String, genre: String? = null) =
        CollectionFolder(id = id, title = title, tileShape = PosterShape.POSTER,
            sources = listOf(AddonCatalogCollectionSource(ADDON, type, catalog, genre)))
}
