package org.opened.app.core.ai

import kotlinx.serialization.Serializable

// ---- Flashcard generation ----

@Serializable
data class FlashcardGenerationResponse(
    val flashcards: List<FlashcardItem> = emptyList()
)

@Serializable
data class FlashcardItem(
    val front: String = "",
    val back: String = ""
)

// ---- Adaptive Quiz ----

@Serializable
data class AdaptiveQuizResponse(
    val questions: List<QuizQuestion> = emptyList()
)

@Serializable
data class QuizQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = ""
)

// ---- Feynman Evaluation ----

@Serializable
data class FeynmanEvaluationResponse(
    val score: Int = 0,
    val whatYouGotRight: List<String> = emptyList(),
    val missingConcepts: List<String> = emptyList(),
    val refinedExplanation: String = ""
)
