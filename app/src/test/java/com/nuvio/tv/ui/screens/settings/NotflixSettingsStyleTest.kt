package com.nuvio.tv.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M50: the pure parts of the Notflix settings style (no Compose runtime needed). */
class NotflixSettingsStyleTest {

    @Test
    fun switchTrackUsesTokensForEveryState() {
        assertEquals(Spec.ToggleOffTrack, notflixSettingsToggleTrack(checked = false, focused = false, enabled = true))
        assertEquals(
            Spec.ToggleOffTrackFocused,
            notflixSettingsToggleTrack(checked = false, focused = true, enabled = true)
        )
        assertEquals(Spec.Accent, notflixSettingsToggleTrack(checked = true, focused = false, enabled = true))
        assertEquals(Spec.Accent, notflixSettingsToggleTrack(checked = true, focused = true, enabled = true))
    }

    @Test
    fun disabledSwitchKeepsItsStateWithReducedAlpha() {
        val off = notflixSettingsToggleTrack(checked = false, focused = false, enabled = false)
        val on = notflixSettingsToggleTrack(checked = true, focused = false, enabled = false)
        assertEquals(Spec.ToggleOffTrack.copy(alpha = 0.35f), off)
        assertEquals(Spec.Accent.copy(alpha = 0.35f), on)
        assertFalse(off == Spec.ToggleOffTrack)
        assertFalse(on == Spec.Accent)
    }

    @Test
    fun switchKnobIsWhiteWhenOnAndMutedWhenOff() {
        assertEquals(Color.White, notflixSettingsToggleKnob(checked = true, enabled = true))
        assertEquals(Spec.ToggleOffKnob, notflixSettingsToggleKnob(checked = false, enabled = true))
        assertEquals(
            Spec.ToggleOffKnob.copy(alpha = 0.35f),
            notflixSettingsToggleKnob(checked = false, enabled = false)
        )
    }

    @Test
    fun rowValueIsTrimmedAndBlankValuesAreNotDrawn() {
        assertEquals("Enabled", notflixSettingsRowValue("  Enabled "))
        assertNull(notflixSettingsRowValue(null))
        assertNull(notflixSettingsRowValue(""))
        assertNull(notflixSettingsRowValue("   "))
    }

    @Test
    fun railIndicatorOnlyMarksTheSelectedItem() {
        assertTrue(notflixSettingsRailIndicator(selected = true, suppressSelectedVisual = false))
        assertFalse(notflixSettingsRailIndicator(selected = false, suppressSelectedVisual = false))
        assertFalse(notflixSettingsRailIndicator(selected = true, suppressSelectedVisual = true))
        assertFalse(notflixSettingsRailIndicator(selected = false, suppressSelectedVisual = true))
    }

    @Test
    fun choicePanelUsesTheMeasuredWidth() {
        assertEquals(420.dp, notflixSettingsPanelWidth())
        assertEquals(420.dp, NotflixSettingsStyle.PanelWidth)
    }

    @Test
    fun choiceLabelKeepsTitleTrailingAndDescription() {
        assertEquals(
            "English · EN · Original audio",
            notflixSettingsOptionLabel("English", " EN ", "Original audio")
        )
        assertEquals("English", notflixSettingsOptionLabel("English", null, null))
        assertEquals("English", notflixSettingsOptionLabel(" English ", "  ", ""))
        assertEquals("Auto", notflixSettingsOptionLabel("", null, "Auto"))
    }

    @Test
    fun measuresMatchTheCommission() {
        assertEquals(64.dp, NotflixSettingsStyle.RowMinHeight)
        assertEquals(16.dp, NotflixSettingsStyle.RowHorizontal)
        assertEquals(1.dp, NotflixSettingsStyle.DividerHeight)
        assertEquals(3.dp, NotflixSettingsStyle.RailBarWidth)
        assertEquals(22.dp, NotflixSettingsStyle.RailBarHeight)
        assertEquals(40.dp, NotflixSettingsStyle.ToggleWidth)
        assertEquals(22.dp, NotflixSettingsStyle.ToggleHeight)
        assertEquals(120, NotflixSettingsStyle.ToggleMotionMs)
        assertEquals(40.dp, NotflixSettingsStyle.ArtworkSize)
    }

    @Test
    fun tokensStayInsideTheVisualSpec() {
        assertEquals(Color(0xFF26262C), NotflixSettingsStyle.Divider)
        assertEquals(Color(0xFF3C3C44), NotflixSettingsStyle.InvertedSecondary)
    }
}
