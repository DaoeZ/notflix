package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.PosterShape

/** How a folder card draws its cover. Pure so the empty-card fix is testable without Compose. */
internal enum class NotflixFolderArtworkMode { CROP, FIT, EMOJI, FALLBACK }

internal fun notflixFolderArtworkMode(
    imageUrl: String?,
    imageFailed: Boolean,
    coverEmoji: String?,
    tileShape: PosterShape
): NotflixFolderArtworkMode {
    val hasImage = !imageUrl.isNullOrBlank()
    return when {
        hasImage && !imageFailed && tileShape == PosterShape.POSTER -> NotflixFolderArtworkMode.CROP
        hasImage && !imageFailed -> NotflixFolderArtworkMode.FIT
        !hasImage && !coverEmoji.isNullOrBlank() -> NotflixFolderArtworkMode.EMOJI
        else -> NotflixFolderArtworkMode.FALLBACK
    }
}
