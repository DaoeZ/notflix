package com.nuvio.tv.data.local

import android.content.Context
import android.content.ComponentCallbacks
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.TmdbSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TmdbSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FEATURE = "tmdb_settings"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val localePreferences = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)

    private fun appLocale(configuration: Configuration = context.resources.configuration): java.util.Locale {
        val selected = localePreferences.getString("locale_tag", null)?.takeIf { it.isNotBlank() }
        return selected?.let(java.util.Locale::forLanguageTag)
            ?: configuration.locales[0] ?: java.util.Locale.getDefault()
    }

    // The Activity wraps its context for the app's own language; ApplicationContext keeps the
    // system resources. Observe both the explicit setting and Android configuration changes.
    private val appLocales = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "locale_tag") trySend(appLocale())
        }
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) { trySend(appLocale(newConfig)) }
            override fun onLowMemory() = Unit
        }
        localePreferences.registerOnSharedPreferenceChangeListener(listener)
        context.registerComponentCallbacks(callbacks)
        trySend(appLocale())
        awaitClose {
            localePreferences.unregisterOnSharedPreferenceChangeListener(listener)
            context.unregisterComponentCallbacks(callbacks)
        }
    }.distinctUntilChanged()

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val enabledKey = booleanPreferencesKey("tmdb_enabled")
    private val modernHomeEnabledKey = booleanPreferencesKey("tmdb_modern_home_enabled")
    private val enrichContinueWatchingKey = booleanPreferencesKey("tmdb_enrich_continue_watching")
    private val languageKey = stringPreferencesKey("tmdb_language")
    private val useArtworkKey = booleanPreferencesKey("tmdb_use_artwork")
    private val useBasicInfoKey = booleanPreferencesKey("tmdb_use_basic_info")
    private val useDetailsKey = booleanPreferencesKey("tmdb_use_details")
    private val useCreditsKey = booleanPreferencesKey("tmdb_use_credits")
    private val useProductionsKey = booleanPreferencesKey("tmdb_use_productions")
    private val useNetworksKey = booleanPreferencesKey("tmdb_use_networks")
    private val useEpisodesKey = booleanPreferencesKey("tmdb_use_episodes")
    private val useTrailersKey = booleanPreferencesKey("tmdb_use_trailers")
    private val useMoreLikeThisKey = booleanPreferencesKey("tmdb_use_more_like_this")
    private val useCollectionsKey = booleanPreferencesKey("tmdb_use_collections")

    val settings: StateFlow<TmdbSettings> = profileManager.activeProfileId.flatMapLatest { pid ->
        combine(factory.get(pid, FEATURE).data, appLocales) { prefs, locale ->
            TmdbSettings(
                // Notflix (promoter, 08-11-2026): enrichment on and in the app language, so card descriptions and
                // trailers follow the language the user reads the app in instead of English. TMDB still needs the
                // user key; without it nothing changes.
                enabled = prefs[enabledKey] ?: true,
                modernHomeEnabled = prefs[modernHomeEnabledKey] ?: false,
                enrichContinueWatching = prefs[enrichContinueWatchingKey] ?: true,
                language = prefs[languageKey] ?: defaultTmdbLanguage(locale),
                useArtwork = prefs[useArtworkKey] ?: true,
                useBasicInfo = prefs[useBasicInfoKey] ?: true,
                useDetails = prefs[useDetailsKey] ?: true,
                useCredits = prefs[useCreditsKey] ?: true,
                useProductions = prefs[useProductionsKey] ?: true,
                useNetworks = prefs[useNetworksKey] ?: true,
                useEpisodes = prefs[useEpisodesKey] ?: true,
                useTrailers = prefs[useTrailersKey] ?: true,
                useMoreLikeThis = prefs[useMoreLikeThisKey] ?: true,
                useCollections = prefs[useCollectionsKey] ?: true
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, TmdbSettings())

    suspend fun setEnabled(enabled: Boolean) {
        store().edit { it[enabledKey] = enabled }
    }

    suspend fun setModernHomeEnabled(enabled: Boolean) {
        store().edit { it[modernHomeEnabledKey] = enabled }
    }

    suspend fun setEnrichContinueWatching(enabled: Boolean) {
        store().edit { it[enrichContinueWatchingKey] = enabled }
    }

    suspend fun setLanguage(language: String) {
        store().edit { it[languageKey] = language.ifBlank { "en" } }
    }

    suspend fun setUseArtwork(enabled: Boolean) {
        store().edit { it[useArtworkKey] = enabled }
    }

    suspend fun setUseBasicInfo(enabled: Boolean) {
        store().edit { it[useBasicInfoKey] = enabled }
    }

    suspend fun setUseDetails(enabled: Boolean) {
        store().edit { it[useDetailsKey] = enabled }
    }

    suspend fun setUseCredits(enabled: Boolean) {
        store().edit { it[useCreditsKey] = enabled }
    }

    suspend fun setUseProductions(enabled: Boolean) {
        store().edit { it[useProductionsKey] = enabled }
    }

    suspend fun setUseNetworks(enabled: Boolean) {
        store().edit { it[useNetworksKey] = enabled }
    }

    suspend fun setUseEpisodes(enabled: Boolean) {
        store().edit { it[useEpisodesKey] = enabled }
    }

    suspend fun setUseTrailers(enabled: Boolean) {
        store().edit { it[useTrailersKey] = enabled }
    }

    suspend fun setUseMoreLikeThis(enabled: Boolean) {
        store().edit { it[useMoreLikeThisKey] = enabled }
    }

    suspend fun setUseCollections(enabled: Boolean) {
        store().edit { it[useCollectionsKey] = enabled }
    }
}

/**
 * The TMDB language for the app's language (M61).
 *
 * The app ships one `values-*` folder per language, and every language it ships has a TMDB
 * counterpart, so the code the user reads the app in is the one TMDB must answer in. Two things
 * need translating on the way: Android's legacy codes for the folders it names `values-iw` and
 * `values-in`, and the languages the app splits by region, where TMDB keeps them apart too
 * (Portuguese of Brazil and Portugal, simplified and traditional Chinese, Latin American Spanish).
 */
internal fun defaultTmdbLanguage(locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val language = when (val raw = locale.language.lowercase(java.util.Locale.US)) {
        "iw" -> "he"
        "in" -> "id"
        "ji" -> "yi"
        else -> raw
    }
    val region = locale.country.uppercase(java.util.Locale.US)
    val candidates = buildList {
        when (language) {
            "pt" -> add(if (region == "BR") "pt-br" else "pt")
            "zh" -> add(when {
                locale.script.equals("Hant", ignoreCase = true) -> "zh-TW"
                locale.script.equals("Hans", ignoreCase = true) -> "zh-CN"
                region == "TW" || region == "HK" || region == "MO" -> "zh-TW"
                else -> "zh-CN"
            })
            "es" -> if (region == "419") add("es-419") else Unit
            else -> Unit
        }
        add(language)
    }
    return candidates.firstNotNullOfOrNull { candidate ->
        AVAILABLE_TMDB_LANGUAGES.firstOrNull { it.code.equals(candidate, ignoreCase = true) }?.code
    } ?: "en"
}
