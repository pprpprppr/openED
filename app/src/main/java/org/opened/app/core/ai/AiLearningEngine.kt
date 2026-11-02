package org.opened.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.domain.repository.OpenEDRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiLearningEngine @Inject constructor(
    private val multiAiClient: MultiAiClient,
    private val repository: OpenEDRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun detectAcademicDomain(
        topic: String,
        courseTitle: String = "",
        chapterTitle: String = ""
    ): AcademicDomain {
        return AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle, chapterTitle)
    }

    private fun getEffectiveSystemInstruction(
        baseInstruction: String,
        userLevel: String,
        domain: AcademicDomain = AcademicDomain.GENERAL_INTERDISCIPLINARY
    ): String {
        val levelNormalized = userLevel.trim().lowercase()
        val isBeginner = levelNormalized.contains("begin") || levelNormalized.contains("found") ||
                levelNormalized.contains("intro") || levelNormalized.contains("novice") ||
                (!levelNormalized.contains("intermed") && !levelNormalized.contains("adv") && !levelNormalized.contains("expert"))

        val nonCodeRule = if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            """
DISCIPLINE INTEGRITY & NON-PROGRAMMING RULE:
This course is in the discipline of ${domain.displayName}.
STRICTLY FORBID generating Python, JavaScript, C++, or any programming code blocks (```python, ```javascript, etc.) or pseudocode!
Do not force software engineering or programming paradigms into this academic discipline. Use discipline-authentic models, case studies, formulas ($$...$$) if mathematically necessary, or Markdown tables.
""".trimIndent()
        } else {
            ""
        }

        val beginnerRule = if (isBeginner) {
            """
CRITICAL PEDAGOGY RULE: The learner is a COMPLETE BEGINNER. Override any general instructions asking for excessive complexity or deep internals. Use plain, everyday English, zero unexplained jargon, relatable analogies, and gradual step-by-step examples.
""".trimIndent()
        } else {
            ""
        }

        return listOf(nonCodeRule, beginnerRule, baseInstruction).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    suspend fun generateDiagnosticQuestions(topic: String): Result<List<DiagnosticQuestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val prompt = Prompts.diagnosticInterviewPrompt(topic)

            val rawOutput = multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = true
            )
            val cleanedJson = cleanJsonOutput(rawOutput)
            val response = json.decodeFromString(DiagnosticQuestionsResponse.serializer(), cleanedJson)
            response.questions
        }
    }

    suspend fun generateSyllabus(
        topic: String,
        userLevel: String,
        answers: Map<String, List<String>>,
        domainOverride: AcademicDomain? = null
    ): Result<SyllabusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val domain = domainOverride ?: AcademicDomainClassifier.detectAcademicDomain(topic)
            val summaryBuilder = StringBuilder()
            summaryBuilder.append("User Self-Assessed Level: $userLevel\n")
            for ((q, ansList) in answers) {
                summaryBuilder.append("- $q: ${ansList.joinToString(", ")}\n")
            }

            val prompt = Prompts.syllabusGenerationPrompt(topic, userLevel, summaryBuilder.toString(), domain)
            val effectiveSystemInstruction = getEffectiveSystemInstruction(prefs.customSystemInstructions, userLevel, domain)
            val rawOutput = multiAiClient.generateContent(
                systemInstruction = effectiveSystemInstruction,
                prompt = prompt,
                isJson = true,
                taskType = org.opened.app.core.ai.provider.AiTaskType.CURRICULUM_SYNTHESIS
            )
            val cleanedJson = cleanJsonOutput(rawOutput)
            val parsed = json.decodeFromString(SyllabusResponse.serializer(), cleanedJson)
            parsed.copy(userLevel = userLevel.ifBlank { parsed.userLevel })
        }
    }

    suspend fun persistGeneratedSyllabus(syllabus: SyllabusResponse): String = withContext(Dispatchers.IO) {
        val pathId = UUID.randomUUID().toString()
        val studyPath = StudyPathEntity(
            id = pathId,
            title = syllabus.title,
            topic = syllabus.topic,
            userLevel = syllabus.userLevel.ifBlank { "BEGINNER" },
            learningGoalsJson = json.encodeToString(syllabus.prerequisites),
            progress = 0f
        )

        val modulesWithChapters = mutableListOf<Pair<ModuleEntity, List<ChapterEntity>>>()

        syllabus.modules.forEachIndexed { modIndex, modItem ->
            val moduleId = UUID.randomUUID().toString()
            val moduleEntity = ModuleEntity(
                id = moduleId,
                pathId = pathId,
                title = modItem.title,
                orderIndex = modIndex,
                summary = modItem.summary
            )

            val chapterEntities = modItem.chapters.mapIndexed { chIndex, chItem ->
                ChapterEntity(
                    id = UUID.randomUUID().toString(),
                    moduleId = moduleId,
                    pathId = pathId,
                    title = chItem.title,
                    orderIndex = chIndex,
                    markdownContent = "",
                    summary = chItem.summary,
                    status = ChapterStatus.PENDING
                )
            }
            modulesWithChapters.add(Pair(moduleEntity, chapterEntities))
        }

        repository.saveStudyPathWithModules(studyPath, modulesWithChapters)
        pathId
    }

    suspend fun expandCourseSyllabus(pathId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pathWithModules = requireNotNull(repository.getStudyPathWithModules(pathId)) { "Course not found: $pathId" }
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val studyPath = pathWithModules.studyPath

            val existingOutline = pathWithModules.sortedModules.map { modWithChapters ->
                modWithChapters.module.title to modWithChapters.sortedChapters.map { it.title }
            }

            val domain = AcademicDomainClassifier.detectAcademicDomain(studyPath.topic, studyPath.title)
            val prompt = Prompts.expandCoursePrompt(
                courseTitle = studyPath.title,
                topic = studyPath.topic,
                userLevel = studyPath.userLevel,
                existingModulesWithChapters = existingOutline,
                domain = domain
            )

            val effectiveSystemInstruction = getEffectiveSystemInstruction(prefs.customSystemInstructions, studyPath.userLevel, domain)
            val rawOutput = multiAiClient.generateContent(
                systemInstruction = effectiveSystemInstruction,
                prompt = prompt,
                isJson = true,
                taskType = org.opened.app.core.ai.provider.AiTaskType.CURRICULUM_SYNTHESIS
            )
            val cleanedJson = cleanJsonOutput(rawOutput)
            val response = json.decodeFromString(ExpandCourseResponse.serializer(), cleanedJson)

            val currentModuleCount = pathWithModules.modules.size
            val newModulesWithChapters = mutableListOf<Pair<ModuleEntity, List<ChapterEntity>>>()

            response.modules.take(2).forEachIndexed { index, modItem ->
                val newOrderIndex = currentModuleCount + index
                val moduleId = UUID.randomUUID().toString()
                val moduleEntity = ModuleEntity(
                    id = moduleId,
                    pathId = pathId,
                    title = modItem.title,
                    orderIndex = newOrderIndex,
                    summary = modItem.summary
                )
                val chapterEntities = modItem.chapters.mapIndexed { chIndex, chItem ->
                    ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        moduleId = moduleId,
                        pathId = pathId,
                        title = chItem.title,
                        orderIndex = chIndex,
                        markdownContent = "",
                        summary = chItem.summary,
                        status = ChapterStatus.PENDING
                    )
                }
                newModulesWithChapters.add(Pair(moduleEntity, chapterEntities))
            }

            if (newModulesWithChapters.isNotEmpty()) {
                repository.appendModulesWithChapters(pathId, newModulesWithChapters)
            }
        }
    }

    fun streamChapterContent(
        chapterId: String,
        userLevelOverride: String? = null,
        domainOverride: AcademicDomain? = null
    ): Flow<String> = flow {
        val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found: $chapterId" }
        val pathWithModules = requireNotNull(repository.getStudyPathWithModules(chapter.pathId)) { "Path not found" }
        val currentModule = pathWithModules.modules.find { it.module.id == chapter.moduleId }?.module

        val allOtherChapterTitles = pathWithModules.allChapters
            .filter { it.id != chapterId }
            .map { it.title }

        if (chapter.markdownContent.isNotBlank()) {
            repository.addChapterRevision(
                chapterId = chapterId,
                previousMarkdown = chapter.markdownContent,
                changeReason = "Pre-regeneration backup snapshot"
            )
        }

        repository.updateChapterStatus(chapterId, ChapterStatus.GENERATING)

        val domain = domainOverride ?: AcademicDomainClassifier.detectAcademicDomain(
            topic = pathWithModules.studyPath.topic,
            courseTitle = pathWithModules.studyPath.title,
            chapterTitle = chapter.title
        )
        val effectiveUserLevel = userLevelOverride?.takeIf { it.isNotBlank() } ?: pathWithModules.studyPath.userLevel
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val effectiveSystemInstruction = getEffectiveSystemInstruction(prefs.customSystemInstructions, effectiveUserLevel, domain)
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = pathWithModules.studyPath.title,
            topic = pathWithModules.studyPath.topic,
            userLevel = effectiveUserLevel,
            moduleTitle = currentModule?.title ?: "Module",
            chapterTitle = chapter.title,
            chapterSummary = chapter.summary,
            keyConcepts = emptyList(),
            otherChapterTitles = allOtherChapterTitles,
            domain = domain
        )

        val accumulatedText = StringBuilder()
        try {
            multiAiClient.streamGenerateContent(
                systemInstruction = effectiveSystemInstruction,
                prompt = prompt,
                taskType = org.opened.app.core.ai.provider.AiTaskType.CHAPTER_LESSON
            ).collect { chunk ->
                accumulatedText.append(chunk)
                emit(chunk)
            }

            var finalMarkdown = accumulatedText.toString().trim()
            if (finalMarkdown.isNotEmpty()) {
                if (domain != AcademicDomain.SOFTWARE_AND_CS) {
                    finalMarkdown = sanitizeNonCsCodeBlocks(finalMarkdown)
                }
                finalMarkdown = org.opened.app.core.source.EducationalDisclaimerManager.injectDisclaimerIfNeeded(finalMarkdown, domain)
                repository.updateChapterContent(chapterId, finalMarkdown, ChapterStatus.COMPLETED)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.updateChapterStatus(chapterId, ChapterStatus.PENDING)
            throw e
        } finally {
            if (accumulatedText.toString().trim().isBlank()) {
                repository.updateChapterStatus(chapterId, ChapterStatus.PENDING)
            }
        }
    }

    private fun sanitizeNonCsCodeBlocks(markdown: String): String {
        val codeBlockRegex = "```(python|javascript|typescript|c|cpp|java|rust|go|csharp)[\\s\\S]*?```".toRegex(RegexOption.IGNORE_CASE)
        if (!codeBlockRegex.containsMatchIn(markdown)) return markdown

        return codeBlockRegex.replace(markdown) { matchResult ->
            val inner = matchResult.value
                .lines()
                .drop(1)
                .dropLast(1)
                .joinToString("\n")
                .trim()
            "> [!NOTE] Conceptual Model\n> ${inner.replace("\n", "\n> ")}"
        }
    }

    suspend fun refineChapterContent(
        chapterId: String,
        selectedText: String,
        action: RefineAction,
        customInstruction: String? = null,
        userLevelOverride: String? = null,
        domainOverride: AcademicDomain? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            val studyPath = repository.getStudyPathWithModules(chapter.pathId)?.studyPath
            val domain = domainOverride ?: AcademicDomainClassifier.detectAcademicDomain(
                topic = studyPath?.topic.orEmpty(),
                courseTitle = studyPath?.title.orEmpty(),
                chapterTitle = chapter.title
            )
            val effectiveUserLevel = userLevelOverride?.takeIf { it.isNotBlank() } ?: (studyPath?.userLevel ?: "BEGINNER")
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val effectiveSystemInstruction = getEffectiveSystemInstruction(prefs.customSystemInstructions, effectiveUserLevel, domain)

            val prompt = Prompts.inlineRefinePrompt(
                chapterContext = chapter.markdownContent,
                selectedText = selectedText,
                action = action,
                customInstruction = customInstruction,
                userLevel = effectiveUserLevel,
                domain = domain
            )

            var replacement = multiAiClient.generateContent(
                systemInstruction = effectiveSystemInstruction,
                prompt = prompt,
                isJson = false,
                taskType = org.opened.app.core.ai.provider.AiTaskType.REFINEMENT_QA
            ).trim()

            if (domain != AcademicDomain.SOFTWARE_AND_CS) {
                replacement = sanitizeNonCsCodeBlocks(replacement)
            }

            // Save previous version to history
            val reasonLabel = when (action) {
                RefineAction.SIMPLIFY -> "Simplified explanation"
                RefineAction.ADD_CODE_MATH -> "Added code & math rigor"
                RefineAction.FACT_CHECK_CORRECT -> "Fact-checked & corrected"
                RefineAction.CUSTOM -> customInstruction?.take(50) ?: "Custom refinement"
            }
            repository.addChapterRevision(chapterId, chapter.markdownContent, reasonLabel)

            // Mutate content in place
            val updatedContent = if (selectedText.isNotBlank() && chapter.markdownContent.contains(selectedText)) {
                chapter.markdownContent.replace(selectedText, replacement)
            } else {
                replacement
            }

            repository.updateChapterContent(chapterId, updatedContent, ChapterStatus.COMPLETED)
            replacement
        }
    }

    suspend fun askSocraticQuestion(
        chapterId: String,
        question: String
    ): Result<ChapterQaEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            val prefs = preferencesRepository.userPreferencesFlow.first()

            val studyPath = repository.getStudyPathWithModules(chapter.pathId)?.studyPath
            val domain = AcademicDomainClassifier.detectAcademicDomain(
                topic = studyPath?.topic.orEmpty(),
                courseTitle = studyPath?.title.orEmpty(),
                chapterTitle = chapter.title
            )
            val prompt = Prompts.socraticQaPrompt(
                chapterTitle = chapter.title,
                chapterContent = chapter.markdownContent,
                userQuestion = question,
                domain = domain
            )

            val answer = multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = false,
                taskType = org.opened.app.core.ai.provider.AiTaskType.REFINEMENT_QA
            ).trim()
            repository.addChapterQa(chapterId, question, answer)
        }
    }

    suspend fun appendQaToChapter(chapterId: String, qa: ChapterQaEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            val calloutBlock = """

> [!NOTE] Deep-Dive Q&A: ${qa.userQuestion}
> 
> ${qa.aiAnswer.replace("\n", "\n> ")}
""".trimIndent()

            val updatedContent = "${chapter.markdownContent.trimEnd()}\n\n$calloutBlock\n"
            repository.addChapterRevision(chapterId, chapter.markdownContent, "Appended Socratic Q&A to chapter")
            repository.updateChapterContent(chapterId, updatedContent, ChapterStatus.COMPLETED)
            repository.markQaAsMerged(qa.id, true)
        }
    }

    private fun cleanJsonOutput(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json")
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```")
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```")
        }
        return trimmed.trim()
    }

    // -------------------------------------------------------------------------
    // Feature 1: Spaced Repetition Flashcards
    // -------------------------------------------------------------------------

    suspend fun generateFlashcards(chapterId: String): Result<List<FlashcardEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            require(chapter.markdownContent.isNotBlank()) { "Chapter has no content yet. Generate content first." }
            val prefs = preferencesRepository.userPreferencesFlow.first()

            val prompt = Prompts.generateFlashcardsPrompt(chapter.title, chapter.markdownContent)
            val raw = multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = true,
                taskType = org.opened.app.core.ai.provider.AiTaskType.FLASHCARDS_QUIZ
            )
            val cleaned = cleanJsonOutput(raw)
            val response = json.decodeFromString(FlashcardGenerationResponse.serializer(), cleaned)

            val now = System.currentTimeMillis()
            val entities = response.flashcards.map { item ->
                FlashcardEntity(
                    id = UUID.randomUUID().toString(),
                    chapterId = chapterId,
                    pathId = chapter.pathId,
                    front = item.front,
                    back = item.back,
                    intervalDays = 1,
                    easeFactor = 2.5f,
                    repetitions = 0,
                    nextReviewTimestamp = now,
                    createdAt = now
                )
            }
            repository.insertFlashcards(entities)
            entities
        }
    }

    /**
     * FSRS / SM-2 algorithm: updates flashcard scheduling based on quality rating.
     * @param quality 0=Again, 1=Hard, 2=Good, 3=Easy
     */
    suspend fun reviewFlashcard(card: FlashcardEntity, quality: Int): Result<FlashcardEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val rating = when (quality) {
                0 -> org.opened.app.core.learning.FsrsRating.AGAIN
                1 -> org.opened.app.core.learning.FsrsRating.HARD
                2 -> org.opened.app.core.learning.FsrsRating.GOOD
                else -> org.opened.app.core.learning.FsrsRating.EASY
            }
            val elapsedDays = if (card.lastReviewTimestamp > 0L) {
                ((System.currentTimeMillis() - card.lastReviewTimestamp) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
            } else {
                card.elapsedDays
            }

            val fsrs = org.opened.app.core.learning.FlashcardScheduler.calculateFsrsNextReview(
                card = card,
                rating = rating,
                elapsedDays = elapsedDays
            )
            val nextReview = System.currentTimeMillis() + fsrs.intervalDays * 24L * 60L * 60L * 1000L
            val updated = card.copy(
                intervalDays = fsrs.intervalDays,
                easeFactor = card.easeFactor,
                repetitions = fsrs.repetitions,
                nextReviewTimestamp = nextReview,
                stability = fsrs.stability,
                difficulty = fsrs.difficulty,
                state = fsrs.state.value,
                elapsedDays = elapsedDays,
                lastReviewTimestamp = System.currentTimeMillis()
            )
            repository.updateFlashcard(updated)
            updated
        }
    }

    // -------------------------------------------------------------------------
    // Feature 2: Adaptive Quiz
    // -------------------------------------------------------------------------

    suspend fun generateAdaptiveQuiz(chapterId: String): Result<AdaptiveQuizResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            require(chapter.markdownContent.isNotBlank()) { "Chapter has no content yet." }
            val prefs = preferencesRepository.userPreferencesFlow.first()

            val prompt = Prompts.adaptiveQuizPrompt(chapter.title, chapter.markdownContent)
            val raw = multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = true,
                taskType = org.opened.app.core.ai.provider.AiTaskType.FLASHCARDS_QUIZ
            )
            val cleaned = cleanJsonOutput(raw)
            json.decodeFromString(AdaptiveQuizResponse.serializer(), cleaned)
        }
    }

    // -------------------------------------------------------------------------
    // Feature 2b: Feynman Evaluation
    // -------------------------------------------------------------------------

    suspend fun evaluateFeynmanExplanation(
        chapterId: String,
        userExplanation: String
    ): Result<FeynmanEvaluationResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            val prefs = preferencesRepository.userPreferencesFlow.first()

            val prompt = Prompts.feynmanEvaluationPrompt(chapter.title, chapter.markdownContent, userExplanation)
            val raw = multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = true,
                taskType = org.opened.app.core.ai.provider.AiTaskType.FLASHCARDS_QUIZ
            )
            val cleaned = cleanJsonOutput(raw)
            json.decodeFromString(FeynmanEvaluationResponse.serializer(), cleaned)
        }
    }
}
