package com.nuvio.tv.data.local

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.AgeLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Límite de edad por perfil, solo en el dispositivo. No toca UserProfile ni la sincronización.
 * Un valor guardado corrupto o desconocido NUNCA se lee como «sin límite»: se aplica TP.
 */
@Singleton
class ProfileAgeLimitStore @Inject constructor(
    private val factory: ProfileDataStoreFactory
) {
    companion object {
        const val FEATURE = "profile_age_limit"
        private val KEY = stringPreferencesKey("level")
    }

    fun observe(profileId: Int): Flow<AgeLevel?> = factory.get(profileId, FEATURE).data.map { prefs ->
        val raw = prefs[KEY] ?: return@map null
        AgeLevel.entries.firstOrNull { it.name == raw } ?: run {
            Log.w("ProfileAgeLimitStore", "Unknown stored age limit for profile $profileId; applying TP")
            AgeLevel.TP
        }
    }

    suspend fun set(profileId: Int, level: AgeLevel?) {
        factory.get(profileId, FEATURE).edit { prefs ->
            if (level == null) prefs.remove(KEY) else prefs[KEY] = level.name
        }
    }
}
