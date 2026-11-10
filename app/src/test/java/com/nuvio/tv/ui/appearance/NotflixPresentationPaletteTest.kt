package com.nuvio.tv.ui.appearance

import androidx.compose.ui.graphics.Color
import com.nuvio.tv.ui.theme.NuvioColorScheme
import com.nuvio.tv.ui.theme.NuvioPrimitives
import com.nuvio.tv.ui.theme.ThemeColors
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class NotflixPresentationPaletteTest {
    private fun luminance(c: Color): Double {
        fun linear(v: Float) = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        return .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)
    }
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (max(x, y) + .05) / (min(x, y) + .05)
    }
    @Test fun officialConstructorDefaultsArePreserved() {
        val official = NuvioColorScheme(ThemeColors.White)
        assertEquals(NuvioPrimitives.neutral500, official.Primary)
        assertEquals(ThemeColors.White.secondary, official.Secondary)
        assertEquals(NuvioPrimitives.white, official.TextPrimary)
        assertEquals(NuvioPrimitives.neutral400, official.TextSecondary)
        assertNotEquals(official.Secondary, NotflixPresentationPalette.colors.Secondary)
    }
    @Test fun textActionsAndFocusKeepContrastOnOwnSurfaces() {
        val c = NotflixPresentationPalette.colors
        assertTrue(contrast(c.OnSecondary, c.Secondary) >= 4.5)
        assertTrue(contrast(c.TextPrimary, c.Background) >= 4.5)
        assertTrue(contrast(c.TextSecondary, c.Surface) >= 4.5)
        assertTrue(contrast(c.FocusRing, c.FocusBackground) >= 4.5)
        // Primary is also used as foreground in official ErrorState: do not make it dark red.
        assertTrue(contrast(c.Primary, c.FocusBackground) >= 4.5)
        assertEquals(NotflixVisualSpec.Accent, c.Secondary)
        assertEquals(NotflixVisualSpec.Background, c.Background)
    }
}
