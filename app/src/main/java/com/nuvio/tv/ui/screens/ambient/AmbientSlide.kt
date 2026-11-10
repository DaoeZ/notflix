package com.nuvio.tv.ui.screens.ambient

/** Tipo de contenido de la diapositiva, solo para la línea «Película · 2019 · Drama». */
enum class AmbientKind {
    MOVIE,
    SERIES
}

data class AmbientSlide(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String?,
    val genres: List<String>,
    /** M51: tipo y año ya presentes en el catálogo; nulos si el título no los trae. */
    val kind: AmbientKind? = null,
    val year: Int? = null
)
