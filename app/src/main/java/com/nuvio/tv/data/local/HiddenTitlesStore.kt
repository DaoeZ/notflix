package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.domain.repository.HiddenTitlesRepository
import com.nuvio.tv.domain.repository.HiddenTitleMetadata
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/** Clave estable de un título oculto: "apiType:id". */
fun hiddenTitleKey(apiType: String, id: String): String =
    "${apiType.trim().lowercase()}:${id.trim()}"

/**
 * Títulos ocultos por perfil. Viajan con la cuenta en la copia notflix_tv (NotflixSettingsSyncService).
 * Guarda claves "apiType:id"; el perfil se toma de ProfileDataStoreFactory.
 */
@Singleton
class HiddenTitlesStore @Inject constructor(
    private val factory: ProfileDataStoreFactory
) : HiddenTitlesRepository {
    companion object {
        const val FEATURE = "hidden_titles"
        private val KEY = stringSetPreferencesKey("hidden_keys")
        private val METADATA = stringPreferencesKey("hidden_metadata")
        /** S3: last hide/show per title, so two devices merge instead of overwriting ("key" -> change). */
        internal val CHANGES = stringPreferencesKey("hidden_changes")
        private val metadataType = object : TypeToken<Map<String, HiddenTitleMetadata>>() {}.type
        private val gson = Gson()
        internal fun decodeMetadata(raw: String?): Map<String, HiddenTitleMetadata> =
            if (raw.isNullOrBlank()) emptyMap() else runCatching {
                gson.fromJson<Map<String, HiddenTitleMetadata>?>(raw, metadataType).orEmpty()
            }.getOrDefault(emptyMap())
    }

    override fun observe(profileId: Int): Flow<Set<String>> =
        factory.get(profileId, FEATURE).data.map { prefs -> prefs[KEY] ?: emptySet() }

    override suspend fun hide(profileId: Int, key: String) {
        hide(profileId, key, null, null)
    }

    override fun observeMetadata(profileId: Int): Flow<Map<String, HiddenTitleMetadata>> =
        factory.get(profileId, FEATURE).data.map { prefs ->
            val details = decodeMetadata(prefs[METADATA])
            (prefs[KEY] ?: emptySet()).associateWith { details[it] ?: HiddenTitleMetadata() }
        }.flowOn(Dispatchers.Default)

    override suspend fun hide(profileId: Int, key: String, title: String?, poster: String?) {
        factory.get(profileId, FEATURE).edit { prefs ->
            prefs[KEY] = (prefs[KEY] ?: emptySet()) + key
            val previous = decodeMetadata(prefs[METADATA])
            val metadata = HiddenTitleMetadata(title?.takeIf { it.isNotBlank() } ?: previous[key]?.title,
                poster?.takeIf { it.isNotBlank() } ?: previous[key]?.poster)
            prefs[METADATA] = gson.toJson(previous + (key to metadata))
            prefs[CHANGES] = HiddenTitleChanges.record(prefs[CHANGES], key, hidden = true)
        }
    }

    override suspend fun show(profileId: Int, key: String) {
        factory.get(profileId, FEATURE).edit { prefs ->
            prefs[KEY] = (prefs[KEY] ?: emptySet()) - key
            prefs[METADATA] = gson.toJson(decodeMetadata(prefs[METADATA]) - key)
            prefs[CHANGES] = HiddenTitleChanges.record(prefs[CHANGES], key, hidden = false)
        }
    }
}
