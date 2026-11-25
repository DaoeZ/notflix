package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import com.nuvio.tv.ui.navigation.Screen

/** M66: the destination belongs to this back-stack entry, including process recreation. */
object NotflixSettingsEntry {
    private const val INTEGRATIONS = "notflixIntegrationsEntry"
    internal const val DESTINATION = "notflixSettingsDestination"

    fun requestNotflixIntegrations(state: SavedStateHandle) {
        state[INTEGRATIONS] = true
    }

    fun isNotflixIntegrationsRequested(state: SavedStateHandle): Boolean = state[INTEGRATIONS] ?: false

    fun requestDestination(state: SavedStateHandle, destination: String) {
        if (NotflixSettingsPage.entries.none { it.name == destination }) return
        val previous = state.get<String>(DESTINATION)?.substringBefore(':')?.toLongOrNull() ?: 0L
        state[DESTINATION] = "${previous + 1}:$destination"
    }
}

internal fun NavHostController.openNotflixIntegrations() {
    navigate(Screen.Settings.route) { launchSingleTop = true }
    currentBackStackEntry?.savedStateHandle?.let(NotflixSettingsEntry::requestNotflixIntegrations)
}
