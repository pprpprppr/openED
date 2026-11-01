package org.opened.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
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
    private val geminiApiClient: GeminiApiClient,
    private val repository: OpenEDRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun generateDiagnosticQuestions(topic: String): Result<List<DiagnosticQuestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val prompt = Prompts.diagnosticInterviewPrompt(topic)

            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = prefs.customSystemInstructions))
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.4f
                )
            )

            val rawOutput = geminiApiClient.generateContent(prefs.selectedModel, request)
            val cleanedJson = cleanJsonOutput(rawOutput)
            val response = json.decodeFromString(DiagnosticQuestionsResponse.serializer(), cleanedJson)
            response.questions
        }
    }

    suspend fun generateSyllabus(
        topic: String,
        userLevel: String,
        answers: Map<String, List<String>>
    ): Result<SyllabusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val summaryBuilder = StringBuilder()
            summaryBuilder.append("User Self-Assessed Level: $userLevel\n")
            for ((q, ansList) in answers) {
                summaryBuilder.append("- $q: ${ansList.joinToString(", ")}\n")
            }

            val prompt = Prompts.syllabusGenerationPrompt(topic, summaryBuilder.toString())
            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = prefs.customSystemInstructions))
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.5f
                )
            )

            val rawOutput = geminiApiClient.generateContent(prefs.selectedModel, request)
            val cleanedJson = cleanJsonOutput(rawOutput)
            json.decodeFromString(SyllabusResponse.serializer(), cleanedJson)
        }
    }

    suspend fun persistGeneratedSyllabus(syllabus: SyllabusResponse): String = withContext(Dispatchers.IO) {
        val pathId = UUID.randomUUID().toString()
        val studyPath = StudyPathEntity(
            id = pathId,
            title = syllabus.title,
            topic = syllabus.topic,
            userLevel = syllabus.userLevel,
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

    fun streamChapterContent(chapterId: String): Flow<String> = flow {
        val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found: $chapterId" }
        val pathWithModules = requireNotNull(repository.getStudyPathWithModules(chapter.pathId)) { "Path not found" }
        val currentModule = pathWithModules.modules.find { it.module.id == chapter.moduleId }?.module

        val allOtherChapterTitles = pathWithModules.allChapters
            .filter { it.id != chapterId }
            .map { it.title }

        repository.updateChapterStatus(chapterId, ChapterStatus.GENERATING)

        val prefs = preferencesRepository.userPreferencesFlow.first()
        val prompt = Prompts.chapterContentPrompt(
            courseTitle = pathWithModules.studyPath.title,
            topic = pathWithModules.studyPath.topic,
            userLevel = pathWithModules.studyPath.userLevel,
            moduleTitle = currentModule?.title ?: "Module",
            chapterTitle = chapter.title,
            chapterSummary = chapter.summary,
            keyConcepts = emptyList(),
            otherChapterTitles = allOtherChapterTitles
        )

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = prefs.customSystemInstructions))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.7f
            )
        )

        val accumulatedText = StringBuilder()
        try {
            geminiApiClient.streamGenerateContent(prefs.selectedModel, request).collect { chunk ->
                accumulatedText.append(chunk)
                emit(chunk)
            }

            val finalMarkdown = accumulatedText.toString().trim()
            if (finalMarkdown.isNotEmpty()) {
                repository.updateChapterContent(chapterId, finalMarkdown, ChapterStatus.COMPLETED)
            }
        } catch (e: Exception) {
            repository.updateChapterStatus(chapterId, ChapterStatus.PENDING)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun refineChapterContent(
        chapterId: String,
        selectedText: String,
        action: RefineAction,
        customInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = requireNotNull(repository.getChapter(chapterId)) { "Chapter not found" }
            val prefs = preferencesRepository.userPreferencesFlow.first()

            val prompt = Prompts.inlineRefinePrompt(
                chapterContext = chapter.markdownContent,
                selectedText = selectedText,
                action = action,
                customInstruction = customInstruction
            )

            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = prefs.customSystemInstructions))
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.4f)
            )

            val replacement = geminiApiClient.generateContent(prefs.selectedModel, request).trim()

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
                "${chapter.markdownContent}\n\n$replacement"
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

            val prompt = Prompts.socraticQaPrompt(
                chapterTitle = chapter.title,
                chapterContent = chapter.markdownContent,
                userQuestion = question
            )

            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = prefs.customSystemInstructions))
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.5f)
            )

            val answer = geminiApiClient.generateContent(prefs.selectedModel, request).trim()
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
}
