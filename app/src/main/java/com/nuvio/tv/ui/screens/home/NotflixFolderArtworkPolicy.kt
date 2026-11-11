package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.PosterShape

/** How a folder card draws its cover. Pure so the empty-card fix is testable without Compose. */
internal enum class NotflixFolderArtworkMode { CROP, FIT, EMOJI, FALLBACK }

/**
 * Nuvio collections are usually landscape banners with their text inside the image. In a portrait
 * card they shrank to a strip, so a row whose covers are mostly landscape uses wide cards instead.
 */
internal fun notflixCollectionRowIsWide(folders: List<CollectionFolder>): Boolean {
    val covered = folders.filter { !it.coverImageUrl.isNullOrBlank() }
    return covered.isNotEmpty() && covered.count { it.tileShape == PosterShape.LANDSCAPE } * 2 > covered.size
}

internal fun notflixFolderArtworkMode(
    imageUrl: String?,
    imageFailed: Boolean,
    coverEmoji: String?,
    tileShape: PosterShape,
    wideCard: Boolean = false
): NotflixFolderArtworkMode {
    val hasImage = !imageUrl.isNullOrBlank()
    // The cover fills the card when its shape matches the card: posters in portrait, banners in wide cards.
    val matchesCard = if (wideCard) tileShape == PosterShape.LANDSCAPE else tileShape == PosterShape.POSTER
    return when {
        hasImage && !imageFailed && matchesCard -> NotflixFolderArtworkMode.CROP
        hasImage && !imageFailed -> NotflixFolderArtworkMode.FIT
        !hasImage && !coverEmoji.isNullOrBlank() -> NotflixFolderArtworkMode.EMOJI
        else -> NotflixFolderArtworkMode.FALLBACK
    }
}
