package org.opened.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.MultiAiClient
import org.opened.app.core.ai.provider.AiCapabilityRegistry
import org.opened.app.core.ai.provider.TokenUsageTracker
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.QuestionType
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.core.platform.ApiKeyStore
import org.opened.app.core.platform.UserPreferencesStore
import org.opened.app.ui.mocktest.TakeMockTestViewModel
import org.opened.app.ui.wizard.DiagnosticWizardViewModel
import java.io.File
import java.util.concurrent.CompletableFuture

class DesktopCoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sqlite repository persists study paths modules chapters and mock tests`() = runTest {
        val dbFile = File(tempFolder.root, "test_opened.db")
        val repo = DesktopOpenEDRepository(dbFile.absolutePath)

        val path = StudyPathEntity(
            id = "path-1",
            title = "Quantum Mechanics",
            topic = "Physics",
            userLevel = "INTERMEDIATE",
            learningGoalsJson = "[\"Understand wavefunctions\"]",
            customPrompt = "Use Dirac bra-ket notation."
        )
        val module = ModuleEntity(
            id = "mod-1",
            pathId = "path-1",
            title = "Wave Equations",
            orderIndex = 0,
            summary = "Schrodinger equation"
        )
        val chapter = ChapterEntity(
            id = "chap-1",
            moduleId = "mod-1",
            pathId = "path-1",
            title = "Time-Independent Equation",
            orderIndex = 0
        )

        repo.saveStudyPathWithModules(path, listOf(module to listOf(chapter)))
        repo.updateChapterContent("chap-1", "# Time-Independent Equation\n\n\$\$\\hat{H}\\psi = E\\psi\$\$", ChapterStatus.COMPLETED)

        val loaded = repo.getStudyPathWithModules("path-1")
        assertNotNull(loaded)
        assertEquals("Quantum Mechanics", loaded!!.studyPath.title)
        assertEquals("Use Dirac bra-ket notation.", loaded.studyPath.customPrompt)
        assertEquals(1, loaded.modules.size)
        assertEquals(ChapterStatus.COMPLETED, loaded.allChapters.first().status)
        assertEquals(1.0f, loaded.studyPath.progress, 0.01f)

        val test = MockTestEntity(
            id = "test-1",
            pathId = "path-1",
            title = "Quantum Midterm",
            examCategory = ExamCategory.UNIVERSITY,
            targetExamName = "PHYS 301",
            totalQuestions = 1,
            timeLimitMinutes = 15
        )
        val q = MockTestQuestionEntity(
            id = "q-1",
            testId = "test-1",
            questionIndex = 0,
            topic = "Operators",
            questionText = "Which operators are Hermitian?",
            optionsJson = MockTestQuestionEntity.createOptionsJson(listOf("Hamiltonian", "Momentum", "Non-Hermitian A", "Position")),
            correctOptionIndex = 0,
            explanation = "Observables correspond to Hermitian operators.",
            questionType = QuestionType.MULTI_SELECT,
            correctAnswers = listOf("Hamiltonian", "Momentum", "Position")
        )
        repo.saveMockTestWithQuestions(test, listOf(q))
        repo.answerMockTestQuestion("q-1", 0, listOf("Hamiltonian", "Momentum", "Position"))

        val withQ = repo.getMockTestWithQuestions("test-1")
        assertNotNull(withQ)
        assertTrue(withQ!!.questions.first().isCorrect)

        val bundle = repo.exportDatabase()
        assertEquals(1, bundle.studyPaths.size)
        assertEquals(1, bundle.mockTests.size)

        val dbFile2 = File(tempFolder.root, "test_opened_restored.db")
        val repo2 = DesktopOpenEDRepository(dbFile2.absolutePath)
        repo2.importDatabase(bundle, overwrite = true)

        val restoredPaths = repo2.getAllStudyPathsFlow().first()
        assertEquals(1, restoredPaths.size)
        assertEquals("Quantum Mechanics", restoredPaths.first().studyPath.title)
        assertFalse(restoredPaths.first().allChapters.first().markdownContent.isBlank())
    }

    @Test
    fun `desktop theme mode parses dark white existing and defaults safely`() {
        assertEquals(DesktopThemeMode.MODERN_DARK, DesktopThemeMode.fromId("dark"))
        assertEquals(DesktopThemeMode.MODERN_WHITE, DesktopThemeMode.fromId("WHITE"))
        assertEquals(DesktopThemeMode.EXISTING, DesktopThemeMode.fromId("existing"))
        assertEquals(DesktopThemeMode.MODERN_DARK, DesktopThemeMode.fromId(null))
        assertEquals(DesktopThemeMode.MODERN_DARK, DesktopThemeMode.fromId("unknown"))
    }

    @Test
    fun `updateMockTest preserves questions and single and multi answers in sqlite`() = runTest {
        val dbFile = File(tempFolder.root, "test_cascade_fix.db")
        val repo = DesktopOpenEDRepository(dbFile.absolutePath)

        val path = StudyPathEntity(
            id = "path-c1",
            title = "Distributed Systems",
            topic = "CS",
            userLevel = "ADVANCED",
            learningGoalsJson = "[]"
        )
        val module = ModuleEntity("mod-c1", "path-c1", "Consensus", 0, "Paxos & Raft")
        val chapter = ChapterEntity("chap-c1", "mod-c1", "path-c1", "Raft", 0)
        repo.saveStudyPathWithModules(path, listOf(module to listOf(chapter)))

        val test = MockTestEntity(
            id = "test-c1",
            pathId = "path-c1",
            title = "Raft Exam",
            examCategory = ExamCategory.UNIVERSITY,
            targetExamName = "CS 410",
            totalQuestions = 2,
            timeLimitMinutes = 20,
            status = MockTestStatus.NOT_STARTED
        )
        val qSingle = MockTestQuestionEntity(
            id = "q-single",
            testId = "test-c1",
            questionIndex = 0,
            topic = "Leader Election",
            questionText = "Which timeout triggers candidate state?",
            optionsJson = MockTestQuestionEntity.createOptionsJson(listOf("Election timeout", "Heartbeat", "Commit index", "Snapshot")),
            correctOptionIndex = 0,
            explanation = "Election timeout starts a new term.",
            questionType = QuestionType.SINGLE_CHOICE,
            correctAnswers = listOf("Election timeout")
        )
        val qMulti = MockTestQuestionEntity(
            id = "q-multi",
            testId = "test-c1",
            questionIndex = 1,
            topic = "Safety",
            questionText = "Select all Raft safety properties",
            optionsJson = MockTestQuestionEntity.createOptionsJson(listOf("Election Safety", "Log Matching", "Byzantine Tolerance", "Leader Completeness")),
            correctOptionIndex = 0,
            explanation = "Raft ensures Election Safety, Log Matching, and Leader Completeness.",
            questionType = QuestionType.MULTI_SELECT,
            correctAnswers = listOf("Election Safety", "Log Matching", "Leader Completeness")
        )
        repo.saveMockTestWithQuestions(test, listOf(qSingle, qMulti))

        // Regression: updating test status must NOT cascade-delete mock_test_questions
        repo.updateMockTest(test.copy(status = MockTestStatus.IN_PROGRESS))
        val afterStatusUpdate = repo.getMockTestWithQuestions("test-c1")
        assertNotNull(afterStatusUpdate)
        assertEquals(2, afterStatusUpdate!!.questions.size)

        // Regression: answering single-choice & multi-select then updating test metadata must keep both
        repo.answerMockTestQuestion("q-single", 0, listOf("Election timeout"))
        repo.answerMockTestQuestion("q-multi", 0, listOf("Election Safety", "Log Matching", "Leader Completeness"))
        repo.updateMockTest(test.copy(status = MockTestStatus.IN_PROGRESS, totalAnswered = 2))

        val afterAnswers = repo.getMockTestWithQuestionsFlow("test-c1").first()
        assertNotNull(afterAnswers)
        assertEquals(2, afterAnswers!!.questions.size)
        assertEquals(listOf(0), afterAnswers.questions[0].effectiveSelectedIndices)
        assertEquals(listOf(0, 1, 3), afterAnswers.questions[1].effectiveSelectedIndices)
        assertTrue(afterAnswers.questions[0].isCorrect)
        assertTrue(afterAnswers.questions[1].isCorrect)

        // Updating parent study path must also preserve modules, chapters, and mock tests
        repo.saveStudyPath(path.copy(title = "Distributed Systems v2"))
        val afterPathSave = repo.getStudyPathWithModules("path-c1")
        assertNotNull(afterPathSave)
        assertEquals(1, afterPathSave!!.allChapters.size)
        assertEquals(2, repo.getQuestionsForTest("test-c1").size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `TakeMockTestViewModel selects and persists single and multi options with sqlite repo`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val dbFile = File(tempFolder.root, "test_vm_select.db")
            val repo = DesktopOpenEDRepository(dbFile.absolutePath)
            val engine = createTestAiEngine(repo)

            val path = StudyPathEntity("path-vm", "OS", "Kernel", "INTERMEDIATE", "[]")
            val mod = ModuleEntity("mod-vm", "path-vm", "Memory", 0, "Virtual memory")
            val chap = ChapterEntity("chap-vm", "mod-vm", "path-vm", "Paging", 0)
            repo.saveStudyPathWithModules(path, listOf(mod to listOf(chap)))

            val test = MockTestEntity(
                id = "test-vm",
                pathId = "path-vm",
                title = "OS Exam",
                examCategory = ExamCategory.UNIVERSITY,
                targetExamName = "CS 320",
                totalQuestions = 2,
                timeLimitMinutes = 15,
                status = MockTestStatus.NOT_STARTED
            )
            val q1 = MockTestQuestionEntity(
                id = "vm-q1",
                testId = "test-vm",
                questionIndex = 0,
                topic = "Paging",
                questionText = "Which structure caches page table translations?",
                optionsJson = MockTestQuestionEntity.createOptionsJson(listOf("MMU", "TLB", "L1d", "DMA")),
                correctOptionIndex = 1,
                explanation = "TLB caches virtual-to-physical translations.",
                questionType = QuestionType.SINGLE_CHOICE,
                correctAnswers = listOf("TLB")
            )
            val q2 = MockTestQuestionEntity(
                id = "vm-q2",
                testId = "test-vm",
                questionIndex = 1,
                topic = "Paging",
                questionText = "Select all valid page replacement algorithms",
                optionsJson = MockTestQuestionEntity.createOptionsJson(listOf("LRU", "FIFO", "Round Robin CPU", "Clock")),
                correctOptionIndex = 0,
                explanation = "LRU, FIFO, and Clock are page replacement policies.",
                questionType = QuestionType.MULTI_SELECT,
                correctAnswers = listOf("LRU", "FIFO", "Clock")
            )
            repo.saveMockTestWithQuestions(test, listOf(q1, q2))

            val vm = TakeMockTestViewModel(repo, engine)
            vm.loadTest("test-vm")

            withTimeout(5000L) {
                while (vm.uiState.value.isLoading || vm.uiState.value.totalQuestions < 2) {
                    delay(10L)
                }
            }

            // Select single choice option (index 1 -> "TLB")
            vm.selectOption(1)
            assertEquals(listOf(1), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)
            assertEquals(1, vm.uiState.value.answeredCount)

            // Wait for DB persistence and reactive flow emissions, verify selection sticks!
            withTimeout(5000L) {
                while (repo.getMockTestWithQuestions("test-vm")?.questions?.getOrNull(0)?.effectiveSelectedIndices != listOf(1)) {
                    delay(10L)
                }
            }
            delay(60L)
            assertEquals(listOf(1), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)

            // Switch single choice option to index 2, then back to index 1
            vm.selectOption(2)
            assertEquals(listOf(2), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)
            vm.selectOption(1)
            assertEquals(listOf(1), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)

            // Move to multi-select question and rapidly select multiple options (0, 1, 3)
            vm.nextQuestion()
            assertEquals(1, vm.uiState.value.currentQuestionIndex)
            vm.selectOption(0)
            vm.selectOption(1)
            vm.selectOption(3)
            assertEquals(listOf(0, 1, 3), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)

            withTimeout(5000L) {
                while (repo.getMockTestWithQuestions("test-vm")?.questions?.getOrNull(1)?.effectiveSelectedIndices != listOf(0, 1, 3)) {
                    delay(10L)
                }
            }
            delay(60L)
            assertEquals(listOf(0, 1, 3), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)

            // Toggle option 1 off and back on
            vm.selectOption(1)
            assertEquals(listOf(0, 3), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)
            vm.selectOption(1)
            assertEquals(listOf(0, 1, 3), vm.uiState.value.currentQuestion?.effectiveSelectedIndices)

            // Submit and verify score
            val completedId = CompletableFuture<String>()
            vm.submitTest { id -> completedId.complete(id) }
            withTimeout(5000L) {
                while (!completedId.isDone) {
                    delay(10L)
                }
            }
            assertEquals("test-vm", completedId.get())

            val graded = repo.getMockTestWithQuestions("test-vm")
            assertNotNull(graded)
            assertEquals(MockTestStatus.COMPLETED, graded!!.test.status)
            assertEquals(100, graded.test.score)
            assertEquals(2, graded.test.totalCorrect)
            assertEquals(2, graded.test.totalAnswered)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `DiagnosticWizardViewModel supports single and multi option selection`() {
        val dbFile = File(tempFolder.root, "test_wizard.db")
        val repo = DesktopOpenEDRepository(dbFile.absolutePath)
        val engine = createTestAiEngine(repo)
        val prefs = InMemoryPrefsStore()
        val vm = DiagnosticWizardViewModel(engine, prefs)

        // Single select replaces selection
        vm.toggleAnswerOption("q1", "Beginner", isMultiSelect = false)
        assertEquals(listOf("Beginner"), vm.uiState.value.selectedAnswers["q1"])
        vm.toggleAnswerOption("q1", "Intermediate", isMultiSelect = false)
        assertEquals(listOf("Intermediate"), vm.uiState.value.selectedAnswers["q1"])

        // Multi-select toggles multiple options independently
        vm.toggleAnswerOption("q2", "Theory", isMultiSelect = true)
        vm.toggleAnswerOption("q2", "Labs", isMultiSelect = true)
        assertEquals(listOf("Theory", "Labs"), vm.uiState.value.selectedAnswers["q2"])
        vm.toggleAnswerOption("q2", "Theory", isMultiSelect = true)
        assertEquals(listOf("Labs"), vm.uiState.value.selectedAnswers["q2"])
    }

    private fun createTestAiEngine(repo: DesktopOpenEDRepository): AiLearningEngine {
        val prefs = InMemoryPrefsStore()
        val keyStore = InMemoryApiKeyStore()
        val registry = AiCapabilityRegistry(null, null, null, null, TokenUsageTracker())
        val client = MultiAiClient(registry, keyStore, prefs)
        return AiLearningEngine(client, repo, prefs)
    }

    private class InMemoryPrefsStore : UserPreferencesStore {
        private val flow = MutableStateFlow(UserPreferences())
        override val userPreferencesFlow: Flow<UserPreferences> = flow
        override suspend fun setSelectedProvider(provider: String) {}
        override suspend fun setSelectedModel(model: String) {}
        override suspend fun setSelectionMode(mode: String) {}
        override suspend fun setCustomBaseUrl(url: String) {}
        override suspend fun setCustomSystemInstructions(instructions: String) {}
        override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
        override suspend fun setLastBackupTimestamp(timestamp: Long) {}
        override suspend fun setBackupAccountEmail(email: String?) {}
        override suspend fun setBackupDriveFileId(fileId: String?) {}
        override suspend fun setLearningLanguage(language: String) {}
        override suspend fun setBilingualTerminologyEnabled(enabled: Boolean) {}
        override suspend fun setHighContrastEnabled(enabled: Boolean) {}
        override suspend fun setReducedMotionEnabled(enabled: Boolean) {}
        override suspend fun setSyncProviderId(providerId: String) {}
        override suspend fun setWebDavConfig(url: String, username: String) {}
        override suspend fun setLinkedBackupUri(uri: String?) {}
    }

    private class InMemoryApiKeyStore : ApiKeyStore {
        override val activeApiKey: StateFlow<String?> = MutableStateFlow(null)
        override val activeModel: StateFlow<String> = MutableStateFlow("gemini-3.8-flash")
        override fun warmUp() {}
        override fun getApiKeyForProvider(provider: AiProvider): String? = null
        override fun saveApiKeyForProvider(provider: AiProvider, apiKey: String) {}
        override fun clearApiKeyForProvider(provider: AiProvider) {}
        override fun hasApiKeyForProvider(provider: AiProvider): Boolean = false
        override fun getModelForProvider(provider: AiProvider): String = ""
        override fun saveModelForProvider(provider: AiProvider, model: String) {}
        override fun getCustomBaseUrl(): String = ""
        override fun saveCustomBaseUrl(url: String) {}
        override fun getApiKey(): String? = null
        override fun saveApiKey(apiKey: String) {}
        override fun clearApiKey() {}
        override fun hasApiKey(): Boolean = false
    }
}
