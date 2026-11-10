package com.nuvio.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nuvio.tv.ui.screens.addon.AddonManagerScreen
import com.nuvio.tv.ui.screens.addon.CatalogOrderScreen

private const val CATALOG_ORDER_RETURN_TOKEN = "catalogOrderReturnToken"

/** Existing destinations with an entry-local return signal, never a profile preference. */
internal fun NavGraphBuilder.addonManagerDestinations(
    navController: NavHostController,
    showBuiltInHeader: Boolean = true,
    managerScreen: @Composable (Int, () -> Unit, () -> Unit, () -> Unit) -> Unit = { token, back, order, collections ->
        AddonManagerScreen(
            showBuiltInHeader = showBuiltInHeader,
            catalogOrderReturnToken = token,
            onBackPress = back,
            onNavigateToCatalogOrder = order,
            onNavigateToCollections = collections
        )
    },
    catalogOrderScreen: @Composable (() -> Unit) -> Unit = { back -> CatalogOrderScreen(onBackPress = back) }
) {
    composable(Screen.AddonManager.route) { entry ->
        val token by entry.savedStateHandle.getStateFlow(CATALOG_ORDER_RETURN_TOKEN, 0).collectAsState()
        managerScreen(
            token,
            { navController.popBackStack() },
            { navController.navigate(Screen.CatalogOrder.route) },
            { navController.navigate(Screen.Collections.route) }
        )
    }
    composable(Screen.CatalogOrder.route) {
        catalogOrderScreen {
            val origin = navController.previousBackStackEntry
            if (origin?.destination?.route == Screen.AddonManager.route) {
                val previous = origin.savedStateHandle.get<Int>(CATALOG_ORDER_RETURN_TOKEN) ?: 0
                origin.savedStateHandle[CATALOG_ORDER_RETURN_TOKEN] = previous + 1
            }
            navController.popBackStack()
        }
    }
}
