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
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.TutorPersonaRegistry
import org.opened.app.core.ai.formatUserFriendlyAiError
import org.opened.app.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class DiagnosticWizardViewModel @Inject constructor(
    private val aiLearningEngine: AiLearningEngine,
    private val preferencesRepository: UserPreferencesRepository
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val provider = AiProvider.fromId(prefs.selectedProvider)
            _uiState.update {
                it.copy(
                    activeProviderName = provider.displayName,
                    selectedTutorPersonaId = prefs.selectedTutorPersona,
                    customInstructions = prefs.customSystemInstructions
                )
            }
        }
    }

    fun setTopic(topic: String) {
        _uiState.update { current ->
            val detectedPersonaId = if (!current.isPersonaUserSelected && topic.isNotBlank()) {
                TutorPersonaRegistry.detectMatchingPersona(topic).id
            } else {
                current.selectedTutorPersonaId
            }
            current.copy(
                topic = topic,
                selectedTutorPersonaId = detectedPersonaId,
                errorMessage = null
            )
        }
    }

    fun selectTutorPersona(personaId: String) {
        _uiState.update {
            it.copy(
                selectedTutorPersonaId = personaId,
                isPersonaUserSelected = true
            )
        }
    }

    fun setCustomInstructions(instructions: String) {
        _uiState.update { it.copy(customInstructions = instructions) }
    }

    fun toggleCustomInstructions() {
        _uiState.update { it.copy(isCustomInstructionsExpanded = !it.isCustomInstructionsExpanded) }
    }

    fun toggleCustomTemplate(templateText: String) {
        val snippet = templateText.trim()
        val current = _uiState.value.customInstructions.trim()
        val isPresent = current.contains(snippet, ignoreCase = true)
        val updated = if (isPresent) {
            current.replace(snippet, "")
                .lines()
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .trim()
        } else {
            if (current.isBlank()) {
                snippet
            } else {
                "$current\n\n$snippet"
            }
        }
        _uiState.update {
            it.copy(
                customInstructions = updated,
                isCustomInstructionsExpanded = true
            )
        }
    }

    fun generateCustomPromptFromTopic() {
        val topic = _uiState.value.topic.trim()
        val customExisting = _uiState.value.customInstructions.trim()
        val persona = TutorPersonaRegistry.getById(_uiState.value.selectedTutorPersonaId)
        val userNotes = if (customExisting.isNotBlank()) {
            customExisting
        } else if (topic.isNotBlank()) {
            "Specialized curriculum on '$topic' for a ${_uiState.value.userLevel.lowercase()} student. Provide step-by-step clarity, real-world examples, active recall, and rigorous domain intuition."
        } else {
            "Comprehensive, step-by-step mentorship in ${persona.title}."
        }
        val targetSubject = if (topic.isNotBlank()) "$topic (${persona.title})" else persona.title

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isGeneratingPrompt = true, promptGenerationError = null) }
            val result = aiLearningEngine.generateSystemInstruction(
                userNotes = userNotes,
                targetSubject = targetSubject
            )
            result.onSuccess { generatedPrompt ->
                _uiState.update {
                    it.copy(
                        isGeneratingPrompt = false,
                        customInstructions = generatedPrompt,
                        isCustomInstructionsExpanded = true
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingPrompt = false,
                        promptGenerationError = formatUserFriendlyAiError(err.localizedMessage ?: "Failed to generate prompt.")
                    )
                }
            }
        }
    }

    fun setUserLevel(level: String) {
        _uiState.update { it.copy(userLevel = level) }
    }

    fun startDiagnosticInterview() {
        val state = _uiState.value
        val topic = state.topic.trim()
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
                val result = aiLearningEngine.generateDiagnosticQuestions(
                    topic = topic,
                    tutorPersonaOverride = state.selectedTutorPersonaId,
                    customInstructionsOverride = state.customInstructions
                )
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
                    answers = compiledAnswers,
                    tutorPersonaOverride = state.selectedTutorPersonaId,
                    customInstructionsOverride = state.customInstructions
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
