package com.nuvio.tv.core.profile

/** Pura: sin límite todo pasa; con límite, solo clasificaciones conocidas dentro del nivel. */
fun isAllowed(limit: AgeLevel?, rating: AgeLevel?): Boolean =
    limit == null || (rating != null && rating.ordinal <= limit.ordinal)

/** Clave estable usada por la caché de clasificaciones y el estado de la puerta de edad. */
fun ageRatingKey(apiType: String, id: String): String =
    "${apiType.trim().lowercase()}:${id.trim()}"
