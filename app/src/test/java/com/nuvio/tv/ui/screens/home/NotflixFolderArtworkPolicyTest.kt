package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

class NotflixFolderArtworkPolicyTest {
    @Test fun posterCoverFillsTheCard() {
        assertEquals(NotflixFolderArtworkMode.CROP,
            notflixFolderArtworkMode("https://fixture.invalid/poster.jpg", false, null, PosterShape.POSTER))
    }

    @Test fun squareAndLandscapeCoversKeepTheWholeImage() {
        assertEquals(NotflixFolderArtworkMode.FIT,
            notflixFolderArtworkMode("https://fixture.invalid/logo.png", false, null, PosterShape.SQUARE))
        assertEquals(NotflixFolderArtworkMode.FIT,
            notflixFolderArtworkMode("https://fixture.invalid/banner.png", false, null, PosterShape.LANDSCAPE))
    }

    @Test fun emojiIsUsedOnlyWhenThereIsNoImage() {
        assertEquals(NotflixFolderArtworkMode.EMOJI,
            notflixFolderArtworkMode(null, false, "🎬", PosterShape.POSTER))
        assertEquals(NotflixFolderArtworkMode.EMOJI,
            notflixFolderArtworkMode("   ", false, "🎬", PosterShape.SQUARE))
    }

    @Test fun emptyFolderAndBrokenImageKeepTheSeeAllDrawing() {
        assertEquals(NotflixFolderArtworkMode.FALLBACK,
            notflixFolderArtworkMode(null, false, null, PosterShape.POSTER))
        assertEquals(NotflixFolderArtworkMode.FALLBACK,
            notflixFolderArtworkMode("   ", false, "", PosterShape.SQUARE))
        assertEquals(NotflixFolderArtworkMode.FALLBACK,
            notflixFolderArtworkMode("https://fixture.invalid/missing.jpg", true, null, PosterShape.POSTER))
    }

    @Test fun failedImageUsesTheDrawingEvenIfAnEmojiIsStored() {
        assertEquals(NotflixFolderArtworkMode.FALLBACK,
            notflixFolderArtworkMode("https://fixture.invalid/missing.jpg", true, "🎬", PosterShape.LANDSCAPE))
    }
}
