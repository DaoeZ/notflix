package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.AppearanceContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotflixM70RulesTest {
    private val ready = PreviewEnvironment(binding = PreviewBinding(AppearanceContext(1, 1), 1),
        valid = true, resumed = true, windowFocused = true, introActive = false)

    @Test fun rotationIgnoresReducedMotionCardVideoAndPreviewPreference() {
        for (reduced in listOf(false, true)) for (enabled in listOf(false, true)) {
            val environment = ready.copy(reduced = reduced, enabled = enabled, otherVideo = true,
                blocked = true, rotationBlocked = false)
            assertTrue(environment.billboardRotationReady())
            assertTrue(notflixBillboardRotates(environment.billboardRotationReady(), false, false, 6))
        }
    }

    @Test fun bannerFocusPausesAndLeavingItOrReturningToHomeResumes() {
        assertFalse(notflixBillboardRotates(ready.billboardRotationReady(), true, false, 6))
        assertTrue(notflixBillboardRotates(ready.billboardRotationReady(), false, false, 6))
        assertFalse(ready.copy(resumed = false).billboardRotationReady())
        assertFalse(ready.copy(rotationBlocked = true).billboardRotationReady())
        assertTrue(ready.copy(rotationBlocked = false).billboardRotationReady())
        assertFalse(notflixBillboardRotates(true, false, true, 6))
        assertFalse(notflixBillboardRotates(true, false, false, 1))
    }

    @Test fun actionsHintExistsOnlyBeforeEnteringTheButtons() {
        assertTrue(notflixShowActionsHint(null, 6))
        assertFalse(notflixShowActionsHint(0, 6))
        assertFalse(notflixShowActionsHint(5, 6))
        assertFalse(notflixShowActionsHint(null, 0))
    }

    @Test fun undoNeverExpiresWithFocusAndGetsFiveNewSecondsOnLeaving() = runTest {
        val expired = mutableListOf<HiddenTitleUndo>()
        val timer = HiddenTitleUndoTimeout(this, expired::add)
        val undo = HiddenTitleUndo(1, "movie:one", "One")
        timer.start(undo); advanceTimeBy(4_999); runCurrent()
        assertTrue(expired.isEmpty())
        timer.focus(undo, true); advanceTimeBy(30_000); runCurrent()
        assertTrue(expired.isEmpty())
        timer.focus(undo, false); advanceTimeBy(4_999); runCurrent()
        assertTrue(expired.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(undo), expired)
    }

    @Test fun oldNoticeCannotExpireOrPauseTheNextProfileNoticeAndUndoCancelsExpiry() = runTest {
        val expired = mutableListOf<HiddenTitleUndo>()
        val timer = HiddenTitleUndoTimeout(this, expired::add)
        val first = HiddenTitleUndo(1, "movie:one", "One")
        val second = first.copy(profileId = 2)
        timer.start(first); advanceTimeBy(4_000); timer.start(second)
        timer.focus(first, true); advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(second), expired)
        timer.start(first); timer.cancel(); advanceUntilIdle()
        assertEquals(listOf(second), expired)
    }
}
