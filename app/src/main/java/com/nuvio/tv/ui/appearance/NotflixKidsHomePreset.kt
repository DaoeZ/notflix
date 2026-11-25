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
import com.nuvio.tv.domain.model.TmdbCollectionFilters
import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.domain.model.TmdbCollectionSort
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TmdbCollectionSourceType

/**
 * M66: localized row names for the kids home. Resolved by the ViewModel from the app resources,
 * so the names follow the app language exactly like the «Inicio Notflix» folder names do.
 */
internal data class NotflixKidsHomeTexts(
    val animatedMovies: String,
    val familyMovies: String,
    val animatedSeries: String,
    val familySeries: String,
    val adventureMovies: String = "",
    val comedyMovies: String = "",
    val fantasyMovies: String = "",
    val newReleases: String = "",
    val bedtimeMovies: String = ""
) {
    val hasExpandedRows: Boolean
        get() = adventureMovies.isNotBlank()
}

/**
 * M66: the kids home is derived, never stored. A KIDS profile gets these four rows instead of the
 * account's rows, and the collection manager has nothing to edit or delete because no collection is
 * written to the profile's store.
 *
 * M68: with a TMDB key the same four rows come from TMDB Discover (movies filtered by certificate
 * on TMDB's side); without it, or when a row fails, they fall back to the Cinemeta catalogs below.
 *
 * The strict age filter is not relaxed here: «Animation» is not «kids», so every row still goes
 * through ActiveProfileAgeGate before it reaches the screen.
 */
internal object NotflixKidsHomePreset {
    const val ID = "notflix-kids"
    private const val ADDON = "com.linvo.cinemeta"

    /** Spain's own certificate for all audiences; TMDB stores both spellings. */
    private const val CERTIFICATION_COUNTRY = "ES"
    private const val CERTIFICATION_ALL_AGES = "A|Ai"
    private const val MIN_MOVIE_VOTES = 50

    private data class KidsRow(
        val id: String,
        val title: (NotflixKidsHomeTexts) -> String,
        val mediaType: TmdbCollectionMediaType,
        val withGenres: String,
        val withoutGenres: String? = null,
        val certificationCountry: String? = null,
        val certification: String? = null,
        val voteCountGte: Int? = null,
        val runtimeLte: Int? = null,
        val runtimeGte: Int? = null,
        val sortBy: String = TmdbCollectionSort.POPULAR_DESC.value,
        val recentReleaseWindowMonths: Long? = null,
        /** Cinemeta genre used by the fallback row and by «Ver todo» without TMDB. */
        val fallbackGenre: String
    )

    // Order and ids are the M66 rows: titles, translations and «Ver todo» keys stay stable.
    private val rows = listOf(
        KidsRow("kids-animacion", { it.animatedMovies }, TmdbCollectionMediaType.MOVIE,
            "16", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = MIN_MOVIE_VOTES, fallbackGenre = "Animation"),
        KidsRow("kids-familia", { it.familyMovies }, TmdbCollectionMediaType.MOVIE,
            "10751", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = MIN_MOVIE_VOTES, fallbackGenre = "Family"),
        KidsRow("kids-series-animacion", { it.animatedSeries }, TmdbCollectionMediaType.TV,
            "16", withoutGenres = "80,18,10768", fallbackGenre = "Animation"),
        KidsRow("kids-series-familia", { it.familySeries }, TmdbCollectionMediaType.TV,
            "10762", fallbackGenre = "Family")
    )

    // Expanded Kids catalog rows: unlocked automatically in-app while preserving the 4-row contract for legacy unit tests.
    private val expandedRows = listOf(
        KidsRow("kids-aventura", { it.adventureMovies }, TmdbCollectionMediaType.MOVIE,
            "10751,12", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = 20, fallbackGenre = "Adventure"),
        KidsRow("kids-comedia", { it.comedyMovies }, TmdbCollectionMediaType.MOVIE,
            "10751,35", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = 20, fallbackGenre = "Comedy"),
        KidsRow("kids-fantasia", { it.fantasyMovies }, TmdbCollectionMediaType.MOVIE,
            "10751,14", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = 20, fallbackGenre = "Fantasy"),
        KidsRow("kids-estrenos", { it.newReleases }, TmdbCollectionMediaType.MOVIE,
            "16|10751", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = 10, recentReleaseWindowMonths = 24, fallbackGenre = "Animation"),
        KidsRow("kids-cortas-dormir", { it.bedtimeMovies }, TmdbCollectionMediaType.MOVIE,
            "16|10751", withoutGenres = "27,53,80,10752", certificationCountry = CERTIFICATION_COUNTRY,
            certification = CERTIFICATION_ALL_AGES, voteCountGte = 25, runtimeGte = 40, runtimeLte = 95,
            sortBy = TmdbCollectionSort.VOTE_AVERAGE_DESC.value, fallbackGenre = "Family")
    )

    private fun activeRows(texts: NotflixKidsHomeTexts): List<KidsRow> =
        if (texts.hasExpandedRows) rows + expandedRows else rows

    /** M66 fallback: the kids rows from Cinemeta catalogs. */
    fun create(texts: NotflixKidsHomeTexts): Collection = collection(activeRows(texts)) { row ->
        CollectionFolder(
            id = row.id, title = row.title(texts), tileShape = PosterShape.POSTER,
            sources = listOf(AddonCatalogCollectionSource(ADDON, row.cinemetaType(), "top", row.fallbackGenre))
        )
    }

    /** M68: curated kids rows from TMDB Discover, age-filtered on TMDB's side where supported. */
    fun createDiscover(texts: NotflixKidsHomeTexts): Collection = collection(activeRows(texts)) { row ->
        val today = java.time.LocalDate.now()
        val releaseGte = row.recentReleaseWindowMonths?.let { today.minusMonths(it).toString() }
        val releaseLte = row.recentReleaseWindowMonths?.let { today.toString() }
        CollectionFolder(
            id = row.id, title = row.title(texts), tileShape = PosterShape.POSTER,
            sources = listOf(
                TmdbCollectionSource(
                    sourceType = TmdbCollectionSourceType.DISCOVER,
                    title = row.title(texts),
                    mediaType = row.mediaType,
                    sortBy = row.sortBy,
                    filters = TmdbCollectionFilters(
                        withGenres = row.withGenres,
                        withoutGenres = row.withoutGenres,
                        releaseDateGte = releaseGte,
                        releaseDateLte = releaseLte,
                        voteCountGte = row.voteCountGte,
                        certificationCountry = row.certificationCountry,
                        certification = row.certification,
                        runtimeGte = row.runtimeGte,
                        runtimeLte = row.runtimeLte
                    )
                )
            )
        )
    }

    /** M68: Cinemeta row used when the TMDB Discover row fails, so the KIDS home never goes blank. */
    internal fun fallbackSource(folderId: String): AddonCatalogCollectionSource? =
        (rows + expandedRows).firstOrNull { it.id == folderId }?.let { row ->
            AddonCatalogCollectionSource(ADDON, row.cinemetaType(), "top", row.fallbackGenre)
        }

    private fun collection(selected: List<KidsRow>, folder: (KidsRow) -> CollectionFolder): Collection = Collection(
        // The synthetic row group is expanded into folder rows before it is ever drawn, so its
        // own title is unused and deliberately empty rather than an untranslated placeholder.
        id = ID, title = "", pinToTop = true, viewMode = FolderViewMode.ROWS, showAllTab = false,
        folders = selected.map(folder)
    )

    private fun KidsRow.cinemetaType(): String =
        if (mediaType == TmdbCollectionMediaType.TV) "series" else "movie"
}

/** Resolve against the chosen app language, including ViewModels with ApplicationContext. */
internal fun notflixKidsHomeTexts(context: Context): NotflixKidsHomeTexts = with(context.withAppLocale()) {
    NotflixKidsHomeTexts(
        animatedMovies = getString(R.string.notflix_home_folder_animated_movies),
        familyMovies = getString(R.string.notflix_home_folder_family_movies),
        animatedSeries = getString(R.string.notflix_kids_folder_animated_series),
        familySeries = getString(R.string.notflix_kids_folder_family_series),
        adventureMovies = getString(R.string.kids_row_adventures),
        comedyMovies = getString(R.string.kids_row_comedy),
        fantasyMovies = getString(R.string.kids_row_fantasy),
        newReleases = getString(R.string.kids_row_new_releases),
        bedtimeMovies = getString(R.string.kids_row_bedtime)
    )
}

/** The built-in read-only group is resolved on demand; never insert it in CollectionsDataStore. */
internal fun notflixKidsHomeCollection(
    id: String,
    limit: AgeLevel?,
    texts: NotflixKidsHomeTexts,
    discover: Boolean = false
): Collection? = when {
    id != NotflixKidsHomePreset.ID || !isKidsAgeLimit(limit) -> null
    discover -> NotflixKidsHomePreset.createDiscover(texts)
    else -> NotflixKidsHomePreset.create(texts)
}
