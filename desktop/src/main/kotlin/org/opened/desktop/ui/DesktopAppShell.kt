package org.opened.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import org.opened.app.ui.navigation.Screen
import org.opened.desktop.DesktopAppContainer
import org.opened.desktop.DesktopThemeMode
import org.opened.desktop.ui.components.DesktopThemeSwitcher
import org.opened.desktop.ui.screens.DesktopFlashcardReviewScreen
import org.opened.desktop.ui.screens.DesktopHomeScreen
import org.opened.desktop.ui.screens.DesktopInteractiveLabScreen
import org.opened.desktop.ui.screens.DesktopKnowledgeGraphScreen
import org.opened.desktop.ui.screens.DesktopMockTestListScreen
import org.opened.desktop.ui.screens.DesktopMockTestResultScreen
import org.opened.desktop.ui.screens.DesktopSettingsScreen
import org.opened.desktop.ui.screens.DesktopTakeMockTestScreen
import org.opened.desktop.ui.screens.DesktopWizardScreen
import org.opened.desktop.ui.screens.DesktopWorkspaceScreen
import org.opened.desktop.ui.theme.LocalDesktopTokens

private const val ROUTE_DESKTOP_LAB = "desktop_lab"
private const val ROUTE_GLOBAL_MOCK_TESTS = "mock_tests/"

@Composable
fun DesktopAppShell(
    container: DesktopAppContainer,
    navController: NavHostController,
    appIcon: Painter,
    themeMode: DesktopThemeMode,
    onSelectThemeMode: (DesktopThemeMode) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val dueFlashcardCount by container.repository.getDueFlashcardCount().collectAsState(initial = 0)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = false }
            launchSingleTop = true
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(tokens.canvas)) {
        // Persistent Modern Desktop Sidebar Rail
        Column(
            modifier = Modifier
                .width(244.dp)
                .fillMaxHeight()
                .background(tokens.sidebar)
                .padding(14.dp)
        ) {
            // Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { navigateTopLevel(Screen.Home.route) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = appIcon,
                    contentDescription = "openED",
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "openED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "PC Knowledge Studio",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Create Course Action
            Button(
                onClick = { navigateTopLevel(Screen.Wizard.route) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Course", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "WORKSPACE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SidebarNavItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = "Courses",
                    selected = currentRoute == Screen.Home.route || currentRoute.startsWith("workspace/"),
                    onClick = { navigateTopLevel(Screen.Home.route) }
                )
                SidebarNavItem(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    label = "Exam Prep",
                    selected = currentRoute.startsWith("mock_tests") || currentRoute.startsWith("take_mock_test") || currentRoute.startsWith("mock_test_result"),
                    onClick = { navigateTopLevel(Screen.MockTestList.createRoute("")) }
                )
                SidebarNavItem(
                    icon = Icons.Default.BubbleChart,
                    label = "Knowledge Graph",
                    selected = currentRoute == Screen.KnowledgeGraph.route,
                    onClick = { navigateTopLevel(Screen.KnowledgeGraph.route) }
                )
                SidebarNavItem(
                    icon = Icons.Default.Style,
                    label = "Flashcards",
                    selected = currentRoute == Screen.FlashcardReview.route,
                    badgeCount = dueFlashcardCount,
                    onClick = { navigateTopLevel(Screen.FlashcardReview.route) }
                )
                SidebarNavItem(
                    icon = Icons.Default.Science,
                    label = "Interactive Lab",
                    selected = currentRoute == ROUTE_DESKTOP_LAB,
                    onClick = { navigateTopLevel(ROUTE_DESKTOP_LAB) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = tokens.border)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            DesktopThemeSwitcher(
                currentMode = themeMode,
                onSelectMode = onSelectThemeMode
            )

            Spacer(modifier = Modifier.height(12.dp))

            SidebarNavItem(
                icon = Icons.Default.Settings,
                label = "Settings & Backup",
                selected = currentRoute == Screen.Settings.route,
                onClick = { navigateTopLevel(Screen.Settings.route) }
            )
        }

        // Hairline Sidebar Divider
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.border))

        // Main Content Frame
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    DesktopHomeScreen(
                        onNavigateToWizard = { navController.navigate(Screen.Wizard.route) },
                        onNavigateToWorkspace = { pathId, chapterId ->
                            navController.navigate(Screen.Workspace.createRoute(pathId, chapterId))
                        },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToFlashcards = { navController.navigate(Screen.FlashcardReview.route) },
                        onNavigateToMockTests = { pathId ->
                            navController.navigate(Screen.MockTestList.createRoute(pathId))
                        },
                        onOpenLab = { navController.navigate(ROUTE_DESKTOP_LAB) }
                    )
                }

                composable(Screen.Wizard.route) {
                    DesktopWizardScreen(
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
                    DesktopWorkspaceScreen(
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
                    DesktopMockTestListScreen(
                        pathId = pathId,
                        onNavigateBack = if (pathId.isNotBlank()) ({ navController.popBackStack() }) else null,
                        onTakeTest = { testId ->
                            navController.navigate(Screen.TakeMockTest.createRoute(testId))
                        },
                        onViewResult = { testId ->
                            navController.navigate(Screen.MockTestResult.createRoute(testId))
                        }
                    )
                }

                composable(ROUTE_GLOBAL_MOCK_TESTS) {
                    DesktopMockTestListScreen(
                        pathId = "",
                        onNavigateBack = null,
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
                    DesktopTakeMockTestScreen(
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
                    DesktopMockTestResultScreen(
                        testId = testId,
                        onNavigateBack = { navController.popBackStack() },
                        onRetakeTest = { retakeId ->
                            navController.navigate(Screen.TakeMockTest.createRoute(retakeId)) {
                                popUpTo(Screen.MockTestResult.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.FlashcardReview.route) {
                    DesktopFlashcardReviewScreen(
                        onNavigateBack = { navigateTopLevel(Screen.Home.route) }
                    )
                }

                composable(Screen.KnowledgeGraph.route) {
                    DesktopKnowledgeGraphScreen(
                        onNavigateToChapter = { pathId, chapterId ->
                            navController.navigate(Screen.Workspace.createRoute(pathId, chapterId))
                        }
                    )
                }

                composable(ROUTE_DESKTOP_LAB) {
                    DesktopInteractiveLabScreen()
                }

                composable(Screen.Settings.route) {
                    DesktopSettingsScreen(
                        currentThemeMode = themeMode,
                        onSelectThemeMode = onSelectThemeMode,
                        onNavigateBack = { navigateTopLevel(Screen.Home.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) tokens.accentSoft else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, tokens.accent.copy(alpha = 0.45f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) tokens.accent else tokens.textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) tokens.accent else tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (badgeCount > 0) {
                Badge(
                    containerColor = tokens.accent,
                    contentColor = Color.White
                ) {
                    Text(badgeCount.toString(), fontSize = 10.sp)
                }
            }
        }
    }
}
