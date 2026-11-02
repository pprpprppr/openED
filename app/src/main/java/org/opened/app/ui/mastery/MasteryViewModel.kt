package org.opened.app.ui.mastery

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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opened.app.core.ai.AdaptiveQuizResponse
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.FeynmanEvaluationResponse
import org.opened.app.core.database.entity.MasteryQuizEntity
import org.opened.app.domain.repository.OpenEDRepository
import java.util.UUID
import javax.inject.Inject

data class MasteryUiState(
    val selectedTabIndex: Int = 0,
    // --- Quiz Tab ---
    val quizLoading: Boolean = false,
    val quizResult: AdaptiveQuizResponse? = null,
    val quizAnswers: Map<Int, Int> = emptyMap(), // questionIndex -> selectedOptionIndex
    val quizSubmitted: Boolean = false,
    val quizScore: Int = 0,
    // --- Feynman Tab ---
    val feynmanText: String = "",
    val feynmanLoading: Boolean = false,
    val feynmanResult: FeynmanEvaluationResponse? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MasteryViewModel @Inject constructor(
    private val aiEngine: AiLearningEngine,
    private val repository: OpenEDRepository
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        _uiState.update {
            it.copy(
                quizLoading = false,
                feynmanLoading = false,
                errorMessage = throwable.localizedMessage ?: "Failed to process mastery challenge."
            )
        }
    }

    private val _uiState = MutableStateFlow(MasteryUiState())
    val uiState: StateFlow<MasteryUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTabIndex = index) }

    fun generateQuiz(chapterId: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(quizLoading = true, errorMessage = null, quizResult = null, quizSubmitted = false, quizAnswers = emptyMap()) }
            try {
                aiEngine.generateAdaptiveQuiz(chapterId)
                    .onSuccess { quiz ->
                        _uiState.update { it.copy(quizLoading = false, quizResult = quiz) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(quizLoading = false, errorMessage = e.message ?: "Failed to generate quiz") }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(quizLoading = false, errorMessage = e.localizedMessage ?: "Failed to generate quiz") }
            }
        }
    }

    fun answerQuestion(questionIndex: Int, optionIndex: Int) {
        if (_uiState.value.quizSubmitted) return
        _uiState.update { it.copy(quizAnswers = it.quizAnswers + (questionIndex to optionIndex)) }
    }

    fun submitQuiz(chapterId: String) {
        val state = _uiState.value
        val quiz = state.quizResult ?: return
        val correctCount = quiz.questions.mapIndexed { i, q ->
            if (state.quizAnswers[i] == q.correctIndex) 1 else 0
        }.sum()
        val score = if (quiz.questions.isEmpty()) 0 else (correctCount * 100) / quiz.questions.size
        _uiState.update { it.copy(quizSubmitted = true, quizScore = score) }

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                val entity = MasteryQuizEntity(
                    id = UUID.randomUUID().toString(),
                    chapterId = chapterId,
                    quizType = "QUIZ",
                    sessionDataJson = json.encodeToString(quiz),
                    score = score
                )
                repository.saveMasteryQuiz(entity)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to save quiz result.") }
            }
        }
    }

    fun updateFeynmanText(text: String) = _uiState.update { it.copy(feynmanText = text) }

    fun evaluateFeynman(chapterId: String) {
        val text = _uiState.value.feynmanText.trim()
        if (text.isBlank()) return
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(feynmanLoading = true, errorMessage = null, feynmanResult = null) }
            try {
                aiEngine.evaluateFeynmanExplanation(chapterId, text)
                    .onSuccess { result ->
                        _uiState.update { it.copy(feynmanLoading = false, feynmanResult = result) }
                        val entity = MasteryQuizEntity(
                            id = UUID.randomUUID().toString(),
                            chapterId = chapterId,
                            quizType = "FEYNMAN",
                            sessionDataJson = json.encodeToString(result),
                            score = result.score
                        )
                        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
                            try { repository.saveMasteryQuiz(entity) } catch (_: Exception) {}
                        }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(feynmanLoading = false, errorMessage = e.message ?: "Failed to evaluate explanation") }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(feynmanLoading = false, errorMessage = e.localizedMessage ?: "Failed to evaluate explanation") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
