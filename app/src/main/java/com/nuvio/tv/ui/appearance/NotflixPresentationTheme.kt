package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.darkColorScheme
import com.nuvio.tv.ui.theme.*

/** Presentation only; never selects a playback engine or changes stored settings. */
val LocalNotflixControlColors = staticCompositionLocalOf { false }

/** Visual values only. LocalAppTheme remains the user's actual official choice. */
object NotflixPresentationPalette {
    // Getters, not stored values: they follow the theme colour (NotflixAccent).
    val palette get() = ThemeColorPalette(
        secondary = NotflixVisualSpec.Accent,
        secondaryVariant = NotflixAccent.dark,
        onSecondary = Color.White,
        onSecondaryVariant = Color.White,
        focusRing = NotflixVisualSpec.Text,
        focusBackground = if (NotflixAccent.selected == NotflixAccentColor.RED) Color(0xFF351A21) else lerp(Color(0xFF18181D), NotflixAccent.color, 0.22f),
        background = NotflixVisualSpec.Background,
        backgroundElevated = NotflixVisualSpec.Surface,
        backgroundCard = NotflixVisualSpec.Surface,
        surface = NotflixVisualSpec.Surface,
        surfaceVariant = NotflixAccent.surfaceVariant,
        panel = NotflixVisualSpec.Surface,
        field = Color(0xFF202028),
        menu = NotflixVisualSpec.Surface,
        modal = NotflixVisualSpec.Surface
    )
    val colors get() = NuvioColorScheme(palette,
        primary = NotflixVisualSpec.Text, primaryVariant = NotflixVisualSpec.Secondary,
        textPrimary = NotflixVisualSpec.Text, textSecondary = NotflixVisualSpec.Secondary,
        textTertiary = Color(0xFF93939F))
}

/** One stable provider slot for both appearances. Never owns navigation or writes preferences. */
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun NotflixPresentationTheme(active: Boolean, content: @Composable () -> Unit) {
    val accent = NotflixAccent.selected
    val palette = if (active) remember(accent) { NotflixPresentationPalette.palette } else NuvioTheme.palette
    val colors = if (active) remember(accent) { NotflixPresentationPalette.colors } else NuvioTheme.colors
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
    val rawDensity = LocalDensity.current
    val uiScalePercent = if (active) NotflixUiScale.scalePercent else NotflixUiScale.DEFAULT_PERCENT
    val scaledDensity = remember(rawDensity, uiScalePercent) {
        if (uiScalePercent == NotflixUiScale.DEFAULT_PERCENT) rawDensity
        else Density(density = rawDensity.density * (uiScalePercent / 100f), fontScale = rawDensity.fontScale)
    }
    // Screens that size themselves from the screen in dp (hero, ambient, dialogs) must see the scaled size too.
    val rawConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val scaledConfiguration = remember(rawConfiguration, uiScalePercent) {
        if (uiScalePercent == NotflixUiScale.DEFAULT_PERCENT) rawConfiguration
        else android.content.res.Configuration(rawConfiguration).apply {
            screenWidthDp = rawConfiguration.screenWidthDp * 100 / uiScalePercent
            screenHeightDp = rawConfiguration.screenHeightDp * 100 / uiScalePercent
            smallestScreenWidthDp = rawConfiguration.smallestScreenWidthDp * 100 / uiScalePercent
            densityDpi = rawConfiguration.densityDpi * uiScalePercent / 100
        }
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        androidx.compose.ui.platform.LocalConfiguration provides scaledConfiguration,
        LocalNotflixControlColors provides active,
        LocalNuvioColors provides colors,
        LocalNuvioExtendedColors provides extended,
        LocalThemePalette provides palette,
        LocalNuvioFocusRingStyle provides focus
    ) {
        MaterialTheme(colorScheme = material, typography = MaterialTheme.typography, content = content)
    }
}
