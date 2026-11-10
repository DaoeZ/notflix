package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.AgeRatingCache
import com.nuvio.tv.core.profile.CachedAgeRating
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.ageRatingCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "age_rating_cache"
)

/**
 * Caché persistente de clasificaciones resueltas por TMDB. No es datos de cuenta:
 * se puede perder y solo provoca una nueva consulta.
 */
@Singleton
class AgeRatingCacheStore @Inject constructor(
    @ApplicationContext context: Context
) : AgeRatingCache {
    private val store = context.ageRatingCacheDataStore

    override suspend fun all(nowMs: Long): Map<String, CachedAgeRating> {
        val prefs = store.data.first()
        return buildMap {
            prefs.asMap().forEach { (preferenceKey, value) ->
                val name = preferenceKey.name
                if (!name.startsWith("r:")) return@forEach
                val ratingKey = name.removePrefix("r:")
                val raw = value as? String ?: return@forEach
                val storedAt = prefs[longPreferencesKey("t:$ratingKey")] ?: return@forEach
                put(ratingKey, CachedAgeRating(parse(raw), storedAt))
            }
        }
    }

    override suspend fun put(key: String, level: AgeLevel?, nowMs: Long) {
        store.edit { prefs ->
            prefs[stringPreferencesKey("r:$key")] = level?.name ?: NONE
            prefs[longPreferencesKey("t:$key")] = nowMs
        }
    }

    private fun parse(raw: String): AgeLevel? =
        if (raw == NONE) null else AgeLevel.entries.firstOrNull { it.name == raw }

    private companion object {
        const val NONE = "NONE"
    }
}
