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
import org.opened.app.core.ai.AiLearningEngine
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
    val isLoading: Boolean = true,
    val isGeneratingQuestions: Boolean = false,
    val generationProgressText: String = "",
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
    private val repository: OpenEDRepository,
    private val aiLearningEngine: AiLearningEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TakeMockTestUiState())
    val uiState: StateFlow<TakeMockTestUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var questionGenJob: Job? = null

    fun loadTest(testId: String) {
        if (_uiState.value.testId == testId && _uiState.value.testWithQuestions != null && _uiState.value.totalQuestions > 0) {
            return
        }

        _uiState.update { it.copy(testId = testId, isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // Immediate direct load for instantaneous response
                val directTest = repository.getMockTestById(testId)
                val directQuestions = repository.getQuestionsForTest(testId)
                var directTwq = if (directTest != null) {
                    MockTestWithQuestions(directTest, directQuestions)
                } else {
                    repository.getMockTestWithQuestions(testId)
                }

                if (directTwq != null && directTwq.questions.isEmpty() && directQuestions.isNotEmpty()) {
                    directTwq = directTwq.copy(questions = directQuestions)
                }

                if (directTwq != null) {
                    applyTestLoaded(directTwq)
                }

                // Continuous Flow collection for live updates
                repository.getMockTestWithQuestionsFlow(testId).collect { flowTwq ->
                    val effectiveTwq = if (flowTwq != null && flowTwq.questions.isEmpty() && directQuestions.isNotEmpty()) {
                        flowTwq.copy(questions = directQuestions)
                    } else {
                        flowTwq ?: directTwq
                    }

                    if (effectiveTwq != null) {
                        applyTestLoaded(effectiveTwq)
                    } else if (directTwq == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Mock exam not found. It may have been deleted."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load mock exam."
                    )
                }
            }
        }
    }

    fun generateMissingQuestions() {
        val testId = _uiState.value.testId.ifBlank {
            _uiState.value.testWithQuestions?.test?.id.orEmpty()
        }
        if (testId.isBlank()) return

        _uiState.update {
            it.copy(
                isGeneratingQuestions = true,
                generationProgressText = "Preparing exam questions...",
                errorMessage = null
            )
        }

        questionGenJob?.cancel()
        questionGenJob = viewModelScope.launch {
            try {
                val result = aiLearningEngine.generateQuestionsForExistingTest(
                    testId = testId,
                    onProgress = { currentBatch, totalBatches, questionsGenerated ->
                        _uiState.update {
                            it.copy(
                                generationProgressText = "Generating Part $currentBatch of $totalBatches ($questionsGenerated questions ready)..."
                            )
                        }
                    }
                )

                result.fold(
                    onSuccess = { questions ->
                        val test = repository.getMockTestById(testId)
                        if (test != null) {
                            val twq = MockTestWithQuestions(test, questions)
                            applyTestLoaded(twq)
                        }
                        _uiState.update {
                            it.copy(
                                isGeneratingQuestions = false,
                                generationProgressText = "",
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isGeneratingQuestions = false,
                                generationProgressText = "",
                                errorMessage = error.localizedMessage ?: "Failed to generate questions. Please try again."
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingQuestions = false,
                        generationProgressText = "",
                        errorMessage = e.localizedMessage ?: "Failed to generate questions. Please try again."
                    )
                }
            }
        }
    }

    fun cancelGeneration() {
        questionGenJob?.cancel()
        questionGenJob = null
        _uiState.update {
            it.copy(
                isGeneratingQuestions = false,
                generationProgressText = ""
            )
        }
    }

    private suspend fun applyTestLoaded(twq: MockTestWithQuestions) {
        val test = twq.test
        val sortedQuestions = twq.questions.sortedBy { it.questionIndex }
        val timeLimitSec = (test.timeLimitMinutes.coerceAtLeast(1)) * 60
        val initialRemaining = if (test.timeSpentSeconds > 0) {
            (timeLimitSec - test.timeSpentSeconds).coerceAtLeast(0)
        } else {
            timeLimitSec
        }

        _uiState.update { state ->
            val curRemaining = if (state.remainingSeconds > 0) state.remainingSeconds else initialRemaining
            state.copy(
                testWithQuestions = twq.copy(questions = sortedQuestions),
                remainingSeconds = curRemaining,
                isLoading = false,
                errorMessage = if (sortedQuestions.isEmpty()) "This mock exam has no questions generated yet." else null
            )
        }

        if (sortedQuestions.isNotEmpty() && test.status == MockTestStatus.NOT_STARTED) {
            repository.updateMockTest(test.copy(status = MockTestStatus.IN_PROGRESS))
        }

        if (timerJob == null && test.status != MockTestStatus.COMPLETED && sortedQuestions.isNotEmpty()) {
            startTimer()
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
            try {
                val questions = twq.questions
                val totalQuestions = questions.size
                var totalCorrect = 0
                var totalAnswered = 0

                // Load course chapters if available so mistake records reference a valid chapterId foreign key
                val pathDetails = runCatching { repository.getStudyPathWithModules(twq.test.pathId) }.getOrNull()
                val allChapters = pathDetails?.modules?.flatMap { it.chapters }.orEmpty()

                for (q in questions) {
                    if (q.userSelectedOption >= 0) {
                        totalAnswered++
                        if (q.userSelectedOption == q.correctOptionIndex) {
                            totalCorrect++
                        } else {
                            // Find a valid chapter in the course or fallback chapter to satisfy foreign key constraint
                            val validChapter = allChapters.firstOrNull { ch ->
                                ch.title.contains(q.topic, ignoreCase = true) || q.topic.contains(ch.title, ignoreCase = true)
                            } ?: allChapters.firstOrNull()

                            if (validChapter != null) {
                                val userSelectedText = q.optionsList.getOrNull(q.userSelectedOption).orEmpty()
                                val correctText = q.optionsList.getOrNull(q.correctOptionIndex).orEmpty()
                                runCatching {
                                    repository.recordMistake(
                                        MistakeRecordEntity(
                                            id = UUID.randomUUID().toString(),
                                            chapterId = validChapter.id,
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onCompleted(twq.test.id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "Error saving exam results: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
