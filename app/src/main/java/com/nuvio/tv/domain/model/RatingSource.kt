package com.nuvio.tv.domain.model

/** M57: where a title's displayed rating really comes from; IMDB matches Stremio's convention. */
enum class RatingSource {
    IMDB,
    TMDB;

    /** Brand label drawn next to the value. Service names are not translated. */
    val label: String
        get() = when (this) {
            IMDB -> "IMDb"
            TMDB -> "TMDB"
        }
}
