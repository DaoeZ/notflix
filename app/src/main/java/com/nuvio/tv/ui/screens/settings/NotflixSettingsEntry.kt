package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import com.nuvio.tv.ui.navigation.Screen

/** M66: the destination belongs to this back-stack entry, including process recreation. */
object NotflixSettingsEntry {
    private const val INTEGRATIONS = "notflixIntegrationsEntry"

    fun requestNotflixIntegrations(state: SavedStateHandle) {
        state[INTEGRATIONS] = true
    }

    fun isNotflixIntegrationsRequested(state: SavedStateHandle): Boolean = state[INTEGRATIONS] ?: false
}

internal fun NavHostController.openNotflixIntegrations() {
    navigate(Screen.Settings.route) { launchSingleTop = true }
    currentBackStackEntry?.savedStateHandle?.let(NotflixSettingsEntry::requestNotflixIntegrations)
}
