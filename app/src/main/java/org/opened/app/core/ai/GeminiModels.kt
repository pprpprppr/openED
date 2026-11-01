package org.opened.app.core.ai

import kotlinx.serialization.Serializable

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiErrorDetails? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiErrorDetails(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

// --- Structured JSON Domain Models ---

@Serializable
data class DiagnosticQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val isMultiSelect: Boolean = false,
    val rationale: String = ""
)

@Serializable
data class DiagnosticQuestionsResponse(
    val questions: List<DiagnosticQuestion> = emptyList()
)

@Serializable
data class SyllabusChapterItem(
    val title: String,
    val estimatedMinutes: Int = 15,
    val summary: String,
    val keyConcepts: List<String> = emptyList()
)

@Serializable
data class SyllabusModuleItem(
    val title: String,
    val summary: String,
    val chapters: List<SyllabusChapterItem>
)

@Serializable
data class SyllabusResponse(
    val title: String,
    val topic: String,
    val userLevel: String, // BEGINNER, INTERMEDIATE, ADVANCED
    val targetAudience: String,
    val estimatedTotalHours: Double,
    val prerequisites: List<String> = emptyList(),
    val modules: List<SyllabusModuleItem>
)

enum class RefineAction {
    SIMPLIFY,
    ADD_CODE_MATH,
    FACT_CHECK_CORRECT,
    CUSTOM
}
