package com.nuvio.tv.ui.appearance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.InterFamily

/** Notflix native presentation tokens; no web runtime. */
object NotflixVisualSpec {
    // Actual rendered values for clock-controlled tests; no visible debug UI.
    val RenderedCardScale = SemanticsPropertyKey<Float>("NotflixRenderedCardScale")
    val RenderedCardWidthDp = SemanticsPropertyKey<Float>("NotflixRenderedCardWidthDp")
    /** M50: a settings row is drawing the inverted (white fill, black text) focus state. */
    val InvertedRowFocus = SemanticsPropertyKey<Boolean>("NotflixInvertedRowFocus")
    val Accent = Color(0xFFE50914)
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF18181D)
    val Text = Color(0xFFF4F4F5)
    val Secondary = Color(0xFFC4C4CC)
    /** M50: 1 dp line between settings rows, and the muted copy inside inverted focus. */
    val Divider = Color(0xFF26262C)
    val InvertedSecondary = Color(0xFF3C3C44)
    /** M50: Notflix switch, off state (and its focused variant over the white row). */
    val ToggleOffTrack = Color(0xFF3A3A42)
    val ToggleOffKnob = Color(0xFFC4C4CC)
    val ToggleOffTrackFocused = Color(0xFF8E8E96)
    val BodyFont = InterFamily
    val BrandFont = FontFamily(Font(R.font.notflix_bebas_neue_regular))
    const val GUTTER = 34
    const val NAV_HEIGHT = 44
    const val HERO_HEIGHT = 390
    const val FOCUS_MS = 120
    const val EXPAND_MS = 180
    /** La tarjeta enfocada pide una variante mayor del póster; el resto no. */
    const val SHARPER_FOCUSED_POSTER = true
    /** Tiempo sin pulsar antes de ocultar los controles de la vista grande del tráiler. */
    const val FULLSCREEN_CONTROLS_TIMEOUT_MS = 4_000L
    // M29 (promoter, Xiaomi): 600 ms grew the card before zapping on; a short beat feels calmer.
    const val EXPAND_DELAY_MS = 1200L
    /** Card preview starts after this dwell on a title (expansion first, then the clip). */
    const val PREVIEW_DWELL_MS = 2000L
    // M29 (promoter on the Xiaomi): portrait posters, same size in every row and «un pelín más grandes» than the
    // 180x266 trial (was 122x183 first rows / 168x248 others). Landscape 16:9 was tried and rejected by the promoter;
    // its layout stays available behind CARD_LANDSCAPE. 08-11-2026: promoter asked again for «un pelín más grandes»: +10% (was 190x281, CW 224x126).
    const val CARD_LANDSCAPE = false
    const val POSTER_WIDTH = 210
    const val POSTER_HEIGHT = 311
    const val EXPANDED_WIDTH = 378
    const val TV_POSTER_WIDTH = POSTER_WIDTH
    const val TV_POSTER_HEIGHT = POSTER_HEIGHT
    const val TV_EXPANDED_WIDTH = EXPANDED_WIDTH
    const val CW_WIDTH = 248
    const val CW_HEIGHT = 140
    fun posterWidth(browsing: Boolean) = if (browsing) TV_POSTER_WIDTH else POSTER_WIDTH
    fun posterHeight(browsing: Boolean) = if (browsing) TV_POSTER_HEIGHT else POSTER_HEIGHT
    fun expandedWidth(browsing: Boolean) = if (browsing) TV_EXPANDED_WIDTH else EXPANDED_WIDTH
}
