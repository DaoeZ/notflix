package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.util.isUnreleased
import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.model.Collection
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class NotflixCollectionScope(
    val profile: Int,
    val generation: Long,
    val visit: Long,
    val hideUnreleased: Boolean,
    val ageLimit: AgeLevel? = null,
    val hiddenKeys: Set<String> = emptySet()
)
internal data class NotflixCollectionSpec(
    val key: String, val title: String, val folder: Pair<String, String>,
    val source: AddonCatalogCollectionSource?, val request: CollectionCatalogRequest?
)

internal fun notflixCollectionSpecs(collections: List<Collection>, addons: List<Addon>, disabled: Set<String>): List<NotflixCollectionSpec> {
    val collection = collections.lastOrNull { it.id == "notflix-inicio" } ?: return emptyList()
    if ("collection_${collection.id}" in disabled) return emptyList()
    return collection.folders.associateBy { it.id }.values.map { folder ->
        val source = folder.sources.singleOrNull() as? AddonCatalogCollectionSource
        NotflixCollectionSpec("collection_${collection.id}:${folder.id}", folder.title,
            collection.id to folder.id, source, source?.collectionCatalogRequest(addons))
    }
}

/** A bounded lazy adapter owned by HomeViewModel, using its existing official repository.
 * All entry points run on the owning UI dispatcher. No preferences, HTTP client or folder VM.
 */
internal class NotflixCollectionRows(
    private val owner: CoroutineScope,
    private val load: (CollectionCatalogRequest) -> Flow<NetworkResult<CatalogRow>>,
    private val stillCurrent: (NotflixCollectionScope) -> Boolean,
    private val changed: () -> Unit,
    private val filterRow: suspend (CatalogRow) -> CatalogRow = { it }
) {
    companion object { const val MAX_PARALLEL = 2; const val MAX_ROWS = 16; const val MAX_ITEMS = 40 }
    private val permits = Semaphore(MAX_PARALLEL)
    private var context: NotflixCollectionScope? = null
    private var cachedContext: NotflixCollectionScope? = null
    private var specs = linkedMapOf<String, NotflixCollectionSpec>()
    private val jobs = mutableMapOf<String, Job>()
    private val running = mutableMapOf<String, Job>()
    private val data = linkedMapOf<String, CatalogRow>()
    private val phases = mutableMapOf<String, NotflixRowPhase>()

    fun configure(next: NotflixCollectionScope?, definitions: List<NotflixCollectionSpec>) {
        if (next == null) { cancelJobs(); context = null; return }
        if (context != next) {
            cancelJobs()
            val previous = cachedContext
            if (previous == null || previous.profile != next.profile || previous.generation != next.generation ||
                previous.hideUnreleased != next.hideUnreleased || previous.ageLimit != next.ageLimit ||
                previous.hiddenKeys != next.hiddenKeys) {
                data.clear(); phases.clear(); specs.clear()
            }
            context = next; cachedContext = next
        }
        val incoming = definitions.associateByTo(linkedMapOf()) { it.key }
        (specs.keys + incoming.keys).toSet().forEach { key ->
            if (specs[key]?.request != incoming[key]?.request || specs[key]?.source != incoming[key]?.source || key !in incoming) {
                jobs.remove(key)?.cancel(); running.remove(key); data.remove(key); phases.remove(key)
            }
        }
        specs = incoming
    }

    private fun cancelJobs() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear(); running.clear()
        phases.filterValues { it == NotflixRowPhase.PENDING }.keys.toList().forEach { key ->
            if (data[key] != null) phases[key] = NotflixRowPhase.READY else phases.remove(key)
        }
    }

    fun clear() {
        cancelJobs(); data.clear(); phases.clear(); specs.clear(); context = null; cachedContext = null
    }

    fun release(key: String) {
        // A layout expansion may hide the next row for one frame. Once HTTP has started,
        // finish that bounded first page into cache instead of repeating it on re-entry.
        // Profile/appearance/source retirement still cancels every job in configure/clear.
        if (key in running) return
        if (jobs.remove(key)?.also { it.cancel() } != null) {
            phases.remove(key); changed()
        }
    }

    fun request(key: String, retry: Boolean = false) {
        val captured = context ?: return
        val spec = specs[key] ?: return
        val source = spec.request ?: return
        if (!stillCurrent(captured) || key in jobs) return
        if (!retry && phases[key] in setOf(NotflixRowPhase.READY, NotflixRowPhase.ERROR)) return
        if (jobs.size >= MAX_PARALLEL + 2) return
        phases[key] = NotflixRowPhase.PENDING
        lateinit var job: Job
        job = owner.launch(start = CoroutineStart.LAZY) {
            try {
                permits.withPermit {
                    if (!valid(captured, spec, job)) return@withPermit
                    running[key] = job
                    load(source).collect { result ->
                        if (!valid(captured, spec, job)) return@collect
                        when (result) {
                            NetworkResult.Loading -> Unit
                            is NetworkResult.Success -> {
                                val ageAllowed = filterRow(result.data)
                                val hiddenAllowed = if (captured.hiddenKeys.isEmpty()) ageAllowed else {
                                    ageAllowed.copy(items = ageAllowed.items.filterNot { hiddenTitleKey(it.apiType, it.id) in captured.hiddenKeys })
                                }
                                val items = if (captured.hideUnreleased) hiddenAllowed.items.filterNot { it.isUnreleased(LocalDate.now()) } else hiddenAllowed.items
                                val seen = mutableSetOf<String>()
                                data.remove(key)
                                data[key] = hiddenAllowed.copy(items = items.filter { seen.add("${it.apiType}:${it.id}") }.take(MAX_ITEMS))
                                phases[key] = NotflixRowPhase.READY
                                while (data.size > MAX_ROWS) {
                                    val oldest = data.keys.firstOrNull { it !in jobs && it != key } ?: break
                                    data.remove(oldest); phases.remove(oldest)
                                }
                                changed()
                            }
                            is NetworkResult.Error -> { phases[key] = NotflixRowPhase.ERROR; changed() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (valid(captured, spec, job)) { phases[key] = NotflixRowPhase.ERROR; changed() }
            } finally {
                if (running[key] === job) running.remove(key)
                if (jobs[key] === job) jobs.remove(key)
            }
        }
        jobs[key] = job
        changed()
        job.start()
    }

    private fun valid(captured: NotflixCollectionScope, spec: NotflixCollectionSpec, job: Job) =
        jobs[spec.key] === job &&
        context == captured && stillCurrent(captured) && specs[spec.key]?.request == spec.request &&
            specs[spec.key]?.source == spec.source

    fun snapshot(): List<NotflixCatalogRow> = if (context == null) emptyList() else specs.values.map { spec ->
        NotflixCatalogRow(spec.key, spec.key, spec.title,
            if (spec.source == null) NotflixRowPhase.READY else if (spec.request == null) NotflixRowPhase.ERROR else phases[spec.key] ?: NotflixRowPhase.PENDING,
            data[spec.key], folderTarget = spec.folder, sourceIdentity = spec.request)
    }
}

/** Derived suppression, only exact Cinemeta/top/movie|series and only with usable substitutes. */
internal fun suppressReplacedPopular(rows: List<NotflixCatalogRow>, previousAliases: Map<String, String>): Pair<List<NotflixCatalogRow>, Map<String, String>> {
    val removed = mutableMapOf<String, String>()
    rows.filter { it.folderTarget != null && it.catalog?.items?.isNotEmpty() == true }.forEach { replacement ->
        val request = replacement.sourceIdentity ?: return@forEach
        if (request.addon.id != "com.linvo.cinemeta" || request.catalogId != "top" ||
            request.type !in setOf("movie", "series") || request.extraArgs.isNotEmpty()) return@forEach
        rows.filter { original ->
            original.folderTarget == null && original.collection == null &&
                original.loadKey == catalogRowLegacyKey(request.addon.id, request.type, "top") &&
                original.key == catalogRowStableKey(request.addon.id, request.addon.baseUrl, request.type, "top") &&
                original.catalog?.extraArgs?.isNotEmpty() != true
        }.forEach { removed[it.key] = replacement.key }
    }
    val visible = rows.filter { it.key !in removed }
    val keys = visible.map { it.key }.toSet()
    val aliases = (previousAliases + removed + removed.entries.associate { it.value to it.key })
        .filter { (from, to) -> from in keys || to in keys }.entries.toList().takeLast(24).associate { it.toPair() }
    return visible to aliases
}
