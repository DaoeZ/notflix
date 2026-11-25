package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.nuvio.tv.ui.navigation.Screen

/** Replaces only the compatibility entry; Back cannot re-enter an alias and redirect in a loop. */
@Composable
internal fun RedirectNotflixSettings(nav: NavHostController, legacyRoute: String, page: NotflixSettingsPage) {
    LaunchedEffect(legacyRoute, page) {
        if (nav.currentBackStackEntry?.destination?.route != legacyRoute) return@LaunchedEffect
        nav.navigate(Screen.Settings.route) {
            popUpTo(legacyRoute) { inclusive = true }
            launchSingleTop = true
        }
        nav.currentBackStackEntry?.takeIf { it.destination.route == Screen.Settings.route }?.savedStateHandle
            ?.let { NotflixSettingsEntry.requestDestination(it, page.name) }
    }
}
