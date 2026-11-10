package com.nuvio.tv.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixUiSoundsPolicyTest {

    @Test fun `confirm and open play when enabled and no video is running`() {
        assertTrue(shouldPlayUiSound(UiSoundEvent.CONFIRM, enabled = true, videoPlaying = false))
        assertTrue(shouldPlayUiSound(UiSoundEvent.OPEN_SCREEN, enabled = true, videoPlaying = false))
    }

    @Test fun `focus moves never play`() {
        assertFalse(shouldPlayUiSound(UiSoundEvent.FOCUS_MOVE, enabled = true, videoPlaying = false))
        assertFalse(shouldPlayUiSound(UiSoundEvent.FOCUS_MOVE, enabled = true, videoPlaying = true))
        assertFalse(shouldPlayUiSound(UiSoundEvent.FOCUS_MOVE, enabled = false, videoPlaying = false))
    }

    @Test fun `disabled setting silences every event`() {
        UiSoundEvent.entries.forEach { event ->
            assertFalse(shouldPlayUiSound(event, enabled = false, videoPlaying = false))
        }
    }

    @Test fun `video playing silences confirm and open`() {
        assertFalse(shouldPlayUiSound(UiSoundEvent.CONFIRM, enabled = true, videoPlaying = true))
        assertFalse(shouldPlayUiSound(UiSoundEvent.OPEN_SCREEN, enabled = true, videoPlaying = true))
    }

    /** Completes the 3x2x2 matrix required by M44: every event with the setting off and video running. */
    @Test fun `video playing together with the setting off silences every event`() {
        UiSoundEvent.entries.forEach { event ->
            assertFalse(shouldPlayUiSound(event, enabled = false, videoPlaying = true))
        }
    }
}
