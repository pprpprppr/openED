package org.opened.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
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
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    fun detectAcademicDomain(
        topic: String,
        courseTitle: String = "",
        chapterTitle: String = ""
    ): AcademicDomain {
        return AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle, chapterTitle)
    }

    fun buildSystemInstruction(
        prefs: org.opened.app.core.datastore.UserPreferences,
        userLevel: String,
        domain: AcademicDomain = AcademicDomain.GENERAL_INTERDISCIPLINARY,
        tutorPersonaOverride: String? = null,
        customInstructionsOverride: String? = null
    ): String {
        val personaId = tutorPersonaOverride ?: prefs.selectedTutorPersona
        val customInstructions = (customInstructionsOverride ?: prefs.customSystemInstructions).trim()
        val persona = TutorPersonaRegistry.getById(personaId)
        val personaInstruction = if (persona.id == "CUSTOM") {
            customInstructions.ifBlank { TutorPersonaRegistry.ADAPTIVE_POLYMATH.instruction }
        } else {
            persona.instruction
        }

        val combinedInstruction = if (persona.id != "CUSTOM" && customInstructions.isNotBlank() &&
            customInstructions != org.opened.app.core.datastore.UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS
        ) {
            """
$personaInstruction

ADDITIONAL USER CUSTOM SYSTEM INSTRUCTIONS:
$customInstructions
""".trimIndent()
        } else {
            personaInstruction
        }

        return getEffectiveSystemInstruction(
            baseInstruction = combinedInstruction,
            userLevel = userLevel,
            domain = domain,
            learningLanguage = prefs.learningLanguage,
            bilingualTerminology = prefs.bilingualTerminologyEnabled
        )
    }

    private fun getEffectiveSystemInstruction(
        baseInstruction: String,
        userLevel: String,
        domain: AcademicDomain = AcademicDomain.GENERAL_INTERDISCIPLINARY,
        learningLanguage: String = "English",
        bilingualTerminology: Boolean = false
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

        val languageRule = if (!learningLanguage.equals("English", ignoreCase = true)) {
            """
GLOBAL LOCALIZATION RULE:
Teach and generate all lesson content, explanations, and concepts natively in $learningLanguage.
${if (bilingualTerminology) "BILINGUAL TECHNICAL ACCESSIBILITY: When introducing key concepts or specialized domain terms, display the $learningLanguage terminology alongside the standard English terminology in parentheses (e.g. 'Termino (\$'English Term')')." else ""}
""".trimIndent()
        } else {
            ""
        }

        return listOf(languageRule, nonCodeRule, beginnerRule, baseInstruction).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    suspend fun generateDiagnosticQuestions(
        topic: String,
        tutorPersonaOverride: String? = null,
        customInstructionsOverride: String? = null
    ): Result<List<DiagnosticQuestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val domain = AcademicDomainClassifier.detectAcademicDomain(topic)
            val prompt = Prompts.diagnosticInterviewPrompt(topic)
            val effectiveSystemInstruction = buildSystemInstruction(
                prefs = prefs,
                userLevel = "INTERMEDIATE",
                domain = domain,
                tutorPersonaOverride = tutorPersonaOverride,
                customInstructionsOverride = customInstructionsOverride
            )

            val rawOutput = multiAiClient.generateContent(
                systemInstruction = effectiveSystemInstruction,
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
        domainOverride: AcademicDomain? = null,
        tutorPersonaOverride: String? = null,
        customInstructionsOverride: String? = null
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
            val effectiveSystemInstruction = buildSystemInstruction(
                prefs = prefs,
                userLevel = userLevel,
                domain = domain,
                tutorPersonaOverride = tutorPersonaOverride,
                customInstructionsOverride = customInstructionsOverride
            )
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

            val effectiveSystemInstruction = buildSystemInstruction(
                prefs = prefs,
                userLevel = studyPath.userLevel,
                domain = domain
            )
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
        val effectiveSystemInstruction = buildSystemInstruction(
            prefs = prefs,
            userLevel = effectiveUserLevel,
            domain = domain
        )
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
    }.flowOn(Dispatchers.IO)

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
            val effectiveSystemInstruction = buildSystemInstruction(
                prefs = prefs,
                userLevel = effectiveUserLevel,
                domain = domain
            )

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

            val effectiveSystemInstruction = buildSystemInstruction(
                prefs = prefs,
                userLevel = "INTERMEDIATE",
                domain = domain
            )

            val answer = multiAiClient.generateContent(
                systemInstruction = effectiveSystemInstruction,
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
        return JsonRepairer.cleanAndRepairJson(raw)
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

    // -------------------------------------------------------------------------
    // AI System Instruction Prompt Architect
    // -------------------------------------------------------------------------

    suspend fun generateSystemInstruction(
        userNotes: String,
        targetSubject: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = Prompts.generateSystemInstructionPrompt(userNotes, targetSubject)
            val systemInstruction = """
You are an expert pedagogical prompt engineer. Generate an actionable, concise, and structured system instruction prompt for an AI personal tutor based on the user's requirements. Output ONLY the raw instruction text without commentary or markdown code fences.
            """.trimIndent()
            val rawOutput = multiAiClient.generateContent(
                systemInstruction = systemInstruction,
                prompt = prompt,
                isJson = false,
                taskType = org.opened.app.core.ai.provider.AiTaskType.GENERAL
            )
            rawOutput.trim()
                .removeSurrounding("```markdown", "```")
                .removeSurrounding("```", "```")
                .trim()
        }
    }

    // -------------------------------------------------------------------------
    // Exam Prep & Mock Test Generator
    // -------------------------------------------------------------------------

    suspend fun generateMockTestBatch(
        courseTitle: String,
        courseTopic: String,
        examCategory: ExamCategory,
        targetExamName: String,
        batchTopicsSummary: String,
        batchNumber: Int,
        totalBatches: Int,
        questionCount: Int,
        startQuestionNumber: Int,
        customPrompt: String = ""
    ): Result<List<MockTestQuestionItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val prompt = Prompts.examMockTestBatchPrompt(
                courseTitle = courseTitle,
                courseTopic = courseTopic,
                examCategory = examCategory.name,
                targetExamName = targetExamName,
                batchTopicsSummary = batchTopicsSummary,
                batchNumber = batchNumber,
                totalBatches = totalBatches,
                questionCount = questionCount,
                startQuestionNumber = startQuestionNumber,
                customPrompt = customPrompt
            )
            val raw = multiAiClient.generateContent(
                systemInstruction = prefs.customSystemInstructions,
                prompt = prompt,
                isJson = true,
                taskType = org.opened.app.core.ai.provider.AiTaskType.FLASHCARDS_QUIZ
            )
            val cleaned = cleanJsonOutput(raw)
            parseMockTestBatchFlexible(cleaned)
        }
    }

    suspend fun generateFullMockTest(
        pathId: String,
        title: String,
        examCategory: ExamCategory,
        targetExamName: String,
        totalQuestions: Int,
        timeLimitMinutes: Int,
        customPrompt: String = "",
        onProgress: suspend (currentBatch: Int, totalBatches: Int, questionsGenerated: Int) -> Unit = { _, _, _ -> }
    ): Result<MockTestEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val pathWithModules = repository.getStudyPathWithModules(pathId)
            val courseTitle = pathWithModules?.studyPath?.title ?: title
            val courseTopic = pathWithModules?.studyPath?.topic ?: targetExamName.ifBlank { title }
            val modulesWithChapters = pathWithModules?.modules ?: emptyList()

            // Collect all topics/chapters for syllabus distribution
            val topicList = mutableListOf<String>()
            for (moduleWithChapters in modulesWithChapters) {
                val mod = moduleWithChapters.module
                val chapTitles = moduleWithChapters.chapters.joinToString(", ") { it.title }
                topicList.add("Module: ${mod.title} (Chapters: $chapTitles)")
            }
            if (topicList.isEmpty()) {
                topicList.add("Core Syllabus & Domain Topics: $courseTitle ($courseTopic)")
            }

            // Calculate batches: batch size 10 questions (or smaller if totalQuestions <= 10)
            val batchSize = if (totalQuestions <= 10) totalQuestions else 10
            val totalBatches = ((totalQuestions + batchSize - 1) / batchSize).coerceAtLeast(1)

            val allQuestions = mutableListOf<MockTestQuestionEntity>()
            val testId = UUID.randomUUID().toString()

            var remainingQuestions = totalQuestions
            var currentStartIndex = 1

            for (batchIdx in 0 until totalBatches) {
                val currentBatchCount = if (remainingQuestions >= batchSize) batchSize else remainingQuestions
                if (currentBatchCount <= 0) break

                // Determine topics slice for this batch
                val batchTopics = if (topicList.size <= 1) {
                    val baseTopic = topicList.firstOrNull() ?: "$courseTitle ($courseTopic)"
                    "- $baseTopic (Comprehensive High-Yield Focus - Part ${batchIdx + 1} of $totalBatches)"
                } else {
                    val startTopicIdx = (batchIdx * topicList.size) / totalBatches
                    val endTopicIdx = (((batchIdx + 1) * topicList.size) / totalBatches).coerceAtLeast(startTopicIdx + 1).coerceAtMost(topicList.size)
                    topicList.subList(startTopicIdx, endTopicIdx).joinToString("\n- ", prefix = "- ")
                }

                onProgress(batchIdx + 1, totalBatches, allQuestions.size)

                val batchResult = generateMockTestBatch(
                    courseTitle = courseTitle,
                    courseTopic = courseTopic,
                    examCategory = examCategory,
                    targetExamName = targetExamName,
                    batchTopicsSummary = batchTopics,
                    batchNumber = batchIdx + 1,
                    totalBatches = totalBatches,
                    questionCount = currentBatchCount,
                    startQuestionNumber = currentStartIndex,
                    customPrompt = customPrompt
                ).getOrThrow()

                for (item in batchResult) {
                    val qEntity = MockTestQuestionEntity(
                        id = UUID.randomUUID().toString(),
                        testId = testId,
                        questionIndex = allQuestions.size,
                        topic = item.topic.ifBlank { "General $courseTopic" },
                        questionText = item.question,
                        optionsJson = MockTestQuestionEntity.createOptionsJson(item.options),
                        correctOptionIndex = item.correctIndex.coerceIn(0, (item.options.size - 1).coerceAtLeast(0)),
                        explanation = item.explanation,
                        userSelectedOption = -1,
                        isFlagged = false
                    )
                    allQuestions.add(qEntity)
                }

                currentStartIndex += currentBatchCount
                remainingQuestions -= currentBatchCount
            }

            if (allQuestions.isEmpty()) {
                throw IllegalStateException("Failed to generate questions for this mock test. No questions could be created.")
            }

            val finalTimeLimit = if (timeLimitMinutes > 0) {
                timeLimitMinutes
            } else {
                (allQuestions.size * examCategory.defaultTimePerQuestionMinutes).toInt().coerceAtLeast(10)
            }

            val finalTitle = title.ifBlank {
                if (targetExamName.isNotBlank()) "$targetExamName Mock Test" else "${examCategory.displayName} Mock Test"
            }

            val finalTestEntity = MockTestEntity(
                id = testId,
                pathId = pathId,
                title = finalTitle,
                examCategory = examCategory,
                targetExamName = targetExamName,
                totalQuestions = allQuestions.size,
                timeLimitMinutes = finalTimeLimit,
                status = MockTestStatus.NOT_STARTED,
                score = 0,
                totalCorrect = 0,
                totalAnswered = 0,
                timeSpentSeconds = 0,
                completedAt = 0L,
                createdAt = System.currentTimeMillis()
            )

            repository.saveMockTestWithQuestions(finalTestEntity, allQuestions)
            finalTestEntity
        }
    }

    suspend fun generateQuestionsForExistingTest(
        testId: String,
        onProgress: suspend (currentBatch: Int, totalBatches: Int, questionsGenerated: Int) -> Unit = { _, _, _ -> }
    ): Result<List<MockTestQuestionEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val test = requireNotNull(repository.getMockTestById(testId)) { "Mock test not found: $testId" }
            val pathWithModules = repository.getStudyPathWithModules(test.pathId)
            val courseTitle = pathWithModules?.studyPath?.title ?: test.title
            val courseTopic = pathWithModules?.studyPath?.topic ?: test.targetExamName.ifBlank { test.title }
            val modulesWithChapters = pathWithModules?.modules ?: emptyList()

            val topicList = mutableListOf<String>()
            for (moduleWithChapters in modulesWithChapters) {
                val mod = moduleWithChapters.module
                val chapTitles = moduleWithChapters.chapters.joinToString(", ") { it.title }
                topicList.add("Module: ${mod.title} (Chapters: $chapTitles)")
            }
            if (topicList.isEmpty()) {
                topicList.add("Core Syllabus & Domain Topics: $courseTitle ($courseTopic)")
            }

            val targetCount = if (test.totalQuestions > 0) test.totalQuestions else 10
            val batchSize = if (targetCount <= 10) targetCount else 10
            val totalBatches = ((targetCount + batchSize - 1) / batchSize).coerceAtLeast(1)

            val allQuestions = mutableListOf<MockTestQuestionEntity>()
            var remainingQuestions = targetCount
            var currentStartIndex = 1

            for (batchIdx in 0 until totalBatches) {
                val currentBatchCount = if (remainingQuestions >= batchSize) batchSize else remainingQuestions
                if (currentBatchCount <= 0) break

                val batchTopics = if (topicList.size <= 1) {
                    val baseTopic = topicList.firstOrNull() ?: "$courseTitle ($courseTopic)"
                    "- $baseTopic (Comprehensive High-Yield Focus - Part ${batchIdx + 1} of $totalBatches)"
                } else {
                    val startTopicIdx = (batchIdx * topicList.size) / totalBatches
                    val endTopicIdx = (((batchIdx + 1) * topicList.size) / totalBatches).coerceAtLeast(startTopicIdx + 1).coerceAtMost(topicList.size)
                    topicList.subList(startTopicIdx, endTopicIdx).joinToString("\n- ", prefix = "- ")
                }

                onProgress(batchIdx + 1, totalBatches, allQuestions.size)

                val batchResult = generateMockTestBatch(
                    courseTitle = courseTitle,
                    courseTopic = courseTopic,
                    examCategory = test.examCategory,
                    targetExamName = test.targetExamName,
                    batchTopicsSummary = batchTopics,
                    batchNumber = batchIdx + 1,
                    totalBatches = totalBatches,
                    questionCount = currentBatchCount,
                    startQuestionNumber = currentStartIndex
                ).getOrThrow()

                for (item in batchResult) {
                    val qEntity = MockTestQuestionEntity(
                        id = UUID.randomUUID().toString(),
                        testId = test.id,
                        questionIndex = allQuestions.size,
                        topic = item.topic.ifBlank { "General $courseTopic" },
                        questionText = item.question,
                        optionsJson = MockTestQuestionEntity.createOptionsJson(item.options),
                        correctOptionIndex = item.correctIndex.coerceIn(0, (item.options.size - 1).coerceAtLeast(0)),
                        explanation = item.explanation,
                        userSelectedOption = -1,
                        isFlagged = false
                    )
                    allQuestions.add(qEntity)
                }

                currentStartIndex += currentBatchCount
                remainingQuestions -= currentBatchCount
            }

            if (allQuestions.isEmpty()) {
                throw IllegalStateException("Failed to generate questions. Model returned no questions.")
            }

            val updatedTest = test.copy(
                totalQuestions = allQuestions.size,
                status = MockTestStatus.NOT_STARTED,
                timeSpentSeconds = 0,
                score = 0,
                totalCorrect = 0,
                totalAnswered = 0
            )
            repository.saveMockTestWithQuestions(updatedTest, allQuestions)
            allQuestions
        }
    }

    companion object {
        /**
         * Resilient and flexible parser for LLM-generated mock test question batches.
         * Decodes questions from diverse JSON formats (objects, arrays, wrapper keys) and handles
         * alternative field names (e.g. choices, answer letters 'A'-'D', prompt, stem, etc.).
         */
        fun parseMockTestBatchFlexible(cleanedJson: String): List<MockTestQuestionItem> {
            val rootElement = try {
                JsonRepairer.lenientJson.parseToJsonElement(cleanedJson)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to parse AI output as JSON: ${e.message}", e)
            }

            val questionsArray = findQuestionsArray(rootElement)
                ?: throw IllegalStateException("Could not find a list of questions in the AI response.")

            val result = mutableListOf<MockTestQuestionItem>()

            for (itemElem in questionsArray) {
                if (itemElem !is JsonObject) continue

                val questionText = itemElem.findFirstString(
                    "question", "questionText", "question_text", "text", "prompt", "problem", "stem", "title"
                )?.trim().orEmpty()

                if (questionText.isBlank()) continue

                val topic = itemElem.findFirstString(
                    "topic", "subject", "category", "domain", "concept", "chapter", "section"
                )?.trim().orEmpty()

                val options = extractOptions(itemElem)
                val correctIndex = extractCorrectIndex(itemElem, options)
                val explanation = itemElem.findFirstString(
                    "explanation", "rationale", "reason", "solution", "analysis", "detail"
                )?.trim() ?: "The correct option is Option ${('A' + correctIndex)}."

                result.add(
                    MockTestQuestionItem(
                        topic = topic,
                        question = questionText,
                        options = options,
                        correctIndex = correctIndex,
                        explanation = explanation
                    )
                )
            }

            if (result.isEmpty()) {
                throw IllegalStateException("The AI response did not contain any valid multiple-choice questions.")
            }

            return result
        }

        private fun findQuestionsArray(element: JsonElement): JsonArray? {
            if (element is JsonArray) {
                return element
            }
            if (element is JsonObject) {
                val standardKeys = listOf(
                    "questions", "mockTest", "mock_test", "mockTests", "mock_tests",
                    "exam", "items", "mcqs", "quiz", "test", "data", "results", "list"
                )
                for (key in standardKeys) {
                    val candidate = element[key]
                    if (candidate is JsonArray && candidate.isNotEmpty()) {
                        return candidate
                    }
                }

                for ((_, value) in element) {
                    if (value is JsonArray && value.isNotEmpty() && value.firstOrNull() is JsonObject) {
                        return value
                    }
                }

                for ((_, value) in element) {
                    if (value is JsonObject) {
                        val nested = findQuestionsArray(value)
                        if (nested != null && nested.isNotEmpty()) return nested
                    }
                }
            }
            return null
        }

        private fun JsonObject.findFirstString(vararg keys: String): String? {
            for (key in keys) {
                val el = this[key]
                if (el is JsonPrimitive && el.isString) {
                    return el.content
                } else if (el is JsonPrimitive) {
                    return el.content
                }
            }
            for (key in keys) {
                val entry = this.entries.find { it.key.equals(key, ignoreCase = true) }
                if (entry != null && entry.value is JsonPrimitive) {
                    return entry.value.jsonPrimitive.content
                }
            }
            return null
        }

        private fun extractOptions(questionObj: JsonObject): List<String> {
            val optionKeys = listOf(
                "options", "choices", "answers", "alternatives", "distractors", "optionsList", "options_list"
            )
            for (key in optionKeys) {
                val candidate = questionObj[key]
                    ?: questionObj.entries.find { it.key.equals(key, ignoreCase = true) }?.value
                if (candidate is JsonArray) {
                    val list = mutableListOf<String>()
                    for (opt in candidate) {
                        when (opt) {
                            is JsonPrimitive -> list.add(opt.content.trim())
                            is JsonObject -> {
                                val text = opt.findFirstString("text", "option", "value", "content", "choice")
                                    ?: opt.values.filterIsInstance<JsonPrimitive>().firstOrNull()?.content
                                if (!text.isNullOrBlank()) list.add(text.trim())
                            }
                            else -> {}
                        }
                    }
                    if (list.size >= 2) return list
                } else if (candidate is JsonObject) {
                    val sortedValues = candidate.entries
                        .sortedBy { it.key }
                        .mapNotNull {
                            (it.value as? JsonPrimitive)?.content?.trim()
                        }
                    if (sortedValues.size >= 2) return sortedValues
                }
            }
            return listOf("Option A", "Option B", "Option C", "Option D")
        }

        private fun extractCorrectIndex(questionObj: JsonObject, options: List<String>): Int {
            val indexKeys = listOf(
                "correctIndex", "correct_index", "answerIndex", "answer_index",
                "correctAnswerIndex", "correctAnswer", "correct_answer", "correct", "answer", "key"
            )
            for (key in indexKeys) {
                val candidate = questionObj[key]
                    ?: questionObj.entries.find { it.key.equals(key, ignoreCase = true) }?.value
                if (candidate is JsonPrimitive) {
                    val intVal = candidate.intOrNull
                    if (intVal != null && intVal in options.indices) {
                        return intVal
                    }
                    val str = candidate.content.trim()
                    val digitVal = str.toIntOrNull()
                    if (digitVal != null && digitVal in options.indices) {
                        return digitVal
                    }
                    val letterMatch = when (str.uppercase().take(1)) {
                        "A" -> 0
                        "B" -> 1
                        "C" -> 2
                        "D" -> 3
                        else -> -1
                    }
                    if (letterMatch in options.indices) {
                        return letterMatch
                    }
                    val matchIdx = options.indexOfFirst { opt ->
                        opt.equals(str, ignoreCase = true) || opt.contains(str, ignoreCase = true) || str.contains(opt, ignoreCase = true)
                    }
                    if (matchIdx >= 0) {
                        return matchIdx
                    }
                }
            }
            return 0
        }
    }
}
