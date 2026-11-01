package org.opened.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Wizard : Screen("wizard")
    object Settings : Screen("settings")
    object Workspace : Screen("workspace/{pathId}?chapterId={chapterId}") {
        fun createRoute(pathId: String, chapterId: String? = null): String {
            return if (chapterId != null) {
                "workspace/$pathId?chapterId=$chapterId"
            } else {
                "workspace/$pathId"
            }
        }
    }
}
