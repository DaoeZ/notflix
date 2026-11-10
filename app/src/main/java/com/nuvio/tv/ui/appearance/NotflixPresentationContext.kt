package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

data class NotflixPresentation(
    val active: Boolean = false,
    val navigation: NotflixNavigationState? = null,
    val reduceHomeMotion: Boolean = false
)
val LocalNotflixPresentation = staticCompositionLocalOf { NotflixPresentation() }
val LocalAppearanceViewModel = staticCompositionLocalOf<AppearanceViewModel?> { null }

/** Session-only focus bridge. Never owns routes, data or a second controller. */
class NotflixNavigationState {
    var topFocused by mutableStateOf(false)
    var homeBrowsing by mutableStateOf(false)
    // Chrome backdrop follows actual Home scroll placement, independently of TV row density.
    var homeAtTop by mutableStateOf(true)
    // La vista grande del tráiler oculta la barra superior para ocupar toda la pantalla.
    var immersive by mutableStateOf(false)
    var interacted = false
    var selectedRoute: String? = null
    val topRequesters = mutableMapOf<String, FocusRequester>()
    var topOrder = emptyList<String>()
    var homeDown: (() -> Unit)? = null
    var homeEntry: (() -> Unit)? = null
    var homeCancelFocus: (() -> Unit)? = null
    var homeNeedsFocusRepair: (() -> Boolean)? = null

    fun focusTop() {
        topFocused = true
        homeBrowsing = false
        homeEntry?.invoke()
        selectedRoute?.let { route -> topRequesters[route]?.let { runCatching { it.requestFocus() } } }
    }
}
