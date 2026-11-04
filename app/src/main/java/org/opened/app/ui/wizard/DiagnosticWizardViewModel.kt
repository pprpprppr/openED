package org.opened.app.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.formatUserFriendlyAiError
import javax.inject.Inject

@HiltViewModel
class DiagnosticWizardViewModel @Inject constructor(
    private val aiLearningEngine: AiLearningEngine
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        _uiState.update {
            it.copy(
                isBusy = false,
                errorMessage = formatUserFriendlyAiError(throwable)
            )
        }
    }

    private val _uiState = MutableStateFlow(DiagnosticWizardUiState())
    val uiState: StateFlow<DiagnosticWizardUiState> = _uiState.asStateFlow()

    fun setTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, errorMessage = null) }
    }

    fun setUserLevel(level: String) {
        _uiState.update { it.copy(userLevel = level) }
    }

    fun startDiagnosticInterview() {
        val topic = _uiState.value.topic.trim()
        if (topic.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a subject or topic.") }
            return
        }

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    currentStep = WizardStep.GENERATING_QUESTIONS,
                    isBusy = true,
                    errorMessage = null
                )
            }

            try {
                val result = aiLearningEngine.generateDiagnosticQuestions(topic)
                result.fold(
                    onSuccess = { questions ->
                        _uiState.update {
                            it.copy(
                                questions = questions,
                                currentStep = WizardStep.ANSWERING_QUESTIONS,
                                isBusy = false
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                currentStep = WizardStep.ENTER_TOPIC,
                                isBusy = false,
                                errorMessage = formatUserFriendlyAiError(error.localizedMessage ?: "Failed to generate diagnostic interview.")
                            )
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        currentStep = WizardStep.ENTER_TOPIC,
                        isBusy = false,
                        errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to generate diagnostic interview.")
                    )
                }
            }
        }
    }

    fun toggleAnswerOption(questionId: String, option: String, isMultiSelect: Boolean) {
        _uiState.update { state ->
            val currentList = state.selectedAnswers[questionId].orEmpty()
            val updatedList = if (isMultiSelect) {
                if (currentList.contains(option)) {
                    currentList - option
                } else {
                    currentList + option
                }
            } else {
                listOf(option)
            }
            state.copy(selectedAnswers = state.selectedAnswers + (questionId to updatedList))
        }
    }

    fun setCustomAnswer(questionId: String, text: String) {
        _uiState.update { state ->
            state.copy(customTextAnswers = state.customTextAnswers + (questionId to text))
        }
    }

    fun generateSyllabus() {
        val state = _uiState.value
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    currentStep = WizardStep.GENERATING_SYLLABUS,
                    isBusy = true,
                    errorMessage = null
                )
            }

            try {
                // Combine chip selections and custom text answers
                val compiledAnswers = mutableMapOf<String, List<String>>()
                state.questions.forEach { q ->
                    val selected = state.selectedAnswers[q.id].orEmpty().toMutableList()
                    val custom = state.customTextAnswers[q.id]?.trim().orEmpty()
                    if (custom.isNotBlank()) {
                        selected.add("Self note: $custom")
                    }
                    if (selected.isNotEmpty()) {
                        compiledAnswers[q.question] = selected
                    }
                }

                val result = aiLearningEngine.generateSyllabus(
                    topic = state.topic,
                    userLevel = state.userLevel,
                    answers = compiledAnswers
                )

                result.fold(
                    onSuccess = { syllabus ->
                        _uiState.update {
                            it.copy(
                                generatedSyllabus = syllabus,
                                currentStep = WizardStep.REVIEW_SYLLABUS,
                                isBusy = false
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                currentStep = WizardStep.ANSWERING_QUESTIONS,
                                isBusy = false,
                                errorMessage = formatUserFriendlyAiError(error.localizedMessage ?: "Failed to generate syllabus.")
                            )
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        currentStep = WizardStep.ANSWERING_QUESTIONS,
                        isBusy = false,
                        errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to generate syllabus.")
                    )
                }
            }
        }
    }

    fun saveAndStartCourse(onSuccess: (String) -> Unit) {
        val baseSyllabus = _uiState.value.generatedSyllabus ?: return
        val syllabus = baseSyllabus.copy(userLevel = _uiState.value.userLevel)
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                val pathId = aiLearningEngine.persistGeneratedSyllabus(syllabus)
                _uiState.update { it.copy(isBusy = false, createdPathId = pathId) }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onSuccess(pathId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to save course.")
                    )
                }
            }
        }
    }

    fun stepBack() {
        _uiState.update {
            when (it.currentStep) {
                WizardStep.ANSWERING_QUESTIONS -> it.copy(currentStep = WizardStep.ENTER_TOPIC)
                WizardStep.REVIEW_SYLLABUS -> it.copy(currentStep = WizardStep.ANSWERING_QUESTIONS)
                else -> it
            }
        }
    }
}
