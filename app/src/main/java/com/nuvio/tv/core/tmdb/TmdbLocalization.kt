package com.nuvio.tv.core.tmdb

import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview

/**
 * M61: TMDB is asked in the app language, so its title and synopsis replace the addon's whenever a
 * translation exists. The card, the banner and the detail screen share this one rule; a field TMDB
 * does not return (or returns as the original in another language) keeps the text the addon sent.
 */
internal fun MetaPreview.withLocalizedTmdbText(enrichment: TmdbEnrichment?): MetaPreview =
    if (enrichment == null) this else copy(
        name = enrichment.localizedTitle?.takeIf { it.isNotBlank() } ?: name,
        description = enrichment.description?.takeIf { it.isNotBlank() } ?: description
    )

/** The same rule for the detail screen's [Meta]. */
internal fun Meta.withLocalizedTmdbText(enrichment: TmdbEnrichment?): Meta =
    if (enrichment == null) this else copy(
        name = enrichment.localizedTitle?.takeIf { it.isNotBlank() } ?: name,
        description = enrichment.description?.takeIf { it.isNotBlank() } ?: description
    )
