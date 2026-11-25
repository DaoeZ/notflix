package com.nuvio.tv.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HiddenTitleMetadata(
    @field:com.google.gson.annotations.SerializedName("title") val title: String? = null,
    @field:com.google.gson.annotations.SerializedName("poster") val poster: String? = null
)

/** Títulos ocultos por perfil, solo en el dispositivo. */
interface HiddenTitlesRepository {
    fun observe(profileId: Int): Flow<Set<String>>
    suspend fun hide(profileId: Int, key: String)
    fun observeMetadata(profileId: Int): Flow<Map<String, HiddenTitleMetadata>> =
        observe(profileId).map { keys -> keys.associateWith { HiddenTitleMetadata() } }
    suspend fun hide(profileId: Int, key: String, title: String?, poster: String?) = hide(profileId, key)
    suspend fun show(profileId: Int, key: String)
}
