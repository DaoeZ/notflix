package com.nuvio.tv.core.profile

import java.util.Locale

// Escala española de 2026 sin confirmar oficialmente; cambiar solo aquí.
enum class AgeLevel { TP, A7, A12, A16, A18, X }

/**
 * Único mapa de cadenas a niveles. Prioridad España; alias internacionales
 * conservadores. Cualquier cadena no reconocida devuelve null (sin clasificación).
 */
fun ageLevelOf(rating: String?): AgeLevel? {
    val normalized = normalizeRating(rating) ?: return null
    aliases[normalized]?.let { return it }
    val numeric = normalized.toIntOrNull() ?: return null
    return when {
        numeric <= 0 -> AgeLevel.TP
        numeric <= 7 -> AgeLevel.A7
        numeric <= 12 -> AgeLevel.A12
        numeric <= 16 -> AgeLevel.A16
        numeric <= 18 -> AgeLevel.A18
        else -> AgeLevel.X
    }
}

/** Etiqueta española para mostrar cuando la clasificación la resolvió TMDB. */
fun AgeLevel.displayRating(): String = when (this) {
    AgeLevel.TP -> "TP"
    AgeLevel.A7 -> "7"
    AgeLevel.A12 -> "12"
    AgeLevel.A16 -> "16"
    AgeLevel.A18 -> "18"
    AgeLevel.X -> "X"
}

private fun normalizeRating(rating: String?): String? {
    var value = rating?.trim()?.uppercase(Locale.US)?.replace(" ", "") ?: return null
    if (value.isEmpty()) return null
    if (value.startsWith("ES-")) value = value.removePrefix("ES-")
    if (value.startsWith("+")) value = value.removePrefix("+")
    return value.ifEmpty { null }
}

private val aliases: Map<String, AgeLevel> = mapOf(
    // España
    "TP" to AgeLevel.TP,
    "APTA" to AgeLevel.TP,
    "A" to AgeLevel.TP,
    "ALL" to AgeLevel.TP,
    "7" to AgeLevel.A7,
    "12" to AgeLevel.A12,
    "16" to AgeLevel.A16,
    "18" to AgeLevel.A18,
    "X" to AgeLevel.X,
    // Estados Unidos, cine
    "G" to AgeLevel.TP,
    "PG" to AgeLevel.A7,
    "PG-13" to AgeLevel.A12,
    "R" to AgeLevel.A16,
    "NC-17" to AgeLevel.A18,
    // Estados Unidos, televisión
    "TV-Y" to AgeLevel.TP,
    "TV-G" to AgeLevel.TP,
    "TV-Y7" to AgeLevel.A7,
    "TV-Y7-FV" to AgeLevel.A7,
    "TV-PG" to AgeLevel.A7,
    "TV-14" to AgeLevel.A12,
    "TV-MA" to AgeLevel.A16,
    // Reino Unido
    "U" to AgeLevel.TP,
    "12A" to AgeLevel.A12,
    "15" to AgeLevel.A16,
    "R18" to AgeLevel.A18
)
