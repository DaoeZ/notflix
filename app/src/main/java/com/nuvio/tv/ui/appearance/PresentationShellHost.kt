package com.nuvio.tv.ui.appearance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalSidebarExpanded

/** Chrome owns its controls/modifiers; it never owns or invokes a navigation host. */
data class PresentationShell(
    val rootModifier: Modifier = Modifier,
    val contentModifier: Modifier = Modifier,
    val sidebarExpanded: Boolean = false,
    val chrome: @Composable BoxScope.() -> Unit = {}
)

/**
 * Content has one permanent composition slot. Changing a shell replaces only
 * chrome/modifiers/locals, not the NavHost, its controller or its session owner.
 * M2 can supply top chrome here without moving or copying navigation content.
 */
@Composable
fun PresentationShellHost(
    shell: PresentationShell,
    contentFocusRequester: FocusRequester,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().then(shell.rootModifier)) {
        Box(Modifier.fillMaxSize().then(shell.contentModifier)) {
            CompositionLocalProvider(
                LocalSidebarExpanded provides shell.sidebarExpanded,
                LocalContentFocusRequester provides contentFocusRequester
            ) { content() }
        }
        shell.chrome(this)
    }
}
