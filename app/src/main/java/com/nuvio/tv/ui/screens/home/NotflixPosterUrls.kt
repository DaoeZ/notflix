package com.nuvio.tv.ui.screens.home

private val TMDB_POSTER_SIZE_REGEX = Regex("/t/p/(w92|w154|w185|w342)/")

/**
 * Devuelve una variante más nítida de la URL del póster para la tarjeta enfocada.
 * Solo sube los tamaños conocidos; cualquier otra URL (incluida null) se queda igual.
 */
fun sharperPosterUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    // Metahub's large poster is re-cropped, not just bigger: focusing a card visibly swapped the
    // artwork (promoter 09-11). Only TMDB sizes are the same file, so only those are upgraded.
    if (url.contains("image.tmdb.org/t/p/")) {
        return url.replace(TMDB_POSTER_SIZE_REGEX, "/t/p/w500/")
    }
    return url
}
