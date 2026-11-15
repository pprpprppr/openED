package org.opened.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Wizard : Screen("wizard")
    object Settings : Screen("settings")
    object KnowledgeGraph : Screen("knowledge_graph")
    object FlashcardReview : Screen("flashcard_review")

    object MockTestList : Screen("mock_tests/{pathId}") {
        fun createRoute(pathId: String): String = "mock_tests/$pathId"
    }

    object TakeMockTest : Screen("take_mock_test/{testId}") {
        fun createRoute(testId: String): String = "take_mock_test/$testId"
    }

    object MockTestResult : Screen("mock_test_result/{testId}") {
        fun createRoute(testId: String): String = "mock_test_result/$testId"
    }

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
