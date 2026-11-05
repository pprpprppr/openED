package org.opened.app.core.document

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opened.app.core.ai.MultiAiClient
import org.opened.app.core.ai.provider.AiTaskType
import org.opened.app.core.database.entity.DocumentChunkEntity
import org.opened.app.core.database.entity.DocumentEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.domain.repository.OpenEDRepository
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DefinitionItem(
    val term: String,
    val definition: String,
    val pageNumber: Int,
    val sectionTitle: String
)

@Serializable
private data class GlossaryExtractionResponse(
    val definitions: List<DefinitionItem> = emptyList()
)

@Serializable
private data class DocumentFlashcardItem(
    val front: String,
    val back: String,
    val pageNumber: Int
)

@Serializable
private data class DocumentFlashcardResponse(
    val flashcards: List<DocumentFlashcardItem> = emptyList()
)

@Singleton
class DocumentIntelligenceEngine @Inject constructor(
    private val repository: OpenEDRepository,
    private val multiAiClient: MultiAiClient,
    private val preferencesRepository: UserPreferencesRepository
) {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    /**
     * Ingest, chunk, and index an imported file into local Room storage.
     */
    suspend fun ingestDocument(
        fileName: String,
        inputStream: InputStream,
        mimeType: String = "",
        sizeBytes: Long = 0L,
        customTitle: String? = null
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val parsed = when {
            fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf" ->
                DocumentIngestionPipeline.parsePdf(inputStream, fileName, sizeBytes)
            fileName.endsWith(".epub", ignoreCase = true) || mimeType.contains("epub") ->
                DocumentIngestionPipeline.parseEpub(inputStream, fileName, sizeBytes)
            fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".markdown", ignoreCase = true) ->
                DocumentIngestionPipeline.parseMarkdown(inputStream.bufferedReader().readText(), fileName)
            else ->
                DocumentIngestionPipeline.parsePlainText(inputStream.bufferedReader().readText(), fileName)
        }

        val docId = UUID.randomUUID().toString()
        val finalTitle = customTitle?.ifBlank { null } ?: parsed.title

        val docEntity = DocumentEntity(
            id = docId,
            title = finalTitle,
            fileName = fileName,
            mimeType = parsed.mimeType,
            fileSizeBytes = parsed.fileSizeBytes,
            pageCount = parsed.pageCount,
            chunkCount = parsed.chunks.size,
            summary = parsed.summary,
            createdAt = System.currentTimeMillis()
        )

        val chunkEntities = parsed.chunks.map { chunk ->
            DocumentChunkEntity(
                id = UUID.randomUUID().toString(),
                documentId = docId,
                chunkIndex = chunk.chunkIndex,
                pageNumber = chunk.pageNumber,
                sectionTitle = chunk.sectionTitle,
                rawText = chunk.rawText,
                tokenEstimate = chunk.tokenEstimate,
                keywords = chunk.keywords,
                createdAt = System.currentTimeMillis()
            )
        }

        repository.saveDocumentWithChunks(docEntity, chunkEntities)
        docEntity
    }

    /**
     * Answer a question grounded in imported documents with explicit page citations.
     */
    suspend fun askQuestionAcrossDocuments(
        query: String,
        documentIds: List<String>
    ): String = withContext(Dispatchers.IO) {
        val chunks = if (documentIds.isEmpty()) {
            val allDocs = repository.getAllDocumentsFlow().first()
            if (allDocs.isEmpty()) return@withContext "No documents imported yet. Please import a PDF, EPUB, or note first."
            repository.getChunksForDocuments(allDocs.map { it.id })
        } else {
            repository.getChunksForDocuments(documentIds)
        }

        val ranked = LocalDocumentSearchIndex.search(query, chunks, topK = 6)
        if (ranked.isEmpty()) {
            return@withContext "No matching information found in the imported documents for: \"$query\""
        }

        val securityGuard = UntrustedDocumentSanitizer.getSecurityPromptGuard()
        val contextBuilder = StringBuilder()
        ranked.forEach { r ->
            contextBuilder.appendLine(
                UntrustedDocumentSanitizer.wrapInUntrustedXml(
                    docId = r.chunk.documentId,
                    pageNumber = r.chunk.pageNumber,
                    sectionTitle = r.chunk.sectionTitle,
                    content = r.chunk.rawText
                )
            )
            contextBuilder.appendLine()
        }

        val prefs = preferencesRepository.userPreferencesFlow.first()
        val prompt = """
        $securityGuard
        
        SOURCE DOCUMENTS:
        $contextBuilder
        
        USER QUESTION:
        $query
        
        INSTRUCTIONS:
        1. Answer the user's question accurately using ONLY facts established in the provided source excerpts.
        2. Attribute your statements with inline citations in brackets, e.g. [Page ${ranked.first().chunk.pageNumber}, ${ranked.first().chunk.sectionTitle}].
        3. If the sources do not provide sufficient information, explicitly state what is missing instead of guessing or hallucinating.
        4. Maintain an objective, educational tone with clear markdown formatting.
        """.trimIndent()

        multiAiClient.generateContent(
            systemInstruction = prefs.customSystemInstructions,
            prompt = prompt,
            isJson = false,
            taskType = AiTaskType.REFINEMENT_QA
        )
    }

    /**
     * Synthesize a structured lesson grounded directly in an imported document.
     */
    suspend fun generateSourceGroundedLesson(
        docId: String,
        topic: String
    ): String = withContext(Dispatchers.IO) {
        val doc = repository.getDocumentById(docId) ?: return@withContext "Document not found."
        val chunks = repository.getChunksForDocument(docId)
        val ranked = LocalDocumentSearchIndex.search(topic, chunks, topK = 8)
        val sourceChunks = if (ranked.isNotEmpty()) ranked.map { it.chunk } else chunks.take(8)

        val securityGuard = UntrustedDocumentSanitizer.getSecurityPromptGuard()
        val contextBuilder = StringBuilder()
        sourceChunks.forEach { chunk ->
            contextBuilder.appendLine(
                UntrustedDocumentSanitizer.wrapInUntrustedXml(
                    docId = chunk.documentId,
                    pageNumber = chunk.pageNumber,
                    sectionTitle = chunk.sectionTitle,
                    content = chunk.rawText
                )
            )
        }

        val prefs = preferencesRepository.userPreferencesFlow.first()
        val prompt = """
        $securityGuard
        
        SOURCE DOCUMENT: "${doc.title}"
        SOURCE EXCERPTS:
        $contextBuilder
        
        TOPIC TO TEACH:
        $topic
        
        REQUIREMENTS:
        1. Synthesize a comprehensive, structured study lesson on "$topic" grounded in the source text.
        2. Every major concept, formula, or historical claim MUST include an inline citation to the source page, e.g. [Page X].
        3. Structure the lesson with:
           - # Overview & Key Ideas
           - ## Core Mechanisms & Principles
           - ## Worked Example / Analysis
           - ## Source Summary & Citations
        4. Do NOT hallucinate claims beyond the provided text.
        """.trimIndent()

        multiAiClient.generateContent(
            systemInstruction = prefs.customSystemInstructions,
            prompt = prompt,
            isJson = false,
            taskType = AiTaskType.CHAPTER_LESSON
        )
    }

    /**
     * Extract key definitions and glossary terms from a document.
     */
    suspend fun extractGlossaryDefinitions(docId: String): List<DefinitionItem> = withContext(Dispatchers.IO) {
        val chunks = repository.getChunksForDocument(docId)
        if (chunks.isEmpty()) return@withContext emptyList()

        val sample = chunks.take(10)
        val context = sample.joinToString("\n\n") {
            "[Page ${it.pageNumber}, ${it.sectionTitle}]:\n${UntrustedDocumentSanitizer.sanitize(it.rawText.take(500))}"
        }

        val prompt = """
        Extract 5 to 10 important technical or conceptual terms and definitions from the text below.
        
        TEXT:
        $context
        
        Return ONLY valid JSON matching this schema:
        {
          "definitions": [
            {
              "term": "Term Name",
              "definition": "Precise definition according to the text",
              "pageNumber": 1,
              "sectionTitle": "Section name"
            }
          ]
        }
        """.trimIndent()

        try {
            val response = multiAiClient.generateContent(
                systemInstruction = "You are an academic lexicographer.",
                prompt = prompt,
                isJson = true,
                taskType = AiTaskType.FLASHCARDS_QUIZ
            )
            json.decodeFromString<GlossaryExtractionResponse>(response).definitions
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Automatically generate flashcards grounded in document chunks.
     */
    suspend fun generateDocumentFlashcards(
        docId: String,
        pathId: String,
        chapterId: String
    ): List<FlashcardEntity> = withContext(Dispatchers.IO) {
        val chunks = repository.getChunksForDocument(docId)
        if (chunks.isEmpty()) return@withContext emptyList()

        val sample = chunks.take(8)
        val context = sample.joinToString("\n\n") {
            "[Page ${it.pageNumber}]: ${UntrustedDocumentSanitizer.sanitize(it.rawText.take(400))}"
        }

        val prompt = """
        Generate 6 high-yield active recall flashcard Q&As directly from these document excerpts:
        
        $context
        
        Return strictly valid JSON:
        {
          "flashcards": [
            {
              "front": "Clear question testing one concept",
              "back": "Concise answer citing [Page X]",
              "pageNumber": 1
            }
          ]
        }
        """.trimIndent()

        try {
            val response = multiAiClient.generateContent(
                systemInstruction = "You are a flashcard creator for university students.",
                prompt = prompt,
                isJson = true,
                taskType = AiTaskType.FLASHCARDS_QUIZ
            )
            val parsed = json.decodeFromString<DocumentFlashcardResponse>(response)
            parsed.flashcards.map { item ->
                FlashcardEntity(
                    id = UUID.randomUUID().toString(),
                    chapterId = chapterId,
                    pathId = pathId,
                    front = item.front,
                    back = item.back,
                    intervalDays = 1,
                    easeFactor = 2.5f,
                    repetitions = 0,
                    nextReviewTimestamp = System.currentTimeMillis()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Detect contradictions or discrepancies across multiple imported documents.
     */
    suspend fun detectContradictions(
        documentIds: List<String>,
        topic: String
    ): String = withContext(Dispatchers.IO) {
        val chunks = repository.getChunksForDocuments(documentIds)
        val ranked = LocalDocumentSearchIndex.search(topic, chunks, topK = 8)
        if (ranked.isEmpty()) return@withContext "No sufficient data found across documents to compare."

        val contextBuilder = StringBuilder()
        ranked.forEach { r ->
            contextBuilder.appendLine(
                UntrustedDocumentSanitizer.wrapInUntrustedXml(
                    docId = r.chunk.documentId,
                    pageNumber = r.chunk.pageNumber,
                    sectionTitle = r.chunk.sectionTitle,
                    content = r.chunk.rawText
                )
            )
        }

        val prompt = """
        ${UntrustedDocumentSanitizer.getSecurityPromptGuard()}
        
        Compare the following excerpts on the topic "$topic":
        $contextBuilder
        
        Analyze:
        1. Are there contradictory factual claims, differing statistics, or opposing theoretical frameworks?
        2. Detail each contradiction clearly with citations to the respective pages.
        3. If there are no contradictions and the sources agree, explain how they corroborate each other.
        """.trimIndent()

        multiAiClient.generateContent(
            systemInstruction = "You are an objective academic auditor.",
            prompt = prompt,
            isJson = false,
            taskType = AiTaskType.REFINEMENT_QA
        )
    }
}
