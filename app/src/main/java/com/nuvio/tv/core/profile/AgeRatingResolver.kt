package com.nuvio.tv.core.profile

import android.util.Log
import com.nuvio.tv.domain.model.MetaPreview
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de una consulta externa. Solo se cachea cuando la ausencia de nota es una respuesta. */
data class AgeRatingLookup(val level: AgeLevel?, val cacheable: Boolean)

/** Fuente externa (TMDB) inyectable para poder probar el resolver sin red. */
interface TmdbAgeRatingSource {
    suspend fun lookup(apiType: String, id: String, fallbackImdbId: String?): AgeRatingLookup
}

fun interface TmdbAvailability {
    fun isEnabled(): Boolean
}

data class CachedAgeRating(val level: AgeLevel?, val storedAtMs: Long)

/** Caché persistente de clasificaciones; `all` devuelve las entradas guardadas con su fecha. */
interface AgeRatingCache {
    suspend fun all(nowMs: Long): Map<String, CachedAgeRating>
    suspend fun put(key: String, level: AgeLevel?, nowMs: Long)
}

/**
 * Resuelve la clasificación que el catálogo no trae. Nunca se llama sin límite activo
 * (lo garantiza ActiveProfileAgeGate). Máximo 4 peticiones simultáneas; caché de 30 días.
 */
@Singleton
class AgeRatingResolver @Inject constructor(
    private val source: TmdbAgeRatingSource,
    private val cache: AgeRatingCache,
    private val availability: TmdbAvailability
) {
    companion object {
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val TAG = "AgeRatingResolver"
    }

    private val _ratings = MutableStateFlow<Map<String, AgeLevel?>>(emptyMap())
    val ratings: StateFlow<Map<String, AgeLevel?>> = _ratings.asStateFlow()

    private val semaphore = Semaphore(4)
    private val loadMutex = Mutex()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<AgeLevel?>>()

    @Volatile private var cacheLoaded = false

    fun keyFor(item: MetaPreview): String = keyFor(item.apiType, item.id)

    fun keyFor(apiType: String, id: String): String = ageRatingKey(apiType, id)

    suspend fun resolve(item: MetaPreview): AgeLevel? {
        ageLevelOf(item.ageRating)?.let { return it }
        return resolve(keyFor(item)) {
            source.lookup(item.apiType, item.id, item.imdbId)
        }
    }

    /**
     * Variante segura para la puerta: sin límite no se resuelve nada ni se hace ninguna petición.
     */
    suspend fun resolveForLimit(item: MetaPreview, limit: AgeLevel?): AgeLevel? {
        if (limit == null) return null
        return resolve(item)
    }

    suspend fun resolve(apiType: String, id: String, ownRating: String? = null): AgeLevel? {
        ageLevelOf(ownRating)?.let { return it }
        return resolve(keyFor(apiType, id)) {
            source.lookup(apiType, id, null)
        }
    }

    fun known(key: String): AgeLevel? = _ratings.value[key]

    fun contains(key: String): Boolean = _ratings.value.containsKey(key)

    fun canResolve(): Boolean = availability.isEnabled()

    private suspend fun resolve(key: String, fetch: suspend () -> AgeRatingLookup): AgeLevel? {
        ensureCacheLoaded()
        _ratings.value.let { if (it.containsKey(key)) return it[key] }
        inFlight[key]?.let { return it.await() }
        val deferred = CompletableDeferred<AgeLevel?>()
        val prior = inFlight.putIfAbsent(key, deferred)
        if (prior != null) return prior.await()

        return try {
            val result = withContext(Dispatchers.IO) {
                semaphore.withPermit {
                    _ratings.value.let { if (it.containsKey(key)) return@withPermit it[key] }
                    if (!availability.isEnabled()) return@withPermit null
                    val lookup = fetch()
                    if (lookup.cacheable) {
                        val now = System.currentTimeMillis()
                        runCatching { cache.put(key, lookup.level, now) }
                        _ratings.update { it + (key to lookup.level) }
                    }
                    lookup.level
                }
            }
            deferred.complete(result)
            result
        } catch (cancelled: CancellationException) {
            deferred.complete(null)
            throw cancelled
        } catch (failed: Exception) {
            // Sin rastro, un fallo de red parecería un perfil vacío. Los fallos no se cachean: el
            // siguiente intento vuelve a consultar.
            Log.w(TAG, "Age rating lookup failed for $key", failed)
            deferred.complete(null)
            null
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        loadMutex.withLock {
            if (cacheLoaded) return
            val now = System.currentTimeMillis()
            val fresh = runCatching { cache.all(now) }.getOrDefault(emptyMap())
                .filterValues { cached ->
                    cached.storedAtMs <= now && now - cached.storedAtMs <= CACHE_TTL_MS
                }
            if (fresh.isNotEmpty()) {
                _ratings.update { current -> fresh.mapValues { it.value.level } + current }
            }
            cacheLoaded = true
        }
    }
}
