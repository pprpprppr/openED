package org.opened.app.ui.mocktest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.database.entity.MistakeRecordEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.model.MockTestWithQuestions
import org.opened.app.domain.repository.OpenEDRepository
import java.util.UUID
import javax.inject.Inject

data class TakeMockTestUiState(
    val testId: String = "",
    val testWithQuestions: MockTestWithQuestions? = null,
    val currentQuestionIndex: Int = 0,
    val remainingSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val showPalette: Boolean = false,
    val showSubmitConfirmDialog: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val currentQuestion: MockTestQuestionEntity?
        get() = testWithQuestions?.questions?.getOrNull(currentQuestionIndex)

    val totalQuestions: Int
        get() = testWithQuestions?.questions?.size ?: 0

    val answeredCount: Int
        get() = testWithQuestions?.questions?.count { it.userSelectedOption >= 0 } ?: 0

    val flaggedCount: Int
        get() = testWithQuestions?.questions?.count { it.isFlagged } ?: 0
}

@HiltViewModel
class TakeMockTestViewModel @Inject constructor(
    private val repository: OpenEDRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TakeMockTestUiState())
    val uiState: StateFlow<TakeMockTestUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun loadTest(testId: String) {
        _uiState.update { it.copy(testId = testId) }

        viewModelScope.launch {
            repository.getMockTestWithQuestionsFlow(testId).collectLatest { twq ->
                if (twq == null) return@collectLatest

                val test = twq.test
                val timeLimitSec = test.timeLimitMinutes * 60
                val initialRemaining = if (test.timeSpentSeconds > 0) {
                    (timeLimitSec - test.timeSpentSeconds).coerceAtLeast(0)
                } else {
                    timeLimitSec
                }

                _uiState.update { state ->
                    state.copy(
                        testWithQuestions = twq,
                        remainingSeconds = if (state.remainingSeconds > 0) state.remainingSeconds else initialRemaining
                    )
                }

                if (test.status == MockTestStatus.NOT_STARTED) {
                    repository.updateMockTest(test.copy(status = MockTestStatus.IN_PROGRESS))
                }

                if (timerJob == null && test.status != MockTestStatus.COMPLETED) {
                    startTimer()
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(isTimerRunning = true) }
            while (_uiState.value.remainingSeconds > 0) {
                delay(1000L)
                _uiState.update { state ->
                    val newRemaining = (state.remainingSeconds - 1).coerceAtLeast(0)
                    state.copy(remainingSeconds = newRemaining)
                }
            }
            _uiState.update { it.copy(isTimerRunning = false) }
            // Auto submit when time expires
            submitTest { /* navigated by screen */ }
        }
    }

    fun selectQuestion(index: Int) {
        val total = _uiState.value.totalQuestions
        if (index in 0 until total) {
            _uiState.update { it.copy(currentQuestionIndex = index, showPalette = false) }
        }
    }

    fun nextQuestion() {
        val current = _uiState.value.currentQuestionIndex
        val total = _uiState.value.totalQuestions
        if (current < total - 1) {
            _uiState.update { it.copy(currentQuestionIndex = current + 1) }
        }
    }

    fun prevQuestion() {
        val current = _uiState.value.currentQuestionIndex
        if (current > 0) {
            _uiState.update { it.copy(currentQuestionIndex = current - 1) }
        }
    }

    fun selectOption(optionIndex: Int) {
        val question = _uiState.value.currentQuestion ?: return
        val currentTwq = _uiState.value.testWithQuestions ?: return

        // Update in-memory state immediately for instant responsive feedback
        val updatedQuestions = currentTwq.questions.map { q ->
            if (q.id == question.id) q.copy(userSelectedOption = optionIndex) else q
        }
        val updatedAnsweredCount = updatedQuestions.count { it.userSelectedOption >= 0 }
        val updatedTest = currentTwq.test.copy(totalAnswered = updatedAnsweredCount)

        _uiState.update {
            it.copy(testWithQuestions = currentTwq.copy(test = updatedTest, questions = updatedQuestions))
        }

        // Persist to Room
        viewModelScope.launch {
            repository.answerMockTestQuestion(question.id, optionIndex)
            repository.updateMockTest(updatedTest)
        }
    }

    fun clearCurrentAnswer() {
        val question = _uiState.value.currentQuestion ?: return
        val currentTwq = _uiState.value.testWithQuestions ?: return

        val updatedQuestions = currentTwq.questions.map { q ->
            if (q.id == question.id) q.copy(userSelectedOption = -1) else q
        }
        val updatedAnsweredCount = updatedQuestions.count { it.userSelectedOption >= 0 }
        val updatedTest = currentTwq.test.copy(totalAnswered = updatedAnsweredCount)

        _uiState.update {
            it.copy(testWithQuestions = currentTwq.copy(test = updatedTest, questions = updatedQuestions))
        }

        viewModelScope.launch {
            repository.answerMockTestQuestion(question.id, -1)
            repository.updateMockTest(updatedTest)
        }
    }

    fun toggleFlag() {
        val question = _uiState.value.currentQuestion ?: return
        val currentTwq = _uiState.value.testWithQuestions ?: return
        val newFlag = !question.isFlagged

        val updatedQuestions = currentTwq.questions.map { q ->
            if (q.id == question.id) q.copy(isFlagged = newFlag) else q
        }

        _uiState.update {
            it.copy(testWithQuestions = currentTwq.copy(questions = updatedQuestions))
        }

        viewModelScope.launch {
            repository.toggleQuestionFlag(question.id, newFlag)
        }
    }

    fun openPalette() {
        _uiState.update { it.copy(showPalette = true) }
    }

    fun closePalette() {
        _uiState.update { it.copy(showPalette = false) }
    }

    fun showSubmitConfirmation() {
        _uiState.update { it.copy(showSubmitConfirmDialog = true) }
    }

    fun dismissSubmitConfirmation() {
        _uiState.update { it.copy(showSubmitConfirmDialog = false) }
    }

    fun submitTest(onCompleted: (String) -> Unit) {
        val twq = _uiState.value.testWithQuestions ?: return
        timerJob?.cancel()

        _uiState.update { it.copy(isSubmitting = true, showSubmitConfirmDialog = false) }

        viewModelScope.launch {
            val questions = twq.questions
            val totalQuestions = questions.size
            var totalCorrect = 0
            var totalAnswered = 0

            for (q in questions) {
                if (q.userSelectedOption >= 0) {
                    totalAnswered++
                    if (q.userSelectedOption == q.correctOptionIndex) {
                        totalCorrect++
                    } else {
                        // Record mistake for adaptive mastery loop
                        val userSelectedText = q.optionsList.getOrNull(q.userSelectedOption).orEmpty()
                        val correctText = q.optionsList.getOrNull(q.correctOptionIndex).orEmpty()
                        repository.recordMistake(
                            MistakeRecordEntity(
                                id = UUID.randomUUID().toString(),
                                chapterId = twq.test.id,
                                pathId = twq.test.pathId,
                                conceptKey = q.topic,
                                questionPrompt = q.questionText,
                                userSelectedAnswer = userSelectedText,
                                correctAnswer = correctText,
                                explanation = q.explanation,
                                failureCount = 1,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            val scorePercentage = if (totalQuestions > 0) {
                ((totalCorrect.toFloat() / totalQuestions.toFloat()) * 100).toInt()
            } else {
                0
            }

            val totalLimitSec = twq.test.timeLimitMinutes * 60
            val spentSec = (totalLimitSec - _uiState.value.remainingSeconds).coerceAtLeast(0)

            val completedTest = twq.test.copy(
                status = MockTestStatus.COMPLETED,
                score = scorePercentage,
                totalCorrect = totalCorrect,
                totalAnswered = totalAnswered,
                timeSpentSeconds = spentSec,
                completedAt = System.currentTimeMillis()
            )

            repository.updateMockTest(completedTest)

            _uiState.update { it.copy(isSubmitting = false) }
            onCompleted(twq.test.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
