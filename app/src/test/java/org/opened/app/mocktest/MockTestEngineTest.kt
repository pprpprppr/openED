package org.opened.app.mocktest

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.ai.AiLearningEngine
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
    fun testDynamicBatchCalculation_speedsUpGeneration() {
        fun calculateDynamicBatches(totalQuestions: Int): List<Int> {
            val batchSize = when {
                totalQuestions <= 15 -> totalQuestions
                totalQuestions <= 30 -> 15
                else -> 20
            }
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

        // <= 15: single batch (1 round trip instead of 2)
        assertEquals(listOf(5), calculateDynamicBatches(5))
        assertEquals(listOf(10), calculateDynamicBatches(10))
        assertEquals(listOf(15), calculateDynamicBatches(15))

        // <= 30: 15-question batches (2 round trips for 20/25/30 instead of 3)
        assertEquals(listOf(15, 5), calculateDynamicBatches(20))
        assertEquals(listOf(15, 10), calculateDynamicBatches(25))
        assertEquals(listOf(15, 15), calculateDynamicBatches(30))

        // > 30: 20-question batches (e.g. 50 questions is 3 batches: 20, 20, 10 instead of 5 batches)
        assertEquals(listOf(20, 20, 10), calculateDynamicBatches(50))
        assertEquals(5, calculateDynamicBatches(100).size) // 5 batches instead of 10
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

    @Test
    fun testParseMockTestBatchFlexible_handlesAlternateKeysAndShapes() {
        // AI returns "mockTest" key with "choices" and letter answer "B"
        val rawJson = """
            {
              "mockTest": [
                {
                  "stem": "What is the primary difference between TCP and UDP?",
                  "choices": [
                    "TCP is connectionless",
                    "TCP provides reliable delivery while UDP is best-effort",
                    "UDP uses a 3-way handshake",
                    "TCP has no flow control"
                  ],
                  "answer": "B",
                  "rationale": "TCP provides reliable, ordered byte streams via ACKs and retransmissions."
                }
              ]
            }
        """.trimIndent()

        val questions = AiLearningEngine.parseMockTestBatchFlexible(rawJson)
        assertEquals(1, questions.size)
        val q = questions[0]
        assertEquals("What is the primary difference between TCP and UDP?", q.question)
        assertEquals(4, q.options.size)
        assertEquals(1, q.correctIndex) // 'B' -> index 1
        assertTrue(q.explanation.contains("TCP provides reliable"))
    }

    @Test
    fun testParseMockTestBatchFlexible_handlesRootArray() {
        val rawJson = """
            [
              {
                "question": "Which AWS service is a managed NoSQL database?",
                "options": ["RDS", "DynamoDB", "Aurora", "Redshift"],
                "correctIndex": 1,
                "explanation": "Amazon DynamoDB is a fully managed NoSQL database service."
              }
            ]
        """.trimIndent()

        val questions = AiLearningEngine.parseMockTestBatchFlexible(rawJson)
        assertEquals(1, questions.size)
        assertEquals("DynamoDB", questions[0].options[1])
        assertEquals(1, questions[0].correctIndex)
    }

    @Test(expected = IllegalStateException::class)
    fun testParseMockTestBatchFlexible_emptyThrows() {
        val rawJson = """{ "questions": [] }"""
        AiLearningEngine.parseMockTestBatchFlexible(rawJson)
    }

    @Test
    fun testParseMockTestBatchFlexible_multiSelectWithArrayAnswers() {
        val rawJson = """
            {
              "questions": [
                {
                  "type": "MULTI_SELECT",
                  "question": "Which of the following are ACID properties in database management systems?",
                  "options": [
                    "Atomicity",
                    "Consistency",
                    "Throughput",
                    "Durability"
                  ],
                  "correctAnswers": ["Atomicity", "Consistency", "Durability"],
                  "explanation": "ACID stands for Atomicity, Consistency, Isolation, and Durability."
                },
                {
                  "type": "SINGLE_CHOICE",
                  "question": "Which protocol operates at Layer 4 of the OSI model?",
                  "options": ["IP", "TCP", "HTTP", "Ethernet"],
                  "correctIndex": 1,
                  "explanation": "TCP is a transport layer (Layer 4) protocol."
                }
              ]
            }
        """.trimIndent()

        val questions = AiLearningEngine.parseMockTestBatchFlexible(rawJson)
        assertEquals(2, questions.size)

        // Question 1: MULTI_SELECT
        val q1 = questions[0]
        assertEquals("MULTI_SELECT", q1.type)
        assertEquals(3, q1.correctAnswers.size)
        assertTrue(q1.correctAnswers.contains("Atomicity"))
        assertTrue(q1.correctAnswers.contains("Consistency"))
        assertTrue(q1.correctAnswers.contains("Durability"))

        // Question 2: SINGLE_CHOICE
        val q2 = questions[1]
        assertEquals("SINGLE_CHOICE", q2.type)
        assertEquals(1, q2.correctIndex)
    }

    @Test
    fun testMockTestQuestionEntity_exactSetEqualityGrading_multiSelect() {
        val options = listOf("Option A", "Option B", "Option C", "Option D")
        val entity = MockTestQuestionEntity(
            id = "q-1",
            testId = "test-1",
            questionIndex = 0,
            topic = "Databases",
            questionText = "Select all true statements",
            optionsJson = MockTestQuestionEntity.createOptionsJson(options),
            correctOptionIndex = 0,
            explanation = "A and C are correct.",
            questionType = org.opened.app.core.database.entity.QuestionType.MULTI_SELECT,
            correctAnswers = listOf("Option A", "Option C"),
            userSelectedAnswers = emptyList()
        )

        // Unanswered
        assertFalse(entity.isAnswered)
        assertFalse(entity.isCorrect)

        // Partial selection: only Option A selected -> incorrect
        val partialAnswer = entity.copy(userSelectedAnswers = listOf("Option A"))
        assertTrue(partialAnswer.isAnswered)
        assertFalse(partialAnswer.isCorrect)

        // Over-selection: Option A, B, and C selected -> incorrect (Option B is wrong)
        val overAnswer = entity.copy(userSelectedAnswers = listOf("Option A", "Option B", "Option C"))
        assertTrue(overAnswer.isAnswered)
        assertFalse(overAnswer.isCorrect)

        // Exact match: Option A and Option C selected -> correct!
        val exactAnswer = entity.copy(userSelectedAnswers = listOf("Option C", "Option A"))
        assertTrue(exactAnswer.isAnswered)
        assertTrue(exactAnswer.isCorrect)
        assertEquals(listOf(0, 2), exactAnswer.effectiveCorrectIndices)
        assertEquals(listOf(0, 2), exactAnswer.effectiveSelectedIndices)
    }

    @Test
    fun testMockTestQuestionEntity_exactSetEqualityGrading_singleChoice() {
        val options = listOf("Paris", "London", "Berlin", "Madrid")
        val entity = MockTestQuestionEntity(
            id = "q-2",
            testId = "test-2",
            questionIndex = 0,
            topic = "Geography",
            questionText = "Capital of France?",
            optionsJson = MockTestQuestionEntity.createOptionsJson(options),
            correctOptionIndex = 0,
            explanation = "Paris is the capital of France.",
            questionType = org.opened.app.core.database.entity.QuestionType.SINGLE_CHOICE,
            correctAnswers = listOf("Paris"),
            userSelectedAnswers = listOf("Paris")
        )

        assertTrue(entity.isAnswered)
        assertTrue(entity.isCorrect)
        assertEquals(listOf(0), entity.effectiveCorrectIndices)
        assertEquals(listOf(0), entity.effectiveSelectedIndices)

        // Wrong single choice
        val wrongAnswer = entity.copy(userSelectedAnswers = listOf("London"))
        assertTrue(wrongAnswer.isAnswered)
        assertFalse(wrongAnswer.isCorrect)

        // Exact userSelectedOption index wins for SINGLE_CHOICE even if options have case variants
        val caseVariantEntity = MockTestQuestionEntity(
            id = "q-3",
            testId = "test-3",
            questionIndex = 0,
            topic = "Logic",
            questionText = "Pick second option",
            optionsJson = MockTestQuestionEntity.createOptionsJson(listOf("True", "true")),
            correctOptionIndex = 1,
            explanation = "Second option",
            userSelectedOption = 1,
            questionType = org.opened.app.core.database.entity.QuestionType.SINGLE_CHOICE,
            correctAnswers = listOf("true"),
            userSelectedAnswers = listOf("true")
        )
        assertEquals(listOf(1), caseVariantEntity.effectiveSelectedIndices)
    }

    @Test
    fun testGenerateMockTestCustomPrompt_containsTopicCategoryAndDirectives() {
        val prompt = Prompts.generateMockTestCustomPrompt(
            topic = "Quantum Chromatography",
            examCategory = ExamCategory.ENTRANCE.displayName,
            targetExamName = "JEE Advanced",
            userNotes = "Focus on column efficiency equations and retention factors."
        )

        assertTrue(prompt.contains("Quantum Chromatography"))
        assertTrue(prompt.contains(ExamCategory.ENTRANCE.displayName))
        assertTrue(prompt.contains("TARGET OFFICIAL EXAM STANDARD: JEE Advanced"))
        assertTrue(prompt.contains("Focus on column efficiency equations and retention factors."))
        assertTrue(prompt.contains("REQUIREMENTS FOR THE GENERATED CUSTOM INSTRUCTION"))
    }

    @Test
    fun testExamMockTestBatchPrompt_withContextRetention() {
        val summary = """
PRIOR MISSED QUESTIONS (Learner scored wrong on these concepts):
- [PREVIOUSLY MISSED - TEST COMPLEMENTARY ANGLE] (Chromatography): What is the Van Deemter equation parameter A?

PRIOR TESTED QUESTIONS & TOPICS (DO NOT REPEAT):
- (Retention Theory): How does temperature alter the capacity factor k?
        """.trimIndent()

        val prompt = Prompts.examMockTestBatchPrompt(
            courseTitle = "Quantum Chromatography",
            courseTopic = "Analytical Chemistry",
            examCategory = "ENTRANCE",
            targetExamName = "JEE Advanced",
            batchTopicsSummary = "Module 2: Quantum Retention",
            batchNumber = 1,
            totalBatches = 1,
            questionCount = 10,
            startQuestionNumber = 1,
            previouslyTestedQuestionsSummary = summary
        )

        assertTrue(prompt.contains("PREVIOUSLY TESTED QUESTIONS & CONTINUITY CONTEXT"))
        assertTrue(prompt.contains("ZERO DUPLICATION"))
        assertTrue(prompt.contains("TARGET WEAK SPOTS"))
        assertTrue(prompt.contains("PROGRESSIVE COVERAGE"))
        assertTrue(prompt.contains("[PREVIOUSLY MISSED - TEST COMPLEMENTARY ANGLE]"))
        assertTrue(prompt.contains("Van Deemter"))
    }

    @Test
    fun testTopicGroupingLogic() {
        val test1 = MockTestEntity(
            id = "t1",
            pathId = "path-quantum",
            title = "Test 1",
            examCategory = ExamCategory.ENTRANCE,
            targetExamName = "JEE Advanced",
            totalQuestions = 10,
            timeLimitMinutes = 20,
            status = MockTestStatus.COMPLETED,
            score = 80
        )
        val test2 = MockTestEntity(
            id = "t2",
            pathId = "path-quantum",
            title = "Test 2",
            examCategory = ExamCategory.ENTRANCE,
            targetExamName = "JEE Advanced",
            totalQuestions = 10,
            timeLimitMinutes = 20,
            status = MockTestStatus.COMPLETED,
            score = 60
        )
        val test3 = MockTestEntity(
            id = "t3",
            pathId = "path-python",
            title = "Python Test 1",
            examCategory = ExamCategory.CERTIFICATION,
            targetExamName = "PCAP",
            totalQuestions = 15,
            timeLimitMinutes = 25,
            status = MockTestStatus.NOT_STARTED,
            score = 0
        )

        val tests = listOf(test1, test2, test3)
        val pathNames = mapOf("path-quantum" to "Quantum Chromatography", "path-python" to "Python for Data Science")

        val grouped = tests.groupBy { pathNames[it.pathId] ?: "General Practice" }

        assertEquals(2, grouped.size)
        assertEquals(2, grouped["Quantum Chromatography"]?.size)
        assertEquals(1, grouped["Python for Data Science"]?.size)

        val quantumTests = grouped["Quantum Chromatography"] ?: emptyList()
        val avgScore = quantumTests.map { it.score }.average().toInt()
        assertEquals(70, avgScore)
    }
}
