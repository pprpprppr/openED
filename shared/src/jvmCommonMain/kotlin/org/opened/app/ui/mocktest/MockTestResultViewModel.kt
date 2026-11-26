package org.opened.app.ui.mocktest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.model.MockTestWithQuestions
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

enum class ResultFilter(val label: String) {
    ALL("All Questions"),
    INCORRECT("Incorrect"),
    CORRECT("Correct"),
    FLAGGED("Flagged")
}

data class TopicMasteryItem(
    val topic: String,
    val correctCount: Int,
    val totalCount: Int
) {
    val accuracyPercentage: Int
        get() = if (totalCount > 0) ((correctCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 0
}

data class MockTestResultUiState(
    val testId: String = "",
    val testWithQuestions: MockTestWithQuestions? = null,
    val selectedFilter: ResultFilter = ResultFilter.ALL,
    val isLoading: Boolean = false
) {
    val questions: List<MockTestQuestionEntity>
        get() = testWithQuestions?.questions.orEmpty()

    val filteredQuestions: List<MockTestQuestionEntity>
        get() = when (selectedFilter) {
            ResultFilter.ALL -> questions
            ResultFilter.INCORRECT -> questions.filter { !it.isCorrect }
            ResultFilter.CORRECT -> questions.filter { it.isCorrect }
            ResultFilter.FLAGGED -> questions.filter { it.isFlagged }
        }

    val topicMasteryList: List<TopicMasteryItem>
        get() {
            val grouped = questions.groupBy { it.topic }
            return grouped.map { (topic, qList) ->
                val correct = qList.count { it.isCorrect }
                TopicMasteryItem(topic, correct, qList.size)
            }.sortedBy { it.accuracyPercentage }
        }
}

class MockTestResultViewModel @Inject constructor(
    private val repository: OpenEDRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MockTestResultUiState())
    val uiState: StateFlow<MockTestResultUiState> = _uiState.asStateFlow()

    fun loadResult(testId: String) {
        _uiState.update { it.copy(testId = testId, isLoading = true) }
        viewModelScope.launch {
            val directTwq = repository.getMockTestWithQuestions(testId)
            if (directTwq != null) {
                val sorted = directTwq.copy(questions = directTwq.questions.sortedBy { it.questionIndex })
                _uiState.update { it.copy(testWithQuestions = sorted, isLoading = false) }
            }
            repository.getMockTestWithQuestionsFlow(testId).collect { twq ->
                if (twq != null) {
                    val sorted = twq.copy(questions = twq.questions.sortedBy { it.questionIndex })
                    _uiState.update { it.copy(testWithQuestions = sorted, isLoading = false) }
                } else if (directTwq == null) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun setFilter(filter: ResultFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun retakeTest(onRetakeReady: (String) -> Unit) {
        val twq = _uiState.value.testWithQuestions ?: return
        viewModelScope.launch {
            val resetTest = twq.test.copy(
                status = MockTestStatus.NOT_STARTED,
                score = 0,
                totalCorrect = 0,
                totalAnswered = 0,
                timeSpentSeconds = 0,
                completedAt = 0L
            )
            repository.updateMockTest(resetTest)
            repository.resetMockTestQuestions(twq.test.id)
            onRetakeReady(twq.test.id)
        }
    }
}
