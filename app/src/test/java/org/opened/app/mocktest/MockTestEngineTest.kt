package org.opened.app.mocktest

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.ai.JsonRepairer
import org.opened.app.core.ai.MockTestBatchResponse
import org.opened.app.core.ai.MockTestQuestionItem
import org.opened.app.core.ai.Prompts
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.model.MockTestWithQuestions
import org.opened.app.ui.mocktest.MockTestResultUiState
import org.opened.app.ui.mocktest.ResultFilter

class MockTestEngineTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    @Test
    fun testExamCategories_haveValidProperties() {
        val entrance = ExamCategory.ENTRANCE
        assertTrue(entrance.displayName.contains("Entrance"))
        assertEquals(2.0f, entrance.defaultTimePerQuestionMinutes)

        val competitive = ExamCategory.COMPETITIVE
        assertTrue(competitive.displayName.contains("Competitive"))
        assertEquals(1.5f, competitive.defaultTimePerQuestionMinutes)

        val certification = ExamCategory.CERTIFICATION
        assertTrue(certification.displayName.contains("Certification"))
        assertEquals(1.5f, certification.defaultTimePerQuestionMinutes)

        val university = ExamCategory.UNIVERSITY
        assertTrue(university.displayName.contains("University"))
        assertEquals(2.0f, university.defaultTimePerQuestionMinutes)

        val general = ExamCategory.GENERAL
        assertEquals(1.5f, general.defaultTimePerQuestionMinutes)
    }

    @Test
    fun testMockTestBatchPrompt_containsCategoryGuidelines() {
        // Entrance Exam
        val entrancePrompt = Prompts.examMockTestBatchPrompt(
            courseTitle = "Classical Mechanics",
            courseTopic = "Physics",
            examCategory = "ENTRANCE",
            targetExamName = "JEE Advanced",
            batchTopicsSummary = "Module 1: Rotational Dynamics",
            batchNumber = 1,
            totalBatches = 3,
            questionCount = 10,
            startQuestionNumber = 1
        )
        assertTrue(entrancePrompt.contains("ENTRANCE EXAM RIGOR"))
        assertTrue(entrancePrompt.contains("JEE Advanced"))
        assertTrue(entrancePrompt.contains("LaTeX"))
        assertTrue(entrancePrompt.contains("BATCH 1 OF 3"))
        assertTrue(entrancePrompt.contains("Question 1 to 10"))

        // Competitive Exam
        val competitivePrompt = Prompts.examMockTestBatchPrompt(
            courseTitle = "Indian Polity",
            courseTopic = "Constitutional Law",
            examCategory = "COMPETITIVE",
            targetExamName = "UPSC Prelims Paper 1",
            batchTopicsSummary = "Module 2: Fundamental Rights",
            batchNumber = 2,
            totalBatches = 5,
            questionCount = 10,
            startQuestionNumber = 11
        )
        assertTrue(competitivePrompt.contains("COMPETITIVE CIVIL SERVICES"))
        assertTrue(competitivePrompt.contains("multi-statement"))
        assertTrue(competitivePrompt.contains("Assertion-Reason"))
        assertTrue(competitivePrompt.contains("Question 11 to 20"))

        // Certification Exam
        val certPrompt = Prompts.examMockTestBatchPrompt(
            courseTitle = "AWS Cloud Architecture",
            courseTopic = "Cloud Computing",
            examCategory = "CERTIFICATION",
            targetExamName = "AWS Solutions Architect Associate",
            batchTopicsSummary = "Module 3: VPC Networking & IAM",
            batchNumber = 3,
            totalBatches = 3,
            questionCount = 5,
            startQuestionNumber = 21
        )
        assertTrue(certPrompt.contains("INDUSTRY PROFESSIONAL CERTIFICATION"))
        assertTrue(certPrompt.contains("enterprise production scenarios"))
        assertTrue(certPrompt.contains("Question 21 to 25"))
    }

    @Test
    fun testBatchCalculationLogic_handlesVariousQuestionCounts() {
        fun calculateBatches(totalQuestions: Int, batchSize: Int = 10): List<Int> {
            val totalBatches = ((totalQuestions + batchSize - 1) / batchSize).coerceAtLeast(1)
            val batches = mutableListOf<Int>()
            var remaining = totalQuestions
            for (i in 0 until totalBatches) {
                val count = if (remaining >= batchSize) batchSize else remaining
                if (count > 0) batches.add(count)
                remaining -= count
            }
            return batches
        }

        assertEquals(listOf(10), calculateBatches(10))
        assertEquals(listOf(10, 10, 5), calculateBatches(25))
        assertEquals(listOf(10, 10, 10, 10, 10), calculateBatches(50))
        assertEquals(10, calculateBatches(100).size)
        assertEquals(100, calculateBatches(100).sum())
    }

    @Test
    fun testMockTestBatchResponse_serializationAndCleanJson() {
        val rawJson = """
        ```json
        {
          "questions": [
            {
              "topic": "Newtonian Gravity",
              "question": "What is the gravitational force between two masses at distance ${'$'}r${'$'}?",
              "options": [
                "${'$'}G \\frac{m_1 m_2}{r^2}${'$'}",
                "${'$'}G \\frac{m_1 m_2}{r}${'$'}",
                "${'$'}G \\frac{m_1 + m_2}{r^2}${'$'}",
                "Zero"
              ],
              "correctIndex": 0,
              "explanation": "Newton's law states ${'$'}F = G \\frac{m_1 m_2}{r^2}${'$'}. The inverse-square dependence is fundamental."
            }
          ]
        }
        ```
        """.trimIndent()

        val cleaned = JsonRepairer.cleanAndRepairJson(rawJson)
        val response = json.decodeFromString(MockTestBatchResponse.serializer(), cleaned)

        assertEquals(1, response.questions.size)
        val q = response.questions[0]
        assertEquals("Newtonian Gravity", q.topic)
        assertEquals(4, q.options.size)
        assertEquals(0, q.correctIndex)
        assertTrue(q.explanation.contains("Newton's law"))
    }

    @Test
    fun testMockTestQuestionEntity_optionsList_lazyParsing() {
        val options = listOf("Option 1", "Option 2", "Option 3", "Option 4")
        val optionsJson = MockTestQuestionEntity.createOptionsJson(options)

        val entity = MockTestQuestionEntity(
            id = "q-1",
            testId = "test-1",
            questionIndex = 0,
            topic = "Database Sharding",
            questionText = "Which sharding strategy prevents hot spots?",
            optionsJson = optionsJson,
            correctOptionIndex = 2,
            explanation = "Consistent hashing distributes keys evenly across virtual nodes."
        )

        assertEquals(4, entity.optionsList.size)
        assertEquals("Option 1", entity.optionsList[0])
        assertEquals("Option 3", entity.optionsList[2])
    }

    @Test
    fun testMockTestResultUiState_filteringAndTopicMastery() {
        val testEntity = MockTestEntity(
            id = "t1",
            pathId = "p1",
            title = "AWS Mock Test",
            examCategory = ExamCategory.CERTIFICATION,
            targetExamName = "AWS SAA-C03",
            totalQuestions = 4,
            timeLimitMinutes = 10,
            status = MockTestStatus.COMPLETED,
            score = 75,
            totalCorrect = 3,
            totalAnswered = 4
        )

        val questions = listOf(
            MockTestQuestionEntity("q1", "t1", 0, "Networking", "Q1?", "[]", 0, "exp1", userSelectedOption = 0), // Correct
            MockTestQuestionEntity("q2", "t1", 1, "Networking", "Q2?", "[]", 1, "exp2", userSelectedOption = 1), // Correct
            MockTestQuestionEntity("q3", "t1", 2, "Security", "Q3?", "[]", 2, "exp3", userSelectedOption = 0, isFlagged = true), // Incorrect, Flagged
            MockTestQuestionEntity("q4", "t1", 3, "Storage", "Q4?", "[]", 3, "exp4", userSelectedOption = 3) // Correct
        )

        val state = MockTestResultUiState(
            testId = "t1",
            testWithQuestions = MockTestWithQuestions(testEntity, questions),
            selectedFilter = ResultFilter.ALL
        )

        assertEquals(4, state.filteredQuestions.size)

        // Filter INCORRECT
        val incorrectState = state.copy(selectedFilter = ResultFilter.INCORRECT)
        assertEquals(1, incorrectState.filteredQuestions.size)
        assertEquals("q3", incorrectState.filteredQuestions[0].id)

        // Filter CORRECT
        val correctState = state.copy(selectedFilter = ResultFilter.CORRECT)
        assertEquals(3, correctState.filteredQuestions.size)

        // Filter FLAGGED
        val flaggedState = state.copy(selectedFilter = ResultFilter.FLAGGED)
        assertEquals(1, flaggedState.filteredQuestions.size)
        assertEquals("q3", flaggedState.filteredQuestions[0].id)

        // Topic Mastery
        val mastery = state.topicMasteryList
        assertEquals(3, mastery.size) // Networking, Security, Storage
        val securityTopic = mastery.firstOrNull { it.topic == "Security" }
        assertNotNull(securityTopic)
        assertEquals(0, securityTopic?.accuracyPercentage) // 0/1 correct

        val networkingTopic = mastery.firstOrNull { it.topic == "Networking" }
        assertNotNull(networkingTopic)
        assertEquals(100, networkingTopic?.accuracyPercentage) // 2/2 correct
    }
}
