package org.opened.app.ui.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.RefineAction
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

@HiltViewModel
class CourseWorkspaceViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val aiLearningEngine: AiLearningEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseWorkspaceUiState())
    val uiState: StateFlow<CourseWorkspaceUiState> = _uiState.asStateFlow()

    private var chapterJob: Job? = null
    private var streamingJob: Job? = null
    private var currentPathId: String = ""

    init {
        val pathId = savedStateHandle.get<String>("pathId")
        val chapterId = savedStateHandle.get<String>("chapterId")
        if (pathId != null) {
            initWorkspace(pathId, chapterId)
        }
    }

    fun initWorkspace(pathId: String, initialChapterId: String? = null) {
        currentPathId = pathId
        viewModelScope.launch {
            repository.getStudyPathWithModulesFlow(pathId).collectLatest { pathWithModules ->
                _uiState.update { it.copy(pathWithModules = pathWithModules) }

                // Auto select first chapter if none active
                if (_uiState.value.activeChapterId == null && pathWithModules != null) {
                    val targetChapterId = initialChapterId
                        ?: pathWithModules.sortedModules.firstOrNull()?.sortedChapters?.firstOrNull()?.id
                    if (targetChapterId != null) {
                        selectChapter(targetChapterId)
                    }
                }
            }
        }
    }

    fun selectChapter(chapterId: String) {
        if (_uiState.value.activeChapterId == chapterId && _uiState.value.chapterDetails != null) {
            return
        }

        chapterJob?.cancel()
        streamingJob?.cancel()
        _uiState.update {
            it.copy(
                activeChapterId = chapterId,
                streamingContent = null,
                isStreaming = false,
                errorMessage = null
            )
        }

        chapterJob = viewModelScope.launch {
            repository.getChapterWithDetailsFlow(chapterId).collectLatest { details ->
                _uiState.update { it.copy(chapterDetails = details) }
            }
        }
    }

    fun generateChapterContent() {
        val chapterId = _uiState.value.activeChapterId ?: return
        streamingJob?.cancel()

        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamingContent = "",
                    errorMessage = null
                )
            }

            try {
                aiLearningEngine.streamChapterContent(chapterId).collect { chunk ->
                    _uiState.update {
                        val current = it.streamingContent.orEmpty()
                        it.copy(streamingContent = current + chunk)
                    }
                }
                _uiState.update { it.copy(isStreaming = false, streamingContent = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        errorMessage = e.localizedMessage ?: "Failed to generate chapter content."
                    )
                }
            }
        }
    }

    fun openRefineSheet(snippet: String? = null) {
        val defaultSnippet = snippet?.ifBlank { null }
            ?: _uiState.value.chapterDetails?.chapter?.markdownContent?.take(500).orEmpty()

        _uiState.update {
            it.copy(
                showRefineSheet = true,
                refineSnippet = defaultSnippet
            )
        }
    }

    fun closeRefineSheet() {
        _uiState.update { it.copy(showRefineSheet = false) }
    }

    fun applyRefinement(action: RefineAction, customInstruction: String?) {
        val chapterId = _uiState.value.activeChapterId ?: return
        val snippet = _uiState.value.refineSnippet

        viewModelScope.launch {
            _uiState.update { it.copy(isRefining = true, errorMessage = null) }
            val result = aiLearningEngine.refineChapterContent(
                chapterId = chapterId,
                selectedText = snippet,
                action = action,
                customInstruction = customInstruction
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isRefining = false, showRefineSheet = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRefining = false,
                            errorMessage = error.localizedMessage ?: "Refinement failed."
                        )
                    }
                }
            )
        }
    }

    fun askSocraticQuestion(question: String) {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAskingQuestion = true, errorMessage = null) }
            val result = aiLearningEngine.askSocraticQuestion(chapterId, question)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isAskingQuestion = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAskingQuestion = false,
                            errorMessage = error.localizedMessage ?: "Failed to answer question."
                        )
                    }
                }
            )
        }
    }

    fun appendQaToChapter(qa: ChapterQaEntity) {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch {
            aiLearningEngine.appendQaToChapter(chapterId, qa)
        }
    }

    fun generateTangent(conceptTitle: String) {
        val sourceChapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch {
            try {
                val tangentChapter = repository.createTangentChapter(sourceChapterId, conceptTitle)
                selectChapter(tangentChapter.id)
                generateChapterContent()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to generate tangent.")
                }
            }
        }
    }

    fun navigateToChapterByTitle(title: String) {
        viewModelScope.launch {
            val chapter = repository.findChapterByTitle(title)
            if (chapter != null) {
                selectChapter(chapter.id)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Referenced chapter '$title' not found in workspace.")
                }
            }
        }
    }
}
