package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.stableKey

enum class NotflixRowPhase { PENDING, READY, ERROR }

data class NotflixCatalogRow(
    val key: String,
    val loadKey: String,
    val title: String,
    val phase: NotflixRowPhase,
    val catalog: CatalogRow? = null,
    val collection: Collection? = null,
    val folderTarget: Pair<String, String>? = null,
    val sourceIdentity: com.nuvio.tv.domain.model.CollectionCatalogRequest? = null
)

/** Published with its data in one HomeUiState update, scoped to this VM/profile. */
data class NotflixCatalogSnapshot(
    val context: Int = 0,
    val generation: Long = 0,
    val configured: Boolean = false,
    val rows: List<NotflixCatalogRow> = emptyList(),
    val heroItems: List<MetaPreview> = emptyList(),
    val focusAliases: Map<String, String> = emptyMap(),
    val collectionRowsKnown: Boolean = false
)

data class NotflixFocusRecord(
    val rowKey: String, val itemKey: String, val canonicalKey: String,
    val index: Int, val rowIndex: Int, val expanded: Boolean = false
)

class NotflixHomeMemory {
    var focus: NotflixFocusRecord? = null
    var columnIndex = 0
    var columnOffset = 0
    var editorialIndex = 0
    var editorialKey: String? = null
    var editorialAction: String = "info"
    var retainedCatalog: NotflixCatalogSnapshot? = null
    var retainedCw: List<ContinueWatchingItem> = emptyList()
    val rowScroll = mutableMapOf<String, Pair<Int, Int>>()
    fun clear() {
        focus = null; columnIndex = 0; columnOffset = 0; editorialIndex = 0; editorialKey = null; editorialAction = "info"
        rowScroll.clear(); retainedCatalog = null; retainedCw = emptyList()
    }
}

private fun retainNotflixRow(row: NotflixCatalogRow, retained: NotflixCatalogRow?): NotflixCatalogRow =
    if (row.phase != NotflixRowPhase.READY && retained != null && retained.sourceIdentity == row.sourceIdentity &&
        (retained.catalog?.items?.isNotEmpty() == true || retained.collection != null))
        row.copy(catalog = retained.catalog, collection = retained.collection) else row

private fun isProjectedCollection(row: NotflixCatalogRow) =
    row.folderTarget?.first == "notflix-inicio" || row.collection?.id == "notflix-inicio"

/** UI retention only. Catalog refresh cannot undo an authoritative collection edit/removal. */
fun retainNotflixPresentation(state: HomeUiState, memory: NotflixHomeMemory): HomeUiState {
    val incoming = state.notflixCatalog
    val previous = memory.retainedCatalog?.takeIf { it.context == incoming.context }
    val retainedCatalog = if (previous == null) incoming else if (!incoming.configured) {
        incoming.copy(rows = previous.rows.map { it.copy(phase = NotflixRowPhase.PENDING) }, heroItems = previous.heroItems)
    } else {
        val old = previous.rows.associateBy { it.key }
        incoming.copy(rows = incoming.rows.map { row ->
            retainNotflixRow(row, old[row.key])
        })
    }
    // Retained titles are usable substitutes too. Derive suppression after retention so
    // a refresh never briefly displays both the retained substitute and the original Popular.
    val membership = if (previous != null && !incoming.configured && incoming.collectionRowsKnown) {
        val own = incoming.rows.filter(::isProjectedCollection)
        val old = previous.rows.associateBy { it.key }
        val other = retainedCatalog.rows.filterNot(::isProjectedCollection).toMutableList()
        val position = incoming.rows.indexOfFirst(::isProjectedCollection).coerceIn(0, other.size)
        other.addAll(position, own.map { retainNotflixRow(it, old[it.key]) })
        retainedCatalog.copy(rows = other)
    } else retainedCatalog
    val (derivedRows, aliases) = suppressReplacedPopular(membership.rows, membership.focusAliases)
    val catalog = membership.copy(rows = derivedRows, focusAliases = aliases)
    val cw = if (previous != null && state.notflixCwPhase != NotflixRowPhase.READY && memory.retainedCw.isNotEmpty())
        memory.retainedCw else state.continueWatchingItems
    return if (catalog == incoming && cw == state.continueWatchingItems) state
        else state.copy(notflixCatalog = catalog, continueWatchingItems = cw)
}

data class NotflixFocusItem(val key: String, val canonicalKey: String)
data class NotflixFocusRow(val key: String, val phase: NotflixRowPhase, val items: List<NotflixFocusItem>)
sealed interface NotflixFocusResolution {
    data object Wait : NotflixFocusResolution
    data object Top : NotflixFocusResolution
    data class Item(val row: Int, val index: Int) : NotflixFocusResolution
}

/** No timeout, global loading flag, or ambiguous CW series match can retire focus. */
fun resolveNotflixFocus(saved: NotflixFocusRecord, rows: List<NotflixFocusRow>, configured: Boolean): NotflixFocusResolution {
    if (!configured) return NotflixFocusResolution.Wait
    val rowIndex = rows.indexOfFirst { it.key == saved.rowKey }
    if (rowIndex >= 0) {
        val row = rows[rowIndex]
        val exact = row.items.indexOfFirst { it.key == saved.itemKey }
        if (exact >= 0) return NotflixFocusResolution.Item(rowIndex, exact)
        val canonical = row.items.withIndex().filter { it.value.canonicalKey == saved.canonicalKey }
        if (canonical.size == 1) return NotflixFocusResolution.Item(rowIndex, canonical.single().index)
        if (row.phase != NotflixRowPhase.READY) return NotflixFocusResolution.Wait
        if (row.items.isNotEmpty()) return NotflixFocusResolution.Item(rowIndex, saved.index.coerceIn(row.items.indices))
    }
    val start = if (rowIndex >= 0) rowIndex else saved.rowIndex.coerceIn(0, rows.size)
    // Do not skip a pending possible neighbour merely because its data hasn't arrived.
    val order = (start until rows.size).toList() + (start - 1 downTo 0).toList()
    for (index in order) {
        val row = rows[index]
        if (row.items.isNotEmpty()) return NotflixFocusResolution.Item(index, saved.index.coerceIn(row.items.indices))
        if (row.phase != NotflixRowPhase.READY) return NotflixFocusResolution.Wait
    }
    return NotflixFocusResolution.Top
}

fun notflixFocusNeedsRepair(saved: NotflixFocusRecord?, rows: List<NotflixFocusRow>, configured: Boolean): Boolean {
    if (saved == null || saved.rowKey in setOf("hero", "retry")) return false
    val row = rows.firstOrNull { it.key == saved.rowKey }
    val stillPresent = if (saved.itemKey == "status") row != null && row.items.isEmpty() && row.phase != NotflixRowPhase.READY
        else row?.items?.any { it.key == saved.itemKey } == true
    return !stillPresent && resolveNotflixFocus(saved, rows, configured) != NotflixFocusResolution.Top
}

/** Tracks outcomes of the existing catalog requests, not another fetch pipeline. */
class NotflixCatalogLedger {
    data class Descriptor(val key: String, val title: String, val stableKey: String)
    private var generation = -1L
    private var configured = false
    private var descriptors = emptyList<Descriptor>()
    private val phases = mutableMapOf<String, NotflixRowPhase>()

    @Synchronized fun begin(next: Long) {
        generation = next; configured = false; descriptors = emptyList(); phases.clear()
    }
    @Synchronized fun configure(next: Long, rows: List<Descriptor>) {
        if (next != generation) return
        configured = true; descriptors = rows.toList()
        rows.forEach { phases.putIfAbsent(it.key, NotflixRowPhase.PENDING) }
    }
    @Synchronized fun outcome(next: Long, key: String, phase: NotflixRowPhase) {
        if (next == generation) phases[key] = phase
    }
    @Synchronized fun snapshot(next: Long, context: Int, data: Map<String, CatalogRow>, previous: NotflixCatalogSnapshot): NotflixCatalogSnapshot {
        if (next != generation || !configured) return previous.copy(configured = false)
        val old = previous.rows.associateBy { it.loadKey }
        return NotflixCatalogSnapshot(context, generation, true, descriptors.map { d ->
            val phase = phases[d.key] ?: NotflixRowPhase.PENDING
            // Error or pending does not retire retained data. Successful empty is definitive.
            val catalog = data[d.key] ?: if (phase != NotflixRowPhase.READY) old[d.key]?.catalog else null
            NotflixCatalogRow(d.stableKey, d.key, d.title, phase, catalog)
        }, focusAliases = previous.focusAliases)
    }
}

fun notflixMetaKey(item: MetaPreview): String = "${item.apiType}:${item.id}"
