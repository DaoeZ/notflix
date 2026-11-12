package com.nuvio.tv.data.local

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Optional third-party credentials. Empty means the provider is off. */
@Serializable
data class NotflixIntegrationCredentials(
    val tmdbApiKey: String = "",
    val introDbBaseUrl: String = "",
    val traktClientId: String = "",
    val traktClientSecret: String = ""
) {
    fun normalized(): NotflixIntegrationCredentials = copy(
        tmdbApiKey = tmdbApiKey.trim(),
        introDbBaseUrl = normalizeNotflixProviderUrl(introDbBaseUrl),
        traktClientId = traktClientId.trim(),
        traktClientSecret = traktClientSecret.trim()
    )
}

/** Snapshot source so callers and tests never depend on Android storage. */
fun interface NotflixIntegrationValuesSource {
    fun read(): NotflixIntegrationCredentials
}

/**
 * Device-local secrets. Reuses the project's Android Keystore AES/GCM store
 * (`ProtectedProfilePreferences`), same mechanism as the protected profile credentials.
 */
@Singleton
class NotflixIntegrationStore @Inject constructor(
    @ApplicationContext context: Context
) : NotflixIntegrationValuesSource {
    private val preferences = ProtectedProfilePreferences(context, PREFERENCES_NAME, KEY_ALIAS)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val mutableValues = MutableStateFlow(NotflixIntegrationCredentials())
    val values: StateFlow<NotflixIntegrationCredentials> = mutableValues.asStateFlow()

    @Volatile
    private var snapshot: NotflixIntegrationCredentials? = null

    override fun read(): NotflixIntegrationCredentials {
        snapshot?.let { return it }
        synchronized(lock) {
            snapshot?.let { return it }
            val stored = try {
                preferences.read(DEVICE_PROFILE_ID, removeUnreadable = false)
            } catch (e: IOException) {
                // Transient storage or Keystore failure: keep the saved value and retry on the next read.
                Log.w(TAG, "Integration credentials temporarily unavailable", e)
                return NotflixIntegrationCredentials()
            }
            val loaded = stored?.let { raw ->
                runCatching { json.decodeFromString<NotflixIntegrationCredentials>(raw) }
                    .getOrElse {
                        Log.w(TAG, "Discarding unreadable integration credentials")
                        null
                    }
            } ?: NotflixIntegrationCredentials()
            snapshot = loaded
            mutableValues.value = loaded
            return loaded
        }
    }

    suspend fun save(credentials: NotflixIntegrationCredentials) = withContext(Dispatchers.IO) {
        val normalized = credentials.normalized()
        synchronized(lock) {
            preferences.write(DEVICE_PROFILE_ID, json.encodeToString(normalized))
            snapshot = normalized
            mutableValues.value = normalized
        }
    }

    private companion object {
        const val TAG = "NotflixIntegrationStore"
        const val PREFERENCES_NAME = "nuvio_notflix_integrations"
        const val KEY_ALIAS = "com.nuvio.tv.notflix.integrations.v1"
        // Device-level, not tied to a viewing profile; 0 is never a real profile id.
        const val DEVICE_PROFILE_ID = 0
    }
}

/**
 * Injected read side. It is called per use, never captured in BuildConfig or a static val.
 */
class NotflixIntegrationConfig(
    /** Notflix's own Trakt app from the build; used only when the user has not typed a Client ID. */
    private val builtInTraktClientId: String = com.nuvio.tv.BuildConfig.TRAKT_CLIENT_ID,
    private val builtInTraktClientSecret: String = com.nuvio.tv.BuildConfig.TRAKT_CLIENT_SECRET,
    // Last, so callers keep writing NotflixIntegrationConfig { credentials }.
    private val source: NotflixIntegrationValuesSource
) {
    fun tmdbApiKey(): String = source.read().tmdbApiKey.trim()

    fun isTmdbEnabled(): Boolean = tmdbApiKey().isNotEmpty()

    /** Normalized http(s) base URL without trailing slash, or empty when invalid/absent. */
    /** Empty means the public IntroDB service (anonymous reads, no key); a typed address replaces it. */
    fun introDbBaseUrl(): String {
        val stored = source.read().introDbBaseUrl
        return if (stored.isBlank()) DEFAULT_INTRODB_BASE_URL else normalizeNotflixProviderUrl(stored)
    }

    fun isIntroDbEnabled(): Boolean = introDbBaseUrl().isNotEmpty()

    fun traktClientId(): String =
        source.read().traktClientId.trim().ifEmpty { builtInTraktClientId.trim() }

    /** The secret always belongs to the same app as the Client ID: never the user's ID with the built-in secret. */
    fun traktClientSecret(): String {
        val user = source.read()
        return if (user.traktClientId.isNotBlank()) user.traktClientSecret.trim() else builtInTraktClientSecret.trim()
    }

    fun isTraktConfigured(): Boolean =
        traktClientId().isNotEmpty()

    /**
     * Client ID of the app that issued the active session. Tokens only work with their own app, so a
     * session made with an older key (development app, or the user's own) keeps using it after the
     * build key changes; only new connections take the current one.
     */
    @Volatile
    private var traktSessionClientId: String? = null

    fun setTraktSessionClientId(clientId: String?) {
        traktSessionClientId = clientId?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Client ID for a session issued by [clientId] (blank = current key), and the secret that belongs to it. */
    fun traktClientIdFor(clientId: String?): String = clientId?.trim()?.takeIf { it.isNotEmpty() } ?: traktClientId()

    fun traktClientSecretFor(clientId: String?): String =
        if (traktClientIdFor(clientId) == traktClientId()) traktClientSecret() else ""

    /** Value of the trakt-api-key header: the active session's app, or the current key. */
    fun traktApiClientId(): String = traktClientIdFor(traktSessionClientId)
}

internal fun normalizeNotflixProviderUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val parsed = withScheme.toHttpUrlOrNull() ?: return ""
    if (parsed.scheme != "http" && parsed.scheme != "https") return ""
    return parsed.toString().trimEnd('/')
}

const val DEFAULT_INTRODB_BASE_URL = "https://api.introdb.app"
