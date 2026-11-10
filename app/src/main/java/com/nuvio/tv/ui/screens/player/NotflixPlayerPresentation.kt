package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Visual scope only. Does not select a player, own time or dispatch actions. */
internal val LocalNotflixPlayerChrome = staticCompositionLocalOf { false }

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
                .padding(horizontal = 8.dp, vertical = 18.dp).testTag("notflix_player_bottom")) {
                progress()
                Spacer(Modifier.height(2.dp))
                secondary()
            }
        }
    }
}
