package org.opened.app.learning.ui

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
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.domain.LearningRepository
import org.opened.app.learning.model.BlockType
import org.opened.app.learning.model.ChapterStatus
import javax.inject.Inject

@HiltViewModel
class LearningPathViewModel @Inject constructor(
    private val repository: LearningRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearningPathUiState(isLoading = true))
    val uiState: StateFlow<LearningPathUiState> = _uiState.asStateFlow()

    private var activeChapterJob: Job? = null
    private var currentPathId: String? = null

    init {
        val pathId: String? = savedStateHandle.get<String>("pathId")
        if (pathId != null) {
            loadLearningPath(pathId)
        }
    }

    /**
     * Loads the Path structure and ordered chapter headers without heavy ContentBlocks.
     */
    fun loadLearningPath(pathId: String) {
        currentPathId = pathId
        viewModelScope.launch {
            repository.touchLastAccessed(pathId)
            repository.getLearningPathWithChapters(pathId).collectLatest { pathWithChapters ->
                _uiState.update { currentState ->
                    val defaultChapterId = currentState.activeChapterId
                        ?: pathWithChapters?.orderedChapters?.firstOrNull()?.chapterId

                    currentState.copy(
                        pathWithChapters = pathWithChapters,
                        localChapters = pathWithChapters?.orderedChapters.orEmpty(),
                        activeChapterId = defaultChapterId,
                        isLoading = false
                    )
                }

                val activeId = _uiState.value.activeChapterId
                if (activeId != null && activeChapterJob == null) {
                    observeActiveChapterBlocks(activeId)
                }
            }
        }
    }

    /**
     * Selects an active chapter for reading or editing.
     * Memory Efficiency Rule:
     * - Cancels previous chapter ContentBlock collector to allow GC reclamation.
     * - Subscribes to the newly selected chapter's ContentBlock flow.
     */
    fun selectChapter(chapterId: String) {
        _uiState.update {
            it.copy(
                activeChapterId = chapterId,
                activePane = LearningPathPane.DETAIL
            )
        }
        observeActiveChapterBlocks(chapterId)
    }

    fun navigateToPane(pane: LearningPathPane) {
        _uiState.update { it.copy(activePane = pane) }
    }

    private fun observeActiveChapterBlocks(chapterId: String) {
        activeChapterJob?.cancel()
        activeChapterJob = viewModelScope.launch {
            repository.getActiveChapterWithBlocks(chapterId).collectLatest { chapterWithBlocks ->
                _uiState.update {
                    it.copy(
                        activeChapterWithBlocks = chapterWithBlocks,
                        localContentBlocks = chapterWithBlocks?.orderedContentBlocks.orEmpty()
                    )
                }
            }
        }
    }

    // =========================================================================
    // DRAG-AND-DROP REORDERING LOGIC
    // =========================================================================

    /**
     * Temporarily mutates chapter positions in local state during drag gestures
     * so Compose can smoothly render items at their new visual slot without DB latency.
     */
    fun onChapterDragMove(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.localChapters.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            state.copy(localChapters = list)
        }
    }

    /**
     * Called when the chapter drop completes.
     * Persists updated sort orders transactionally into Room SQLite.
     */
    fun onChapterDropCompleted() {
        val currentChapters = _uiState.value.localChapters
        viewModelScope.launch {
            repository.reorderChapters(currentChapters.map { it.chapterId })
        }
    }

    /**
     * Temporarily mutates content block positions in local state during drag gestures.
     */
    fun onContentBlockDragMove(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.localContentBlocks.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            state.copy(localContentBlocks = list)
        }
    }

    /**
     * Called when the content block drop completes.
     * Persists updated sort orders transactionally into Room SQLite.
     */
    fun onContentBlockDropCompleted() {
        val currentBlocks = _uiState.value.localContentBlocks
        viewModelScope.launch {
            repository.reorderContentBlocks(currentBlocks.map { it.blockId })
        }
    }

    // =========================================================================
    // CRUD MUTATIONS
    // =========================================================================

    fun addChapter(title: String) {
        val pathId = currentPathId ?: return
        viewModelScope.launch {
            val newChapter = repository.addChapter(pathId, title)
            selectChapter(newChapter.chapterId)
        }
    }

    fun updateChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.updateChapter(chapter)
        }
    }

    fun addContentBlock(blockType: BlockType, content: String = "") {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch {
            repository.addContentBlock(chapterId, blockType, content)
        }
    }

    fun updateContentBlock(block: ContentBlock) {
        viewModelScope.launch {
            repository.updateContentBlock(block)
        }
    }

    fun deleteContentBlock(block: ContentBlock) {
        viewModelScope.launch {
            repository.deleteContentBlock(block)
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            repository.deleteChapter(chapterId)
            if (_uiState.value.activeChapterId == chapterId) {
                activeChapterJob?.cancel()
                activeChapterJob = null
                val remaining = _uiState.value.localChapters.filter { it.chapterId != chapterId }
                val nextChapterId = remaining.firstOrNull()?.chapterId
                _uiState.update {
                    it.copy(
                        activeChapterId = nextChapterId,
                        activeChapterWithBlocks = null,
                        localContentBlocks = emptyList(),
                        activePane = LearningPathPane.LIST
                    )
                }
                if (nextChapterId != null) {
                    selectChapter(nextChapterId)
                }
            }
        }
    }

    // =========================================================================
    // WORKMANAGER BACKGROUND AI GENERATION INJECTION POINT
    // =========================================================================
    /**
     * Triggers AI content generation in the background using Android WorkManager.
     *
     * 1. Mutates chapter status in Room DB to `ChapterStatus.Generating`.
     * 2. Enqueues a `OneTimeWorkRequest` targeting `ChapterAiGenerationWorker`.
     */
    fun generateChapterContentWithAi(chapterId: String) {
        viewModelScope.launch {
            repository.updateChapterStatus(chapterId, ChapterStatus.Generating)
            // Phase 2 WorkManager invocation:
            // WorkManager.getInstance(context).enqueueUniqueWork(...)
        }
    }
}
