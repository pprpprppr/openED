package org.opened.app.ui.mocktest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

data class MockTestListUiState(
    val pathId: String = "",
    val course: StudyPathWithModules? = null,
    val mockTests: List<MockTestEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val generationProgressText: String = "",
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val showCreateDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MockTestListViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val aiLearningEngine: AiLearningEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MockTestListUiState())
    val uiState: StateFlow<MockTestListUiState> = _uiState.asStateFlow()

    fun loadMockTests(pathId: String) {
        _uiState.update { it.copy(pathId = pathId, isLoading = true) }
        viewModelScope.launch {
            val course = repository.getStudyPathWithModules(pathId)
            _uiState.update { it.copy(course = course, isLoading = false) }

            repository.getMockTestsForPathFlow(pathId).collectLatest { tests ->
                _uiState.update { it.copy(mockTests = tests) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, errorMessage = null) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createMockTest(
        examCategory: ExamCategory,
        targetExamName: String,
        questionCount: Int,
        timeLimitMinutes: Int,
        customTitle: String
    ) {
        val pathId = _uiState.value.pathId
        if (pathId.isBlank()) return

        _uiState.update {
            it.copy(
                showCreateDialog = false,
                isGenerating = true,
                generationProgressText = "Analyzing course topics & preparing test outline...",
                currentBatch = 0,
                totalBatches = 0,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = aiLearningEngine.generateFullMockTest(
                pathId = pathId,
                title = customTitle,
                examCategory = examCategory,
                targetExamName = targetExamName,
                totalQuestions = questionCount,
                timeLimitMinutes = timeLimitMinutes,
                onProgress = { currentBatch, totalBatches, questionsGenerated ->
                    _uiState.update { state ->
                        state.copy(
                            currentBatch = currentBatch,
                            totalBatches = totalBatches,
                            generationProgressText = "Generating Part $currentBatch of $totalBatches ($questionsGenerated questions ready)..."
                        )
                    }
                }
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            generationProgressText = "",
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            generationProgressText = "",
                            errorMessage = error.localizedMessage ?: "Failed to generate mock test"
                        )
                    }
                }
            )
        }
    }

    fun deleteMockTest(testId: String) {
        viewModelScope.launch {
            repository.deleteMockTest(testId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
