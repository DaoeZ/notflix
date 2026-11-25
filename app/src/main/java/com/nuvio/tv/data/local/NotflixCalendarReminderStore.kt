package com.nuvio.tv.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight profile-scoped store for Calendar "Avísame" release reminders on TV.
 * Uses SharedPreferences so reads are cached in memory with zero DataStore startup cost.
 */
@Singleton
class NotflixCalendarReminderStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("notflix_calendar_reminders", Context.MODE_PRIVATE)
    }

    private val _remindedKeys = MutableStateFlow<Set<String>>(emptySet())
    val remindedKeys: StateFlow<Set<String>> = _remindedKeys.asStateFlow()

    @Volatile
    private var activeProfileId: Int = -1

    fun loadForProfile(profileId: Int): Set<String> {
        if (activeProfileId == profileId && _remindedKeys.value.isNotEmpty()) {
            return _remindedKeys.value
        }
        activeProfileId = profileId
        val loaded = prefs.getStringSet(prefKey(profileId), emptySet())?.toSet().orEmpty()
        _remindedKeys.value = loaded
        return loaded
    }

    fun toggle(profileId: Int, key: String): Boolean {
        if (activeProfileId != profileId) loadForProfile(profileId)
        val current = _remindedKeys.value
        val added = key !in current
        val updated = if (added) current + key else current - key
        _remindedKeys.value = updated
        prefs.edit().putStringSet(prefKey(profileId), updated).apply()
        return added
    }

    fun clearProfile(profileId: Int) {
        prefs.edit().remove(prefKey(profileId)).apply()
        if (activeProfileId == profileId) _remindedKeys.value = emptySet()
    }

    private fun prefKey(profileId: Int): String = "reminders_p$profileId"

    companion object {
        fun keyOf(contentId: String, season: Int?, episode: Int?): String =
            "${contentId.trim().lowercase()}:S${season ?: 0}E${episode ?: 0}"
    }
}
