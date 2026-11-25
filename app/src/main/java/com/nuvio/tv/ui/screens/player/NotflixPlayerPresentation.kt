package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec

/** Visual scope only. Does not select a player, own time or dispatch actions. */
internal val LocalNotflixPlayerChrome = staticCompositionLocalOf { false }

@Composable
internal fun playerPanelShape(original: Shape): Shape = if (LocalNotflixPresentation.current.active) RectangleShape else original

@Composable
internal fun playerPanelColor(original: Color): Color =
    if (LocalNotflixPresentation.current.active) NotflixVisualSpec.Surface.copy(alpha = .96f) else original

@Composable
internal fun NotflixPlayerStage(
    title: @Composable () -> Unit,
    transport: @Composable RowScope.() -> Unit,
    progress: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    bottomModifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalNotflixPlayerChrome provides true) {
        Box(Modifier.fillMaxSize().testTag("notflix_player_stage")) {
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 48.dp, vertical = 36.dp)
                .testTag("notflix_player_title")) { title() }
            Row(Modifier.align(Alignment.Center).testTag("notflix_player_transport"),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically,
                content = transport)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(bottomModifier)
                .padding(horizontal = 48.dp, vertical = 24.dp).testTag("notflix_player_bottom")) {
                progress()
                Spacer(Modifier.height(2.dp))
                secondary()
            }
        }
    }
}

/** Keep the provider slots stable on reversion; only Notflix changes the two existing typographies. */
@Composable
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
internal fun NotflixPlayerTypography(content: @Composable () -> Unit) {
    val active = LocalNotflixPresentation.current.active
    val tv = androidx.tv.material3.MaterialTheme.typography
    val material = androidx.compose.material3.MaterialTheme.typography
    val font = NotflixVisualSpec.BodyFont
    val tvType = remember(tv, active) { if (!active) tv else androidx.tv.material3.Typography(
        displayLarge = tv.displayLarge.copy(fontFamily = font), displayMedium = tv.displayMedium.copy(fontFamily = font),
        displaySmall = tv.displaySmall.copy(fontFamily = font), headlineLarge = tv.headlineLarge.copy(fontFamily = font),
        headlineMedium = tv.headlineMedium.copy(fontFamily = font), headlineSmall = tv.headlineSmall.copy(fontFamily = font),
        titleLarge = tv.titleLarge.copy(fontFamily = font), titleMedium = tv.titleMedium.copy(fontFamily = font),
        titleSmall = tv.titleSmall.copy(fontFamily = font), bodyLarge = tv.bodyLarge.copy(fontFamily = font),
        bodyMedium = tv.bodyMedium.copy(fontFamily = font), bodySmall = tv.bodySmall.copy(fontFamily = font),
        labelLarge = tv.labelLarge.copy(fontFamily = font), labelMedium = tv.labelMedium.copy(fontFamily = font),
        labelSmall = tv.labelSmall.copy(fontFamily = font)) }
    val materialType = remember(material, active) { if (!active) material else androidx.compose.material3.Typography(
        displayLarge = material.displayLarge.copy(fontFamily = font), displayMedium = material.displayMedium.copy(fontFamily = font),
        displaySmall = material.displaySmall.copy(fontFamily = font), headlineLarge = material.headlineLarge.copy(fontFamily = font),
        headlineMedium = material.headlineMedium.copy(fontFamily = font), headlineSmall = material.headlineSmall.copy(fontFamily = font),
        titleLarge = material.titleLarge.copy(fontFamily = font), titleMedium = material.titleMedium.copy(fontFamily = font),
        titleSmall = material.titleSmall.copy(fontFamily = font), bodyLarge = material.bodyLarge.copy(fontFamily = font),
        bodyMedium = material.bodyMedium.copy(fontFamily = font), bodySmall = material.bodySmall.copy(fontFamily = font),
        labelLarge = material.labelLarge.copy(fontFamily = font), labelMedium = material.labelMedium.copy(fontFamily = font),
        labelSmall = material.labelSmall.copy(fontFamily = font)) }
    androidx.tv.material3.MaterialTheme(typography = tvType) {
        androidx.compose.material3.MaterialTheme(typography = materialType, content = content)
    }
}
