package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun bannerFillsAWideCardAndPosterIsKeptWhole() {
        assertEquals(NotflixFolderArtworkMode.CROP,
            notflixFolderArtworkMode("https://fixture.invalid/banner.png", false, null, PosterShape.LANDSCAPE, wideCard = true))
        assertEquals(NotflixFolderArtworkMode.FIT,
            notflixFolderArtworkMode("https://fixture.invalid/poster.jpg", false, null, PosterShape.POSTER, wideCard = true))
    }

    @Test fun rowIsWideOnlyWhenMostCoveredFoldersAreLandscape() {
        fun folder(shape: PosterShape, cover: String? = "https://fixture.invalid/c.png") =
            CollectionFolder(id = shape.name + cover, title = "f", coverImageUrl = cover, tileShape = shape)
        assertTrue(notflixCollectionRowIsWide(listOf(folder(PosterShape.LANDSCAPE), folder(PosterShape.LANDSCAPE), folder(PosterShape.POSTER))))
        assertFalse(notflixCollectionRowIsWide(listOf(folder(PosterShape.LANDSCAPE), folder(PosterShape.POSTER))))
        assertFalse(notflixCollectionRowIsWide(listOf(folder(PosterShape.POSTER), folder(PosterShape.SQUARE))))
        // Folders without a cover do not vote; a row with no covers keeps portrait cards.
        assertTrue(notflixCollectionRowIsWide(listOf(folder(PosterShape.LANDSCAPE), folder(PosterShape.SQUARE, cover = null))))
        assertFalse(notflixCollectionRowIsWide(listOf(folder(PosterShape.LANDSCAPE, cover = null))))
    }
}
