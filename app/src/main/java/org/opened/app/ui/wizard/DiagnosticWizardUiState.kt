package org.opened.app.ui.wizard

import androidx.compose.runtime.Immutable
import org.opened.app.core.ai.DiagnosticQuestion
import org.opened.app.core.ai.SyllabusResponse

enum class WizardStep {
    ENTER_TOPIC,
    GENERATING_QUESTIONS,
    ANSWERING_QUESTIONS,
    GENERATING_SYLLABUS,
    REVIEW_SYLLABUS
}

@Immutable
data class DiagnosticWizardUiState(
    val currentStep: WizardStep = WizardStep.ENTER_TOPIC,
    val topic: String = "",
    val userLevel: String = "BEGINNER",
    val questions: List<DiagnosticQuestion> = emptyList(),
    val selectedAnswers: Map<String, List<String>> = emptyMap(), // questionId -> list of selected options
    val customTextAnswers: Map<String, String> = emptyMap(), // questionId -> custom text
    val generatedSyllabus: SyllabusResponse? = null,
    val createdPathId: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val activeProviderName: String = "AI"
)
