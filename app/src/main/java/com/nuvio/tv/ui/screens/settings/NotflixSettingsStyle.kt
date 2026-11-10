package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * M50: the Notflix flavour of the settings design system. Nuvio keeps the three official styles
 * (Minimal, Top Bar, Default); when the Notflix presentation is active every component in
 * [SettingsDesignSystem] reads these values instead, so all 78 settings screens inherit the look
 * without touching their own logic.
 *
 * Colours live in [Spec] (the single token home) and are only read here, never hard-coded in the
 * screens.
 */
internal object NotflixSettingsStyle {
    // Flat rows: 64 dp minimum, 16 dp lateral padding, no card and no border.
    val RowMinHeight = 64.dp
    val RowHorizontal = 16.dp
    val RowVertical = 10.dp
    val DividerHeight = 1.dp

    // Rail: text 20, a 3x22 Accent bar on the selected item, 20 dp icons.
    val RailBarWidth = 3.dp
    val RailBarHeight = 22.dp
    val RailIconSize = 20.dp
    val RailTextSize = 20.sp
    val RailTextLineHeight = 24.sp

    // Switch: 40x22 track, 18 dp knob, 120 ms travel.
    val ToggleWidth = 40.dp
    val ToggleHeight = 22.dp
    val ToggleKnob = 18.dp
    const val ToggleMotionMs = 120

    // Choice panels and page headers.
    val PanelWidth = 420.dp
    val DetailHeaderGap = 24.dp
    val PageTitleSize = 48.sp
    val PageTitleLineHeight = 52.sp
    val SecondarySize = 16.sp
    val SecondaryLineHeight = 21.sp
    val GroupTitleSize = 14.sp
    val GroupTitleLineHeight = 18.sp
    val RowTitleSize = 18.sp
    val RowTitleLineHeight = 23.sp
    val RowSubtitleSize = 14.sp
    val RowSubtitleLineHeight = 19.sp
    val RowValueSize = 16.sp
    val RowValueLineHeight = 20.sp

    // Add-on and plugin rows reuse the same rules with a 40 dp artwork.
    val ArtworkSize = 40.dp
    val GlyphSize = 18.sp

    val InvertedSecondary: Color = Spec.InvertedSecondary
    val Divider: Color = Spec.Divider
}

/** True while the Notflix presentation is on. Every Notflix branch in settings reads this once. */
@Composable
@ReadOnlyComposable
internal fun notflixSettingsActive(): Boolean = LocalNotflixPresentation.current.active

/**
 * Switch track colour. Off-over-white uses the lighter grey so the track still reads when the row
 * is focused and inverted.
 */
internal fun notflixSettingsToggleTrack(checked: Boolean, focused: Boolean, enabled: Boolean): Color {
    val base = when {
        checked -> Spec.Accent
        focused -> Spec.ToggleOffTrackFocused
        else -> Spec.ToggleOffTrack
    }
    return if (enabled) base else base.copy(alpha = 0.35f)
}

/** Switch knob colour: white when on, the muted grey when off. */
internal fun notflixSettingsToggleKnob(checked: Boolean, enabled: Boolean): Color {
    val base = if (checked) Color.White else Spec.ToggleOffKnob
    return if (enabled) base else base.copy(alpha = 0.35f)
}

/**
 * Row value as drawn on the right: the same text the screen already passes, with no invented data.
 * Blank values are not drawn at all so a row never shows a lone separator.
 */
internal fun notflixSettingsRowValue(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

/** The small Accent bar only marks the selected rail item, and never while focus hides selection. */
internal fun notflixSettingsRailIndicator(selected: Boolean, suppressSelectedVisual: Boolean): Boolean =
    selected && !suppressSelectedVisual

/** Choice dialogs become a right hand panel of this width. */
internal fun notflixSettingsPanelWidth(): Dp = NotflixSettingsStyle.PanelWidth

/**
 * Choice label inside the side panel. The option title keeps its trailing value (a language code,
 * for instance) and its description, so nothing the centred dialog used to show is lost now that
 * the row has a single line of text.
 */
internal fun notflixSettingsOptionLabel(
    title: String,
    trailing: String?,
    description: String?
): String = buildList {
    title.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
    trailing?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    description?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
}.joinToString(" · ")
