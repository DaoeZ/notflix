package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.domain.repository.HiddenTitlesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Clave estable de un título oculto: "apiType:id". */
fun hiddenTitleKey(apiType: String, id: String): String =
    "${apiType.trim().lowercase()}:${id.trim()}"

/**
 * Títulos ocultos por perfil y solo en el dispositivo. No se sincroniza con la cuenta.
 * Guarda claves "apiType:id"; el perfil se toma de ProfileDataStoreFactory.
 */
@Singleton
class HiddenTitlesStore @Inject constructor(
    private val factory: ProfileDataStoreFactory
) : HiddenTitlesRepository {
    companion object {
        const val FEATURE = "hidden_titles"
        private val KEY = stringSetPreferencesKey("hidden_keys")
    }

    override fun observe(profileId: Int): Flow<Set<String>> =
        factory.get(profileId, FEATURE).data.map { prefs -> prefs[KEY] ?: emptySet() }

    override suspend fun hide(profileId: Int, key: String) {
        factory.get(profileId, FEATURE).edit { prefs ->
            prefs[KEY] = (prefs[KEY] ?: emptySet()) + key
        }
    }

    override suspend fun show(profileId: Int, key: String) {
        factory.get(profileId, FEATURE).edit { prefs ->
            prefs[KEY] = (prefs[KEY] ?: emptySet()) - key
        }
    }
}
