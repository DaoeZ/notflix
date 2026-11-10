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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    suspend fun filter(items: List<MetaPreview>): List<MetaPreview> {
        val limit = _limit.value ?: return items
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
            pending.forEach { resolver.resolve(it) }
        }
    }
}
