package org.opened.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.QuestionType
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.ChapterStatus
import java.io.File

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
}
