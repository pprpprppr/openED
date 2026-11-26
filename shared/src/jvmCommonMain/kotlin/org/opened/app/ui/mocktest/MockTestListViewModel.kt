package org.opened.app.ui.mocktest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.domain.repository.OpenEDRepository
import java.util.UUID
import javax.inject.Inject

data class MockTestListUiState(
    val pathId: String = "",
    val course: StudyPathWithModules? = null,
    val availableCourses: List<StudyPathWithModules> = emptyList(),
    val allMockTests: List<MockTestEntity> = emptyList(),
    val mockTests: List<MockTestEntity> = emptyList(),
    val selectedCategory: ExamCategory? = null,
    val selectedCourseFilter: String? = null,
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val generationProgressText: String = "",
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val showCreateDialog: Boolean = false,
    val preselectedCourseId: String? = null,
    val isGeneratingPrompt: Boolean = false,
    val generatedCustomPrompt: String? = null,
    val promptGenerationError: String? = null,
    val errorMessage: String? = null
)

class MockTestListViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val aiLearningEngine: AiLearningEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MockTestListUiState())
    val uiState: StateFlow<MockTestListUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    fun loadMockTests(pathId: String = "") {
        _uiState.update { it.copy(pathId = pathId, isLoading = true) }
        viewModelScope.launch {
            repository.getAllStudyPathsFlow().collectLatest { allCourses ->
                val specificCourse = if (pathId.isNotBlank()) allCourses.find { it.studyPath.id == pathId } else null

                val testsFlow = if (pathId.isNotBlank()) {
                    repository.getMockTestsForPathFlow(pathId)
                } else {
                    repository.getAllMockTestsFlow()
                }

                testsFlow.collectLatest { tests ->
                    _uiState.update { state ->
                        val filtered = applyFilters(tests, state.selectedCategory, state.selectedCourseFilter)
                        state.copy(
                            course = specificCourse,
                            availableCourses = allCourses,
                            allMockTests = tests,
                            mockTests = filtered,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun filterByCategory(category: ExamCategory?) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                mockTests = applyFilters(state.allMockTests, category, state.selectedCourseFilter)
            )
        }
    }

    fun filterByCourse(pathId: String?) {
        _uiState.update { state ->
            state.copy(
                selectedCourseFilter = pathId,
                mockTests = applyFilters(state.allMockTests, state.selectedCategory, pathId)
            )
        }
    }

    private fun applyFilters(
        tests: List<MockTestEntity>,
        category: ExamCategory?,
        courseFilter: String?
    ): List<MockTestEntity> {
        return tests.filter { test ->
            val matchesCategory = category == null || test.examCategory == category
            val matchesCourse = courseFilter == null || test.pathId == courseFilter
            matchesCategory && matchesCourse
        }
    }

    fun openCreateDialog(preselectedCourseId: String? = null) {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                preselectedCourseId = preselectedCourseId,
                errorMessage = null,
                generatedCustomPrompt = null,
                promptGenerationError = null
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = false,
                preselectedCourseId = null,
                generatedCustomPrompt = null,
                promptGenerationError = null
            )
        }
    }

    fun generateCustomPrompt(
        topic: String,
        category: ExamCategory,
        targetExam: String,
        userNotes: String
    ) {
        if (topic.isBlank()) {
            _uiState.update { it.copy(promptGenerationError = "Please enter or select a target course or topic first.") }
            return
        }
        _uiState.update { it.copy(isGeneratingPrompt = true, promptGenerationError = null) }
        viewModelScope.launch {
            val result = aiLearningEngine.generateMockTestCustomPrompt(
                topic = topic,
                examCategory = category,
                targetExamName = targetExam,
                userNotes = userNotes
            )
            result.fold(
                onSuccess = { generatedPrompt ->
                    _uiState.update {
                        it.copy(
                            isGeneratingPrompt = false,
                            generatedCustomPrompt = generatedPrompt,
                            promptGenerationError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGeneratingPrompt = false,
                            promptGenerationError = error.localizedMessage ?: "Failed to generate prompt."
                        )
                    }
                }
            )
        }
    }

    fun clearGeneratedPrompt() {
        _uiState.update { it.copy(generatedCustomPrompt = null, promptGenerationError = null) }
    }

    fun createMockTest(
        examCategory: ExamCategory,
        targetExamName: String,
        questionCount: Int,
        timeLimitMinutes: Int,
        customTitle: String,
        targetPathId: String? = null,
        customCourseName: String = "",
        customPrompt: String = ""
    ) {
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

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                var effectivePathId = targetPathId

                // Resolve or create custom target course if provided
                if (effectivePathId.isNullOrBlank() && customCourseName.isNotBlank()) {
                    val trimmedCourse = customCourseName.trim()
                    val existing = _uiState.value.availableCourses.find {
                        it.studyPath.topic.equals(trimmedCourse, ignoreCase = true) ||
                        it.studyPath.title.equals(trimmedCourse, ignoreCase = true)
                    }
                    if (existing != null) {
                        effectivePathId = existing.studyPath.id
                    } else {
                        val newId = UUID.randomUUID().toString()
                        val newPath = StudyPathEntity(
                            id = newId,
                            title = trimmedCourse,
                            topic = trimmedCourse,
                            userLevel = "EXAM_PREP",
                            learningGoalsJson = "[]",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.saveStudyPath(newPath)
                        effectivePathId = newId
                    }
                }

                if (effectivePathId.isNullOrBlank()) {
                    effectivePathId = _uiState.value.pathId.ifBlank {
                        _uiState.value.selectedCourseFilter ?: _uiState.value.availableCourses.firstOrNull()?.studyPath?.id
                    }
                }

                if (effectivePathId.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = "Please enter or select a target course for this mock test."
                        )
                    }
                    return@launch
                }

                val finalTitle = customTitle.ifBlank {
                    if (targetExamName.isNotBlank()) "$targetExamName Practice Mock Test"
                    else "${customCourseName.ifBlank { "Course" }} ${examCategory.displayName.split(" ")[0]} Exam"
                }

                val result = aiLearningEngine.generateFullMockTest(
                    pathId = effectivePathId,
                    title = finalTitle,
                    examCategory = examCategory,
                    targetExamName = targetExamName,
                    totalQuestions = questionCount,
                    timeLimitMinutes = timeLimitMinutes,
                    customPrompt = customPrompt,
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgressText = "",
                        errorMessage = e.localizedMessage ?: "Failed to generate mock test"
                    )
                }
            }
        }
    }

    fun deleteMockTest(testId: String) {
        viewModelScope.launch {
            repository.deleteMockTest(testId)
        }
    }

    fun regenerateMockTestQuestions(testId: String) {
        _uiState.update {
            it.copy(
                isGenerating = true,
                generationProgressText = "Preparing exam questions...",
                errorMessage = null
            )
        }
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
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
                                errorMessage = error.localizedMessage ?: "Failed to generate questions."
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgressText = "",
                        errorMessage = e.localizedMessage ?: "Failed to generate questions."
                    )
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                generationProgressText = ""
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
