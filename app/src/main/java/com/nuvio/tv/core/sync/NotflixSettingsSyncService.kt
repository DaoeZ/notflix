package com.nuvio.tv.core.sync

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.HiddenTitleChanges
import com.nuvio.tv.data.local.HiddenTitlesStore
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.NotflixIntegrationStore
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.data.local.ProfileAgeLimitStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.data.remote.supabase.SupabaseProfileSettingsBlob
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotflixSettingsSync"
private const val NOTFLIX_SYNC_PLATFORM = "notflix_tv"
private const val PUSH_DEBOUNCE_MS = 1500L

internal fun selectorAgeLimit(incoming: JsonObject): AgeLevel? {
    if ("level" !in incoming) return null
    val encoded = incoming["level"] as? JsonObject
    val type = (encoded?.get("type") as? JsonPrimitive)?.contentOrNull
    val value = (encoded?.get("value") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    return AgeLevel.entries.firstOrNull { type == "string" && it.name == value } ?: AgeLevel.TP
}

/**
 * Notflix's own per-profile settings travel with the Nuvio account in a separate settings blob
 * (p_platform = "notflix_tv"). S1 (15-11-2026) showed the server stores it apart from the "tv" blob,
 * so an official NuvioTV on the same account never reads or overwrites it. Same wire format as
 * ProfileSettingsSyncService. Hidden titles merge by date (S3); keys and the Trakt session keep
 * the newest (S4); the rest: last writer wins. Contract for the mobile app:
 * docs/revisiones/CL-20261116-contrato-sync-notflix.md
 */
@Singleton
class NotflixSettingsSyncService @Inject constructor(
    private val authManager: AuthManager,
    private val postgrest: Postgrest,
    private val profileManager: ProfileManager,
    private val factory: ProfileDataStoreFactory,
    private val syncClientIdentity: SyncClientIdentity,
    private val integrationStore: NotflixIntegrationStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Last state known to match the server, per profile; a local change equal to it is not pushed. */
    private val syncedSignatures = java.util.concurrent.ConcurrentHashMap<Int, String>()

    init {
        observeLocalChanges()
    }

    /** Profile cards need the age preset before the user activates that profile. This only reads it. */
    suspend fun pullSelectorAgeLimit(profileId: Int): Unit = withContext(Dispatchers.IO) {
        val account = authManager.currentUserId ?: return@withContext
        fun ownsProfile() = authManager.currentUserId == account &&
            profileManager.activeProfileId.value != profileId &&
            profileManager.profiles.value.any { it.id == profileId }
        if (!ownsProfile()) return@withContext

        val feature = ProfileAgeLimitStore.FEATURE
        val key = stringPreferencesKey("level")
        try {
            val before = read(profileId, feature)?.get(key)
            val incoming = withTimeoutOrNull(3_000L) {
                pullRemoteFeatures(profileId)?.get(feature) as? JsonObject
            } ?: return@withContext
            val level = selectorAgeLimit(incoming)
            mutex.withLock {
                if (ownsProfile()) edit(profileId, feature) { prefs ->
                    if (!ownsProfile() || prefs[key] != before) return@edit
                    if (level == null) prefs.remove(key) else prefs[key] = level.name
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w(TAG, "Could not load the age limit for the profile selector")
        }
    }

    private suspend fun pullRemoteFeatures(profileId: Int): JsonObject? =
        postgrest.rpc("sync_pull_profile_settings_blob", buildJsonObject {
            put("p_profile_id", profileId)
            put("p_platform", NOTFLIX_SYNC_PLATFORM)
        }).decodeList<SupabaseProfileSettingsBlob>().firstOrNull()?.settingsJson
            ?.get("features")?.jsonObject

    suspend fun pullCurrentProfileFromRemote(): Result<Boolean> = withContext(Dispatchers.IO) {
        val profileId = profileManager.activeProfileId.value
        mutex.withLock {
            runCatching {
                val remote = pullRemoteFeatures(profileId)
                if (remote == null) {
                    // First Notflix device on this account: seed the server with what is here.
                    push(profileId)
                    return@runCatching false
                }
                if (signature(remote) == signature(exportFeatures(profileId))) {
                    syncedSignatures[profileId] = signature(remote)
                    return@runCatching false
                }
                importFeatures(profileId, remote)
                val merged = exportFeatures(profileId)
                // Hidden titles merge; if this TV added changes the server lacks, send them back.
                if (signature(merged) != signature(remote)) push(profileId) else syncedSignatures[profileId] = signature(merged)
                true
            }.onFailure { Log.e(TAG, "Pull failed for profile $profileId", it) }
        }
    }

    private suspend fun push(profileId: Int) {
        val features = exportFeatures(profileId)
        postgrest.rpc("sync_push_profile_settings_blob", buildJsonObject {
            put("p_profile_id", profileId)
            put("p_settings_json", buildJsonObject {
                put("version", 1)
                put("features", features)
            })
            put("p_platform", NOTFLIX_SYNC_PLATFORM)
            put("p_origin_client_id", syncClientIdentity.currentClientId())
        })
        syncedSignatures[profileId] = signature(features)
        Log.d(TAG, "Pushed Notflix settings for profile $profileId")
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeLocalChanges() {
        scope.launch {
            profileManager.activeProfileId
                .flatMapLatest { profileId ->
                    integrationStore.read() // fills integrationStore.values
                    combine(FEATURES.map { observeFeature(profileId, it) } + integrationStore.values) { profileId }
                        .map { profileId to signature(exportFeatures(profileId)) }
                        .distinctUntilChanged()
                        .drop(1) // the stored state on open or profile switch is not a local edit
                }
                .debounce(PUSH_DEBOUNCE_MS)
                .collect { (profileId, current) ->
                    if (!authManager.isAuthenticated || syncedSignatures[profileId] == current) return@collect
                    mutex.withLock {
                        runCatching { push(profileId) }.onFailure { Log.e(TAG, "Push failed for profile $profileId", it) }
                    }
                }
        }
    }

    private fun observeFeature(profileId: Int, feature: String): Flow<Any?> =
        runCatching { factory.get(profileId, feature).data }.getOrElse { flowOf(null) }

    internal suspend fun exportFeatures(profileId: Int): JsonObject = buildJsonObject {
        FEATURES.forEach { feature ->
            val prefs = read(profileId, feature) ?: return@forEach
            put(feature, buildJsonObject {
                prefs.asMap().forEach { (key, value) ->
                    if (key.name in localOnlyKeys[feature].orEmpty()) return@forEach
                    if (!travels(feature, key.name)) return@forEach
                    encode(value)?.let { put(key.name, it) }
                }
            })
        }
        val keys = integrationStore.read()
        put(KEYS_FEATURE, buildJsonObject {
            put("tmdb_api_key", encode(keys.tmdbApiKey)!!)
            put("intro_db_base_url", encode(keys.introDbBaseUrl)!!)
            put("trakt_client_id", encode(keys.traktClientId)!!)
            put("trakt_client_secret", encode(keys.traktClientSecret)!!)
            put("updated_at", encode(keys.updatedAt)!!)
        })
    }

    internal suspend fun importFeatures(profileId: Int, features: JsonObject) {
        FEATURES.forEach { feature ->
            val incoming = features[feature] as? JsonObject ?: return@forEach
            if (feature == HiddenTitlesStore.FEATURE) {
                edit(profileId, feature) { mergeHiddenTitles(it, incoming) }
                return@forEach
            }
            if (feature == TRAKT_FEATURE) {
                edit(profileId, feature) { mergeTraktSession(it, incoming) }
                return@forEach
            }
            if (feature in partialFeatures) {
                edit(profileId, feature) { prefs ->
                    prefs.asMap().keys.filter { travels(feature, it.name) }.forEach { prefs.remove(it) }
                    incoming.forEach { (name, value) -> if (travels(feature, name)) decodeInto(prefs, name, value) }
                }
                return@forEach
            }
            edit(profileId, feature) { prefs ->
                val kept = prefs.asMap().filterKeys { it.name in localOnlyKeys[feature].orEmpty() }
                prefs.clear()
                incoming.forEach { (name, value) ->
                    if (name !in localOnlyKeys[feature].orEmpty()) decodeInto(prefs, name, value)
                }
                @Suppress("UNCHECKED_CAST")
                kept.forEach { (key, value) -> prefs[key as Preferences.Key<Any>] = value }
            }
        }
        importKeys(features[KEYS_FEATURE] as? JsonObject)
    }

    /** Device keys: the newest save on any device wins (an older or empty copy never overwrites). */
    private suspend fun importKeys(incoming: JsonObject?) {
        incoming ?: return
        val remote = emptyPreferences().toMutablePreferences()
        incoming.forEach { (name, value) -> decodeInto(remote, name, value) }
        val remoteUpdatedAt = remote[longPreferencesKey("updated_at")] ?: return
        if (remoteUpdatedAt <= integrationStore.read().updatedAt) return
        integrationStore.save(NotflixIntegrationCredentials(
            tmdbApiKey = remote[stringPreferencesKey("tmdb_api_key")].orEmpty(),
            introDbBaseUrl = remote[stringPreferencesKey("intro_db_base_url")].orEmpty(),
            traktClientId = remote[stringPreferencesKey("trakt_client_id")].orEmpty(),
            traktClientSecret = remote[stringPreferencesKey("trakt_client_secret")].orEmpty(),
        ), remoteUpdatedAt)
    }

    /**
     * Trakt session: the newer token (created_at) wins, so a token Trakt renewed on one TV reaches
     * the others. A copy without a token never signs this TV out.
     * shortcut: disconnecting Trakt on one TV does not disconnect the others; add a dated
     * "signed out" mark if the promoter wants that.
     */
    private fun mergeTraktSession(prefs: MutablePreferences, incoming: JsonObject) {
        val remote = emptyPreferences().toMutablePreferences()
        incoming.forEach { (name, value) -> decodeInto(remote, name, value) }
        val createdAt = longPreferencesKey("created_at")
        if (remote[stringPreferencesKey("access_token")].isNullOrBlank()) return
        if ((remote[createdAt] ?: 0L) <= (prefs[createdAt] ?: -1L)) return
        val localOnly = localOnlyKeys[TRAKT_FEATURE].orEmpty()
        val kept = prefs.asMap().filterKeys { it.name in localOnly }
        prefs.clear()
        @Suppress("UNCHECKED_CAST")
        remote.asMap().forEach { (key, value) -> if (key.name !in localOnly) prefs[key as Preferences.Key<Any>] = value }
        @Suppress("UNCHECKED_CAST")
        kept.forEach { (key, value) -> prefs[key as Preferences.Key<Any>] = value }
    }

    // Appearance files are guarded by the factory's appearance context; a retired profile is skipped.
    private suspend fun read(profileId: Int, feature: String): Preferences? =
        if (feature != NotflixAppearanceStore.FEATURE) factory.get(profileId, feature).data.first()
        else factory.appearanceContext(profileId)?.let { ctx ->
            runCatching { factory.withAppearanceContext(ctx) { factory.get(profileId, feature).data.first() } }.getOrNull()
        }

    private suspend fun edit(profileId: Int, feature: String, block: (MutablePreferences) -> Unit) {
        if (feature != NotflixAppearanceStore.FEATURE) {
            factory.get(profileId, feature).edit(block); return
        }
        val ctx = factory.appearanceContext(profileId) ?: return
        factory.withAppearanceContext(ctx) { factory.get(profileId, feature).edit(block) }
    }

    private fun mergeHiddenTitles(prefs: MutablePreferences, incoming: JsonObject) {
        val remote = emptyPreferences().toMutablePreferences()
        incoming.forEach { (name, value) -> decodeInto(remote, name, value) }
        val keys = stringSetPreferencesKey("hidden_keys")
        val metadata = stringPreferencesKey("hidden_metadata")
        val merged = HiddenTitleChanges.merge(
            prefs[keys].orEmpty(), prefs[metadata], prefs[HiddenTitlesStore.CHANGES],
            remote[keys].orEmpty(), remote[metadata], remote[HiddenTitlesStore.CHANGES],
        )
        prefs[keys] = merged.keys
        prefs[metadata] = merged.metadataJson
        prefs[HiddenTitlesStore.CHANGES] = merged.changesJson
    }

    internal companion object {
        const val TRAKT_FEATURE = "trakt_auth_store"
        /** Device-level keys from NotflixIntegrationStore; not a profile DataStore. */
        const val KEYS_FEATURE = "notflix_keys"
        const val PLAYER_FEATURE = "player_settings"
        val FEATURES = listOf(HiddenTitlesStore.FEATURE, ProfileAgeLimitStore.FEATURE, NotflixAppearanceStore.FEATURE, TRAKT_FEATURE, PLAYER_FEATURE)

        /**
         * Stores shared with Nuvio's own "tv" blob: only these keys travel in notflix_tv, so the phone
         * (which never reads the "tv" blob) plays the same source in the same order as the TV
         * (promoter, 16-11). Audio, HDR and the engine stay out: they depend on the device.
         */
        val partialFeatures = mapOf(PLAYER_FEATURE to listOf(
            "stream_auto_play_", "stream_reuse_last_link_", "notflix_player_buttons",
            "next_episode_threshold_", "still_watching_", "post_play_", "preload_next_episode_sources",
            // Languages are the person's, not the device's; Nuvio keeps TV and phone apart.
            "preferred_audio_language", "secondary_preferred_audio_language",
            "subtitle_preferred_language", "subtitle_secondary_language", "subtitle_show_only_preferred_languages",
        ))

        fun travels(feature: String, key: String): Boolean =
            partialFeatures[feature]?.any { key.startsWith(it) } ?: true
        private val SIGNED_FEATURES = FEATURES + KEYS_FEATURE

        /**
         * Stays on this TV: motion depends on the TV's power, and the intro, preview and Wikipedia
         * options promise on screen "Only this profile and device".
         */
        val localOnlyKeys = mapOf(NotflixAppearanceStore.FEATURE to setOf(
            NotflixAppearanceStore.REDUCE_HOME_MOTION,
            NotflixAppearanceStore.INTRO_ENABLED, NotflixAppearanceStore.INTRO_SOUND_ENABLED,
            NotflixAppearanceStore.PREVIEW_ENABLED, NotflixAppearanceStore.PREVIEW_SOUND_ENABLED,
            NotflixAppearanceStore.WIKIPEDIA_ENABLED,
        ), TRAKT_FEATURE to setOf(
            // An unfinished sign-in on this TV (device code flow) is not a session.
            "device_code", "user_code", "verification_url", "expires_at", "poll_interval", "device_client_id",
        ))

        fun signature(features: JsonObject): String =
            SIGNED_FEATURES.joinToString("||") { f ->
                (features[f] as? JsonObject)?.entries.orEmpty().sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
            }

        fun encode(value: Any?): JsonObject? = when (value) {
            is String -> typed("string", JsonPrimitive(value))
            is Boolean -> typed("boolean", JsonPrimitive(value))
            is Int -> typed("int", JsonPrimitive(value))
            is Long -> typed("long", JsonPrimitive(value))
            is Float -> typed("float", JsonPrimitive(value))
            is Set<*> -> if (value.all { it is String }) typed("string_set", JsonArray(value.map { JsonPrimitive(it as String) }.sortedBy { it.content })) else null
            else -> null
        }

        private fun typed(type: String, value: JsonElement) = buildJsonObject { put("type", type); put("value", value) }

        fun decodeInto(prefs: MutablePreferences, name: String, encoded: JsonElement) {
            val obj = encoded as? JsonObject ?: return
            val value = obj["value"] ?: return
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "string" -> value.jsonPrimitive.contentOrNull?.let { prefs[stringPreferencesKey(name)] = it }
                "boolean" -> value.jsonPrimitive.contentOrNull?.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it }
                "int" -> value.jsonPrimitive.intOrNull?.let { prefs[intPreferencesKey(name)] = it }
                "long" -> value.jsonPrimitive.longOrNull?.let { prefs[longPreferencesKey(name)] = it }
                "float" -> value.jsonPrimitive.floatOrNull?.let { prefs[floatPreferencesKey(name)] = it }
                "string_set" -> runCatching { value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet() }
                    .getOrNull()?.let { prefs[stringSetPreferencesKey(name)] = it }
            }
        }
    }
}
