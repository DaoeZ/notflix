package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appOnboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_onboarding",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

@Singleton
class AppOnboardingDataStore internal constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.appOnboardingDataStore)
    private val hasSeenAuthQrOnFirstLaunchKey = booleanPreferencesKey("has_seen_auth_qr_on_first_launch")
    // M58: device-wide, not per profile: the last app version whose notes were shown.
    private val notflixWhatsNewVersionCodeKey = intPreferencesKey("notflix_whats_new_version_code")

    val hasSeenAuthQrOnFirstLaunch: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hasSeenAuthQrOnFirstLaunchKey] ?: false
    }

    /** 0 means this device never stored a version; the caller treats it as a new install. */
    val notflixWhatsNewVersionCode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[notflixWhatsNewVersionCodeKey] ?: 0
    }

    suspend fun setHasSeenAuthQrOnFirstLaunch(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[hasSeenAuthQrOnFirstLaunchKey] = value
        }
    }

    suspend fun setNotflixWhatsNewVersionCode(value: Int) {
        if (value <= 0) return
        dataStore.edit { prefs ->
            // A delayed write (or an older APK) must not lower the device's watermark.
            prefs[notflixWhatsNewVersionCodeKey] = maxOf(prefs[notflixWhatsNewVersionCodeKey] ?: 0, value)
        }
    }
}
