package org.opened.app.ui.graph

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

data class CourseGraphOption(
    val pathId: String,
    val title: String,
    val topic: String,
    val chapterCount: Int
)

data class GraphUiState(
    val allNodes: List<ChapterLinkNode> = emptyList(),
    val courses: List<CourseGraphOption> = emptyList(),
    val selectedPathId: String? = null,
    val displayedNodes: List<ChapterLinkNode> = emptyList(),
    val selectedCourseTitle: String = "All Topics",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class KnowledgeGraphViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialPathId: String? = savedStateHandle.get<String>("pathId")

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.localizedMessage ?: "Failed to load graph."
            )
        }
    }

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    init {
        loadGraph()
    }

    fun loadGraph() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val allNodes = repository.getAllChapterLinkNodes()
                repository.getAllStudyPathsFlow().collectLatest { studyPaths ->
                    val courseOptions = studyPaths.map { sp ->
                        CourseGraphOption(
                            pathId = sp.studyPath.id,
                            title = sp.studyPath.title,
                            topic = sp.studyPath.topic,
                            chapterCount = sp.allChapters.size
                        )
                    }

                    // Default to initialPathId if provided, or the first course if available, or null if empty
                    val currentSelected = _uiState.value.selectedPathId
                        ?: initialPathId
                        ?: courseOptions.firstOrNull()?.pathId

                    val displayed = if (currentSelected != null) {
                        allNodes.filter { it.pathId == currentSelected }
                    } else {
                        allNodes
                    }

                    val courseTitle = courseOptions.find { it.pathId == currentSelected }?.topic
                        ?: if (currentSelected == null) "All Topics (Global Overview)" else "Course Graph"

                    _uiState.update {
                        it.copy(
                            allNodes = allNodes,
                            courses = courseOptions,
                            selectedPathId = currentSelected,
                            displayedNodes = displayed,
                            selectedCourseTitle = courseTitle,
                            isLoading = false
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun selectCourse(pathId: String?) {
        val courseTitle = _uiState.value.courses.find { it.pathId == pathId }?.topic
            ?: if (pathId == null) "All Topics (Global Overview)" else "Course Graph"

        val filtered = if (pathId != null) {
            _uiState.value.allNodes.filter { it.pathId == pathId }
        } else {
            _uiState.value.allNodes
        }

        _uiState.update {
            it.copy(
                selectedPathId = pathId,
                displayedNodes = filtered,
                selectedCourseTitle = courseTitle
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
