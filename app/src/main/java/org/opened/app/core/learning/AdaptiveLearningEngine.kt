package org.opened.app.core.learning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.opened.app.core.ai.AdaptiveQuizResponse
import org.opened.app.core.ai.MultiAiClient
import org.opened.app.core.ai.Prompts
import org.opened.app.core.ai.provider.AiTaskType
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.LearningObjectiveEntity
import org.opened.app.core.database.entity.MistakeRecordEntity
import org.opened.app.core.database.entity.RemedialLessonEntity
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.domain.repository.OpenEDRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveLearningEngine @Inject constructor(
    private val repository: OpenEDRepository,
    private val multiAiClient: MultiAiClient,
    private val preferencesRepository: UserPreferencesRepository
) {

    /**
     * Record a quiz session and perform adaptive analysis:
     * - Evaluates answers and computes objective mastery.
     * - Records mistakes for wrong questions.
     * - Diagnoses prerequisite weaknesses if repeated mistakes occur.
     * - Triggers automatic remedial micro-lesson generation when needed.
     */
    suspend fun recordQuizAttempt(
        chapterId: String,
        quiz: AdaptiveQuizResponse,
        answers: Map<Int, Int>,
        durationSeconds: Long,
        confidence: Float = 3.0f
    ): QuizEvaluationResult = withContext(Dispatchers.IO) {
        val chapter = repository.getChapter(chapterId)
        val pathId = chapter?.pathId.orEmpty()
        val questions = quiz.questions

        if (questions.isEmpty()) {
            return@withContext QuizEvaluationResult(
                score = 0,
                totalQuestions = 0,
                correctCount = 0,
                identifiedWeaknesses = emptyList(),
                remedialGenerated = false
            )
        }

        var correctCount = 0
        val wrongQuestions = mutableListOf<WrongQuestionData>()

        questions.forEachIndexed { index, question ->
            val userSelected = answers[index] ?: -1
            if (userSelected == question.correctIndex) {
                correctCount++
            } else {
                val selectedText = question.options.getOrNull(userSelected) ?: "No answer"
                val correctText = question.options.getOrNull(question.correctIndex) ?: "Correct"
                val conceptKey = extractConceptKey(question.question, question.explanation)
                wrongQuestions.add(
                    WrongQuestionData(
                        conceptKey = conceptKey,
                        questionPrompt = question.question,
                        userAnswer = selectedText,
                        correctAnswer = correctText,
                        explanation = question.explanation
                    )
                )
            }
        }

        val score = (correctCount * 100) / questions.size
        val timestamp = System.currentTimeMillis()

        // 1. Update or create LearningObjectiveEntity for chapter
        val objectiveId = "obj_${chapterId}_mastery"
        val status = when {
            score >= 85 -> "MASTERED"
            score >= 60 -> "IN_PROGRESS"
            else -> "NEEDS_REMEDIAL"
        }

        val objective = LearningObjectiveEntity(
            id = objectiveId,
            chapterId = chapterId,
            pathId = pathId,
            objectiveText = "Master core concepts of: ${chapter?.title.orEmpty()}",
            conceptKey = chapter?.title.orEmpty().lowercase().replace("\\s+".toRegex(), "_"),
            masteryScore = score,
            status = status,
            confidenceScore = confidence,
            studyDurationSeconds = durationSeconds,
            reviewCount = 1,
            lastEvaluatedAt = timestamp
        )
        repository.saveObjectives(listOf(objective))

        // 2. Record mistakes
        val identifiedWeaknesses = mutableListOf<String>()
        var remedialCreated = false

        for (wrong in wrongQuestions) {
            val mistake = MistakeRecordEntity(
                id = UUID.randomUUID().toString(),
                chapterId = chapterId,
                pathId = pathId,
                conceptKey = wrong.conceptKey,
                questionPrompt = wrong.questionPrompt,
                userSelectedAnswer = wrong.userAnswer,
                correctAnswer = wrong.correctAnswer,
                explanation = wrong.explanation,
                failureCount = 1,
                timestamp = timestamp
            )
            repository.recordMistake(mistake)
            identifiedWeaknesses.add(wrong.conceptKey)

            // Check if repeated mistake (failureCount >= 2 or score < 60)
            val existing = repository.getMistakesForChapter(chapterId)
                .find { it.conceptKey == wrong.conceptKey }
            if ((existing != null && existing.failureCount >= 2) || score < 60) {
                try {
                    generateRemedialMicroLesson(
                        chapterId = chapterId,
                        pathId = pathId,
                        conceptKey = wrong.conceptKey,
                        mistakeContext = "Question: ${wrong.questionPrompt}\nUser thought: ${wrong.userAnswer}\nCorrect principle: ${wrong.explanation}"
                    )
                    remedialCreated = true
                } catch (_: Exception) {
                    // Non-fatal if AI generation fails offline
                }
            }
        }

        QuizEvaluationResult(
            score = score,
            totalQuestions = questions.size,
            correctCount = correctCount,
            identifiedWeaknesses = identifiedWeaknesses.distinct(),
            remedialGenerated = remedialCreated
        )
    }

    /**
     * Synthesize a laser-focused 2-to-3 minute remedial micro-lesson addressing
     * the exact root misconception, avoiding a whole-chapter rereading loop.
     */
    suspend fun generateRemedialMicroLesson(
        chapterId: String,
        pathId: String,
        conceptKey: String,
        mistakeContext: String
    ): RemedialLessonEntity = withContext(Dispatchers.IO) {
        val chapter = repository.getChapter(chapterId)
        val path = repository.getStudyPathWithModules(pathId)?.studyPath
        val userLevel = path?.userLevel ?: "BEGINNER"
        val prefs = preferencesRepository.userPreferencesFlow.first()

        val prompt = Prompts.remedialMicroLessonPrompt(
            courseTitle = path?.title ?: "Course",
            chapterTitle = chapter?.title ?: "Chapter",
            conceptKey = conceptKey.replace("_", " ").capitalizeWords(),
            diagnosisReason = "Repeated conceptual confusion detected in assessment.",
            mistakeContext = mistakeContext,
            userLevel = userLevel
        )

        val generatedMarkdown = try {
            multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = false,
                taskType = AiTaskType.REFINEMENT_QA
            )
        } catch (e: Exception) {
            """
            > [!TIP]
            > **Remedial Focus: ${conceptKey.replace("_", " ").capitalizeWords()}**
            
            Let's clarify this core principle step-by-step:
            
            $mistakeContext
            
            Review the foundational definitions before retrying the quiz.
            """.trimIndent()
        }

        val lesson = RemedialLessonEntity(
            id = UUID.randomUUID().toString(),
            chapterId = chapterId,
            pathId = pathId,
            conceptKey = conceptKey,
            title = "Remedial: ${conceptKey.replace("_", " ").capitalizeWords()}",
            diagnosisReason = "Conceptual gap identified in quiz answers.",
            targetedMarkdown = generatedMarkdown,
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        repository.saveRemedialLesson(lesson)
        lesson
    }

    /**
     * Interleaved practice queue generator.
     * Orders flashcards across all courses using FSRS forgetting curves
     * and weak prerequisite signals, interleaving topics to maximize retention.
     */
    suspend fun getInterleavedReviewQueue(
        cards: List<FlashcardEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<FlashcardEntity> = withContext(Dispatchers.IO) {
        val weakChapters = mutableSetOf<String>()
        try {
            repository.getWeakObjectivesFlow().first().forEach {
                weakChapters.add(it.chapterId)
            }
        } catch (_: Exception) {}

        FlashcardScheduler.scheduleInterleavedPractice(
            cards = cards,
            nowMillis = nowMillis,
            weakChapterIds = weakChapters
        )
    }

    private fun extractConceptKey(question: String, explanation: String): String {
        val combined = "$question $explanation".lowercase()
        val stopwords = setOf(
            "what", "which", "how", "why", "when", "where", "is", "are", "the", "a",
            "an", "of", "in", "to", "for", "with", "on", "at", "by", "from", "that",
            "this", "correct", "incorrect", "following", "true", "false", "does"
        )
        val words = combined.split("\\W+".toRegex())
            .filter { it.length > 3 && it !in stopwords }

        return words.take(3).joinToString("_").ifBlank { "core_concept" }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}

data class WrongQuestionData(
    val conceptKey: String,
    val questionPrompt: String,
    val userAnswer: String,
    val correctAnswer: String,
    val explanation: String
)

data class QuizEvaluationResult(
    val score: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val identifiedWeaknesses: List<String>,
    val remedialGenerated: Boolean
)
