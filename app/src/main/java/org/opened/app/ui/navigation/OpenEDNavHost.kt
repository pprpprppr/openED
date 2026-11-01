package org.opened.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.opened.app.ui.home.HomeScreen
import org.opened.app.ui.settings.SettingsScreen
import org.opened.app.ui.wizard.DiagnosticWizardScreen
import org.opened.app.ui.workspace.CourseWorkspaceScreen

@Composable
fun OpenEDNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToWizard = {
                    navController.navigate(Screen.Wizard.route)
                },
                onNavigateToWorkspace = { pathId, chapterId ->
                    navController.navigate(Screen.Workspace.createRoute(pathId, chapterId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Wizard.route) {
            DiagnosticWizardScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCourseCreated = { pathId ->
                    navController.navigate(Screen.Workspace.createRoute(pathId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.Workspace.route,
            arguments = listOf(
                navArgument("pathId") { type = NavType.StringType },
                navArgument("chapterId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val pathId = backStackEntry.arguments?.getString("pathId").orEmpty()
            val chapterId = backStackEntry.arguments?.getString("chapterId")

            CourseWorkspaceScreen(
                pathId = pathId,
                initialChapterId = chapterId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
