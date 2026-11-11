package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.FolderViewMode
import com.nuvio.tv.domain.model.PosterShape

/** Localized texts resolved by the screen with stringResource; the app language decides their values. */
internal data class NotflixHomeCollectionTexts(
    val title: String,
    val popularMovies: String,
    val popularSeries: String,
    val animatedMovies: String,
    val newMovies: String,
    val newSeries: String,
    val familyMovies: String,
    val action: String,
    val comedy: String,
    val sciFi: String,
    val dramaSeries: String,
    val featuredMovies: String
)

/** Product definition approved 2026-11-02. Existing collections are never migrated here. */
internal object NotflixHomeCollectionPreset {
    const val ID = "notflix-inicio"
    private const val ADDON = "com.linvo.cinemeta"

    fun create(texts: NotflixHomeCollectionTexts, year: Int): Collection = Collection(
        id = ID, title = texts.title, pinToTop = true, viewMode = FolderViewMode.ROWS, showAllTab = false,
        folders = listOf(
            folder("peliculas-populares", texts.popularMovies, "movie", "top"),
            folder("series-populares", texts.popularSeries, "series", "top"),
            folder("animacion", texts.animatedMovies, "movie", "top", "Animation"),
            folder("novedades-peliculas", texts.newMovies, "movie", "year", year.toString()),
            folder("novedades-series", texts.newSeries, "series", "year", year.toString()),
            folder("familia", texts.familyMovies, "movie", "top", "Family"),
            folder("accion", texts.action, "movie", "top", "Action"),
            folder("comedia", texts.comedy, "movie", "top", "Comedy"),
            folder("ciencia-ficcion", texts.sciFi, "movie", "top", "Sci-Fi"),
            folder("series-drama", texts.dramaSeries, "series", "top", "Drama"),
            folder("peliculas-destacadas", texts.featuredMovies, "movie", "imdbRating")
        )
    )

    private fun folder(id: String, title: String, type: String, catalog: String, genre: String? = null) =
        CollectionFolder(id = id, title = title, tileShape = PosterShape.POSTER,
            sources = listOf(AddonCatalogCollectionSource(ADDON, type, catalog, genre)))
}
