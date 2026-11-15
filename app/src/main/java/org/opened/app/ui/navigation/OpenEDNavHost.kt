package org.opened.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.opened.app.ui.flashcards.FlashcardReviewScreen
import org.opened.app.ui.graph.KnowledgeGraphScreen
import org.opened.app.ui.home.HomeScreen
import org.opened.app.ui.mocktest.MockTestListScreen
import org.opened.app.ui.mocktest.MockTestResultScreen
import org.opened.app.ui.mocktest.TakeMockTestScreen
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
                onNavigateToWizard = { navController.navigate(Screen.Wizard.route) },
                onNavigateToWorkspace = { pathId, chapterId ->
                    navController.navigate(Screen.Workspace.createRoute(pathId, chapterId))
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToFlashcards = { navController.navigate(Screen.FlashcardReview.route) },
                onNavigateToGraph = { navController.navigate(Screen.KnowledgeGraph.route) },
                onNavigateToMockTests = { pathId ->
                    navController.navigate(Screen.MockTestList.createRoute(pathId))
                }
            )
        }

        composable(Screen.Wizard.route) {
            DiagnosticWizardScreen(
                onNavigateBack = { navController.popBackStack() },
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMockTests = {
                    navController.navigate(Screen.MockTestList.createRoute(pathId))
                }
            )
        }

        composable(
            route = Screen.MockTestList.route,
            arguments = listOf(navArgument("pathId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pathId = backStackEntry.arguments?.getString("pathId").orEmpty()
            MockTestListScreen(
                pathId = pathId,
                onNavigateBack = { navController.popBackStack() },
                onTakeTest = { testId ->
                    navController.navigate(Screen.TakeMockTest.createRoute(testId))
                },
                onViewResult = { testId ->
                    navController.navigate(Screen.MockTestResult.createRoute(testId))
                }
            )
        }

        composable(
            route = Screen.TakeMockTest.route,
            arguments = listOf(navArgument("testId") { type = NavType.StringType })
        ) { backStackEntry ->
            val testId = backStackEntry.arguments?.getString("testId").orEmpty()
            TakeMockTestScreen(
                testId = testId,
                onNavigateBack = { navController.popBackStack() },
                onTestSubmitted = { completedTestId ->
                    navController.navigate(Screen.MockTestResult.createRoute(completedTestId)) {
                        popUpTo(Screen.TakeMockTest.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.MockTestResult.route,
            arguments = listOf(navArgument("testId") { type = NavType.StringType })
        ) { backStackEntry ->
            val testId = backStackEntry.arguments?.getString("testId").orEmpty()
            MockTestResultScreen(
                testId = testId,
                onNavigateBack = { navController.popBackStack() },
                onRetakeTest = { retakeTestId ->
                    navController.navigate(Screen.TakeMockTest.createRoute(retakeTestId)) {
                        popUpTo(Screen.MockTestResult.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.FlashcardReview.route) {
            FlashcardReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.KnowledgeGraph.route) {
            KnowledgeGraphScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChapter = { pathId, chapterId ->
                    navController.navigate(Screen.Workspace.createRoute(pathId, chapterId))
                }
            )
        }
    }
}
