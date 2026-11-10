package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.darkColorScheme
import com.nuvio.tv.ui.theme.*

/** Presentation only; never selects a playback engine or changes stored settings. */
val LocalNotflixControlColors = staticCompositionLocalOf { false }

/** Visual values only. LocalAppTheme remains the user's actual official choice. */
object NotflixPresentationPalette {
    val palette = ThemeColorPalette(
        secondary = NotflixVisualSpec.Accent,
        secondaryVariant = Color(0xFFB20710),
        onSecondary = Color.White,
        onSecondaryVariant = Color.White,
        focusRing = NotflixVisualSpec.Text,
        focusBackground = Color(0xFF351A21),
        background = NotflixVisualSpec.Background,
        backgroundElevated = NotflixVisualSpec.Surface,
        backgroundCard = NotflixVisualSpec.Surface,
        surface = NotflixVisualSpec.Surface,
        surfaceVariant = Color(0xFF24242D),
        panel = NotflixVisualSpec.Surface,
        field = Color(0xFF202028),
        menu = NotflixVisualSpec.Surface,
        modal = NotflixVisualSpec.Surface
    )
    val colors = NuvioColorScheme(palette,
        primary = NotflixVisualSpec.Text, primaryVariant = NotflixVisualSpec.Secondary,
        textPrimary = NotflixVisualSpec.Text, textSecondary = NotflixVisualSpec.Secondary,
        textTertiary = Color(0xFF93939F))
}

/** One stable provider slot for both appearances. Never owns navigation or writes preferences. */
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun NotflixPresentationTheme(active: Boolean, content: @Composable () -> Unit) {
    val palette = if (active) NotflixPresentationPalette.palette else NuvioTheme.palette
    val colors = if (active) NotflixPresentationPalette.colors else NuvioTheme.colors
    val extended = if (active) remember(colors) {
        NuvioExtendedColors(colors.BackgroundElevated, colors.BackgroundCard, colors.TextSecondary,
            colors.TextTertiary, colors.FocusRing, colors.FocusBackground, colors.Rating)
    } else NuvioTheme.extendedColors
    val focus = if (active) remember(palette) { createFocusRingStyle(palette) } else NuvioTheme.focusRing
    val material = if (active) remember(colors) {
        darkColorScheme(primary = colors.Secondary, onPrimary = colors.OnSecondary,
            secondary = colors.Secondary, onSecondary = colors.OnSecondary,
            background = colors.Background, surface = colors.Surface, surfaceVariant = colors.SurfaceVariant,
            onBackground = colors.TextPrimary, onSurface = colors.TextPrimary,
            onSurfaceVariant = colors.TextSecondary, error = colors.Error)
    } else MaterialTheme.colorScheme
    CompositionLocalProvider(
        LocalNotflixControlColors provides active,
        LocalNuvioColors provides colors,
        LocalNuvioExtendedColors provides extended,
        LocalThemePalette provides palette,
        LocalNuvioFocusRingStyle provides focus
    ) {
        MaterialTheme(colorScheme = material, typography = MaterialTheme.typography, content = content)
    }
}
