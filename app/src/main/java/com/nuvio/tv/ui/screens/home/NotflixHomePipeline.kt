package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.R

/** Captured beside the official rows; stale generation results never publish removals. */
internal fun HomeViewModel.notflixSnapshot(
    captured: NotflixCatalogSnapshot, ordered: List<String>, fullRows: List<CatalogRow>, collections: List<Collection>
): NotflixCatalogSnapshot {
    configureNotflixCollectionRows()
    val scopedCollections = if (notflixCollectionsProfile == captured.context) collections else emptyList()
    return projectNotflixCatalogs(captured, ordered, fullRows, scopedCollections, disabledHomeCatalogKeys,
        customCatalogTitles, synchronized(catalogStateLock) { placeholderDescriptors.toList() },
        _uiState.value.catalogTypeSuffixEnabled, appContext.getString(R.string.type_movie),
        appContext.getString(R.string.type_series), notflixCollectionRows.snapshot())
        .copy(collectionRowsKnown = _uiState.value.notflixPresentationActive && notflixCollectionsProfile == captured.context)
}

/** Presentation over captured official inputs only; never reads/writes preferences or starts a load. */
internal fun projectNotflixCatalogs(
    captured: NotflixCatalogSnapshot, ordered: List<String>, fullRows: List<CatalogRow>, collections: List<Collection>,
    disabled: Set<String>, customTitles: Map<String, String>, descriptors: List<HomeViewModel.PlaceholderDescriptor>,
    showTypeSuffix: Boolean, movieLabel: String, seriesLabel: String,
    projectedFolders: List<NotflixCatalogRow> = emptyList()
): NotflixCatalogSnapshot {
    val filtered = fullRows.associateBy { it.legacyKey() }
    val names = descriptors.associateBy { it.catalogKey }
    val catalog = captured.copy(rows = captured.rows.map { row ->
        val latest = filtered[row.loadKey]
        val descriptor = names[row.loadKey]
        val base = customTitles[row.loadKey]?.takeIf { it.isNotBlank() }
            ?: descriptor?.catalogName ?: latest?.catalogName
        val type = descriptor?.apiType ?: (latest ?: row.catalog)?.apiType
        // Match the official display-title convention, including default names and optional type suffix.
        val title = if (base != null && type != null) {
            val name = base.replaceFirstChar { it.uppercase() }
            val suffix = when (type.lowercase()) {
                "movie" -> movieLabel.ifBlank { type.replaceFirstChar { it.uppercase() } }
                "series" -> seriesLabel.ifBlank { type.replaceFirstChar { it.uppercase() } }
                else -> type.replaceFirstChar { it.uppercase() }
            }
            if (showTypeSuffix) "$name - $suffix" else name
        } else row.title
        row.copy(title = title, catalog = latest ?: row.catalog)
    })
    val collectionRows = collections.filter { "collection_${it.id}" !in disabled }
        .map { NotflixCatalogRow("collection_${it.id}", "collection_${it.id}", it.title, NotflixRowPhase.READY, collection = it) }
    val byKey = (catalog.rows + collectionRows).associateBy { it.loadKey }
    val pinned = collections.filter { it.pinToTop }.map { "collection_${it.id}" }
    val folderGroups = projectedFolders.groupBy { it.folderTarget?.first }
    val expanded = (pinned + ordered).distinct().mapNotNull { byKey[it] }
        .filter { it.loadKey !in disabled }.flatMap { row ->
            if (row.collection?.id == "notflix-inicio") folderGroups[row.collection.id] ?: listOf(row) else listOf(row)
        }
    val (rows, aliases) = suppressReplacedPopular(expanded, captured.focusAliases)
    return catalog.copy(rows = rows, focusAliases = aliases)
}
