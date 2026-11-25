package com.nuvio.tv.core.profile

import com.nuvio.tv.data.local.ProfileAgeLimitStore
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AgeGateSnapshot(
    val limit: AgeLevel?,
    val ratings: Map<String, AgeLevel?>
)

fun AgeGateSnapshot.ratingOf(item: MetaPreview): AgeLevel? =
    ageLevelOf(item.ageRating) ?: ratings[ageRatingKey(item.apiType, item.id)]

fun AgeGateSnapshot.allows(item: MetaPreview): Boolean =
    isAllowed(limit, ratingOf(item))

/**
 * Único punto que combina el límite del perfil activo con las clasificaciones resueltas.
 * Sin límite no se resuelve nada ni se hace ninguna petición.
 */
@Singleton
class ActiveProfileAgeGate @Inject constructor(
    private val profileManager: ProfileManager,
    private val limitStore: ProfileAgeLimitStore,
    private val resolver: AgeRatingResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _limit = MutableStateFlow<AgeLevel?>(null)
    val limit: StateFlow<AgeLevel?> = _limit.asStateFlow()
    val ratings: StateFlow<Map<String, AgeLevel?>> = resolver.ratings
    val snapshot: StateFlow<AgeGateSnapshot> = combine(_limit, resolver.ratings) { limit, ratings ->
        AgeGateSnapshot(limit, ratings)
    }.stateIn(scope, SharingStarted.Eagerly, AgeGateSnapshot(null, emptyMap()))

    init {
        scope.launch {
            profileManager.activeProfileId
                .flatMapLatest { limitStore.observe(it) }
                .distinctUntilChanged()
                .collect { _limit.value = it }
        }
    }

    fun currentLimit(): AgeLevel? = _limit.value

    fun knownRating(item: MetaPreview): AgeLevel? =
        ageLevelOf(item.ageRating) ?: resolver.known(resolver.keyFor(item))

    fun knownRating(apiType: String, id: String): AgeLevel? =
        resolver.known(ageRatingKey(apiType, id))

    fun isAllowedNow(item: MetaPreview): Boolean = isAllowed(_limit.value, knownRating(item))

    fun isAllowedNow(apiType: String, id: String, ownRating: String?): Boolean =
        isAllowed(_limit.value, ageLevelOf(ownRating) ?: resolver.known(ageRatingKey(apiType, id)))

    suspend fun ratingFor(apiType: String, id: String, ownRating: String?): AgeLevel? =
        ageLevelOf(ownRating) ?: resolver.resolve(apiType, id)

    suspend fun currentLimitFromStore(): AgeLevel? {
        val profile = profileManager.activeProfileId.value
        val limit = limitStore.observe(profile).first()
        return limit.takeIf { profileManager.activeProfileId.value == profile }
    }

    suspend fun filter(items: List<MetaPreview>): List<MetaPreview> = filterForLimit(items, _limit.value)

    /** Built-in KIDS folders retain the strict TP ceiling during initial profile-state loading. */
    suspend fun filterForLimit(items: List<MetaPreview>, limit: AgeLevel?): List<MetaPreview> {
        if (limit == null) return items
        return coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) {
                    item to (ageLevelOf(item.ageRating) ?: resolver.resolve(item))
                }
            }.awaitAll()
                .filter { (_, rating) -> isAllowed(limit, rating) }
                .map { it.first }
        }
    }

    suspend fun filterRow(row: CatalogRow): CatalogRow {
        if (_limit.value == null) return row
        val filtered = filter(row.items)
        return if (filtered.size == row.items.size) row else row.copy(items = filtered)
    }

    /**
     * Show already verified titles immediately, then add safe titles in bounded batches.
     * Each snapshot preserves catalog order and only the final one can be empty.
     */
    internal suspend fun filterKidsProgressively(
        items: List<MetaPreview>,
        limit: AgeLevel,
        publish: (items: List<MetaPreview>, complete: Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) { resolver.ensureCacheLoaded() }
        val verified = withContext(Dispatchers.Default) {
            val known = items.associateWith(::knownRating).toMutableMap()
            val pending = known.filter { (item, rating) ->
                rating == null && !resolver.contains(resolver.keyFor(item))
            }.keys
            known to pending.chunked(10)
        }
        val (known, batches) = verified
        var previous: List<MetaPreview>? = null
        for (index in 0..batches.size) {
            val complete = index == batches.size
            val allowed = items.filter { isAllowed(limit, known[it]) }
            if (complete || (allowed.isNotEmpty() && allowed != previous)) publish(allowed, complete)
            previous = allowed
            if (!complete) {
                known.putAll(coroutineScope {
                    batches[index].map { item -> async { item to resolver.resolve(item) } }.awaitAll()
                })
            }
        }
    }

    /** Runs with the caller's lifetime so searches can cancel checks for obsolete queries. */
    internal suspend fun prefetchForActiveSearch(items: List<MetaPreview>) {
        if (_limit.value == null || !resolver.canResolve()) return
        withContext(Dispatchers.Default) {
            resolver.ensureCacheLoaded()
            items.asSequence()
                .filter { knownRating(it) == null && !resolver.contains(resolver.keyFor(it)) }
                .distinctBy { resolver.keyFor(it) }
                .chunked(10)
                .forEach { batch -> coroutineScope { batch.map { async { resolver.resolve(it) } }.awaitAll() } }
        }
    }

    /** Lanza la resolución en segundo plano; las filas se refrescan al llegar el resultado. */
    fun prefetch(items: List<MetaPreview>) {
        if (_limit.value == null || !resolver.canResolve()) return
        val pending = items.asSequence()
            .filter { ageLevelOf(it.ageRating) == null }
            .distinctBy { resolver.keyFor(it) }
            .filter { !resolver.contains(resolver.keyFor(it)) }
            .toList()
        if (pending.isEmpty()) return
        scope.launch {
            pending.chunked(10).forEach { batch ->
                coroutineScope { batch.map { async { resolver.resolve(it) } }.awaitAll() }
            }
        }
    }
}
