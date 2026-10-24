package com.example.auraplay.ui

import androidx.navigation.NavController

/** Tab selection always opens that tab's root, never a saved child such as Settings. */
fun NavController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            // Reset Home on explicit selection; keep it as Back's destination for other tabs.
            inclusive = route == graph.startDestinationRoute
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}
