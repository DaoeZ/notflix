package com.nuvio.tv.domain.repository

import kotlinx.coroutines.flow.Flow

/** Títulos ocultos por perfil, solo en el dispositivo. */
interface HiddenTitlesRepository {
    fun observe(profileId: Int): Flow<Set<String>>
    suspend fun hide(profileId: Int, key: String)
    suspend fun show(profileId: Int, key: String)
}
