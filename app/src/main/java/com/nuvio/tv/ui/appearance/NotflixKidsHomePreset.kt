package com.nuvio.tv.ui.appearance

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.util.withAppLocale
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.isKidsAgeLimit
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.FolderViewMode
import com.nuvio.tv.domain.model.PosterShape

/**
 * M66: localized row names for the kids home. Resolved by the ViewModel from the app resources,
 * so the names follow the app language exactly like the «Inicio Notflix» folder names do.
 */
internal data class NotflixKidsHomeTexts(
    val animatedMovies: String,
    val familyMovies: String,
    val animatedSeries: String,
    val familySeries: String
)

/**
 * M66: the kids home is derived, never stored. A KIDS profile gets these four Cinemeta rows
 * instead of the account's rows, and the collection manager has nothing to edit or delete
 * because no collection is written to the profile's store.
 *
 * The strict age filter is not relaxed here: «Animation» is not «kids», so every row still goes
 * through ActiveProfileAgeGate before it reaches the screen.
 */
internal object NotflixKidsHomePreset {
    const val ID = "notflix-kids"
    private const val ADDON = "com.linvo.cinemeta"

    fun create(texts: NotflixKidsHomeTexts): Collection = Collection(
        // The synthetic row group is expanded into folder rows before it is ever drawn, so its
        // own title is unused and deliberately empty rather than an untranslated placeholder.
        id = ID, title = "", pinToTop = true, viewMode = FolderViewMode.ROWS, showAllTab = false,
        folders = listOf(
            folder("kids-animacion", texts.animatedMovies, "movie", "Animation"),
            folder("kids-familia", texts.familyMovies, "movie", "Family"),
            folder("kids-series-animacion", texts.animatedSeries, "series", "Animation"),
            folder("kids-series-familia", texts.familySeries, "series", "Family")
        )
    )

    private fun folder(id: String, title: String, type: String, genre: String) =
        CollectionFolder(id = id, title = title, tileShape = PosterShape.POSTER,
            sources = listOf(AddonCatalogCollectionSource(ADDON, type, "top", genre)))
}

/** Resolve against the chosen app language, including ViewModels with ApplicationContext. */
internal fun notflixKidsHomeTexts(context: Context): NotflixKidsHomeTexts = with(context.withAppLocale()) {
    NotflixKidsHomeTexts(
        getString(R.string.notflix_home_folder_animated_movies),
        getString(R.string.notflix_home_folder_family_movies),
        getString(R.string.notflix_kids_folder_animated_series),
        getString(R.string.notflix_kids_folder_family_series)
    )
}

/** The built-in read-only group is resolved on demand; never insert it in CollectionsDataStore. */
internal fun notflixKidsHomeCollection(id: String, limit: AgeLevel?, texts: NotflixKidsHomeTexts): Collection? =
    if (id == NotflixKidsHomePreset.ID && isKidsAgeLimit(limit)) NotflixKidsHomePreset.create(texts) else null
