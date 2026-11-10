package com.nuvio.tv.domain.model

/** The same addon/catalog fallback and genre rules used by the official folder screen. */
data class CollectionCatalogRequest(
    val addon: Addon,
    val catalog: CatalogDescriptor?,
    val catalogId: String,
    val type: String,
    val extraArgs: Map<String, String>
) {
    val supportsSkip get() = catalog?.supportsExtra("skip") ?: false
    val skipStep get() = catalog?.skipStep() ?: 100
}

fun AddonCatalogCollectionSource.collectionCatalogRequest(addons: List<Addon>): CollectionCatalogRequest? {
    val declared = addons.find { it.id == addonId } ?: return null
    var effective = declared
    var descriptor = declared.catalogs.findCollectionCatalog(type, catalogId)
        ?: declared.catalogs.findCollectionCatalog(type, catalogId.substringBefore(","))
    if (descriptor == null) {
        for (candidate in addons) {
            val match = candidate.catalogs.findCollectionCatalog(type, catalogId)
            if (match != null) { effective = candidate; descriptor = match; break }
        }
    }
    return CollectionCatalogRequest(effective, descriptor, catalogId, descriptor?.apiType ?: type, collectionExtraArgs())
}

fun AddonCatalogCollectionSource.collectionExtraArgs(): Map<String, String> =
    genre?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
        ?.let { mapOf("genre" to it) } ?: emptyMap()
