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
import org.opened.app.core.tts.TtsManager
import org.opened.app.core.tts.TtsState
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

@HiltViewModel
class CourseWorkspaceViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val aiLearningEngine: AiLearningEngine,
    private val ttsManager: TtsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseWorkspaceUiState())
    val uiState: StateFlow<CourseWorkspaceUiState> = _uiState.asStateFlow()

    val ttsState: StateFlow<TtsState> = ttsManager.state

    private var chapterJob: Job? = null
    private var streamingJob: Job? = null
    private var currentPathId: String = ""

    init {
        val pathId = savedStateHandle.get<String>("pathId")
        val chapterId = savedStateHandle.get<String>("chapterId")
        if (pathId != null) {
            initWorkspace(pathId, chapterId)
        }
        ttsManager.initialize()
    }

    fun initWorkspace(pathId: String, initialChapterId: String? = null) {
        currentPathId = pathId
        viewModelScope.launch {
            repository.getStudyPathWithModulesFlow(pathId).collectLatest { pathWithModules ->
                _uiState.update { it.copy(pathWithModules = pathWithModules) }

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
        ttsManager.stop()
        _uiState.update {
            it.copy(
                activeChapterId = chapterId,
                streamingContent = null,
                isStreaming = false,
                errorMessage = null,
                showTtsPlayer = false
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
            _uiState.update { it.copy(isStreaming = true, streamingContent = "", errorMessage = null) }
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

    // ---- Feature 1: Flashcards ----

    fun generateFlashcards() {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingFlashcards = true, errorMessage = null) }
            aiLearningEngine.generateFlashcards(chapterId)
                .onSuccess { cards ->
                    _uiState.update {
                        it.copy(
                            isGeneratingFlashcards = false,
                            flashcardSnackMessage = "${cards.size} flashcards generated!"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isGeneratingFlashcards = false, errorMessage = e.message ?: "Flashcard generation failed.")
                    }
                }
        }
    }

    fun clearFlashcardMessage() = _uiState.update { it.copy(flashcardSnackMessage = null) }

    // ---- Feature 2: Mastery ----

    fun openMasterySheet() = _uiState.update { it.copy(showMasterySheet = true) }
    fun closeMasterySheet() = _uiState.update { it.copy(showMasterySheet = false) }

    // ---- Feature 4: TTS ----

    fun toggleTtsPlayer() {
        val show = !_uiState.value.showTtsPlayer
        if (!show) ttsManager.stop()
        _uiState.update { it.copy(showTtsPlayer = show) }
    }

    fun ttsPlay() {
        val markdown = _uiState.value.chapterDetails?.chapter?.markdownContent ?: return
        ttsManager.speak(markdown)
    }

    fun ttsPause() = ttsManager.pause()

    fun ttsResume() {
        val markdown = _uiState.value.chapterDetails?.chapter?.markdownContent ?: return
        ttsManager.resume(markdown)
    }

    fun ttsStop() = ttsManager.stop()

    fun ttsSetSpeed(rate: Float) = ttsManager.setSpeed(rate)

    // ---- Feature 3: Revision History ----

    fun openRevisionSheet() = _uiState.update { it.copy(showRevisionSheet = true) }
    fun closeRevisionSheet() = _uiState.update { it.copy(showRevisionSheet = false) }

    fun restoreRevision(revision: org.opened.app.core.database.entity.ChapterRevisionEntity) {
        val chapterId = _uiState.value.activeChapterId ?: return
        val currentContent = _uiState.value.chapterDetails?.chapter?.markdownContent.orEmpty()
        viewModelScope.launch {
            repository.addChapterRevision(chapterId, currentContent, "Rollback before restoring '${revision.changeReason}'")
            repository.updateChapterContent(chapterId, revision.previousMarkdown, ChapterStatus.COMPLETED)
            _uiState.update {
                it.copy(
                    showRevisionSheet = false,
                    flashcardSnackMessage = "Restored previous revision from ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(revision.timestamp))}"
                )
            }
        }
    }

    // ---- Existing ----

    fun openRefineSheet(snippet: String? = null) {
        val defaultSnippet = snippet?.ifBlank { null }
            ?: _uiState.value.chapterDetails?.chapter?.markdownContent?.take(500).orEmpty()
        _uiState.update { it.copy(showRefineSheet = true, refineSnippet = defaultSnippet) }
    }

    fun closeRefineSheet() = _uiState.update { it.copy(showRefineSheet = false) }

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
                onSuccess = { _uiState.update { it.copy(isRefining = false, showRefineSheet = false) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isRefining = false, errorMessage = error.localizedMessage ?: "Refinement failed.")
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
                onSuccess = { _uiState.update { it.copy(isAskingQuestion = false) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isAskingQuestion = false, errorMessage = error.localizedMessage ?: "Failed to answer question.")
                    }
                }
            )
        }
    }

    fun appendQaToChapter(qa: ChapterQaEntity) {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch { aiLearningEngine.appendQaToChapter(chapterId, qa) }
    }

    fun generateTangent(conceptTitle: String) {
        val sourceChapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch {
            try {
                val tangentChapter = repository.createTangentChapter(sourceChapterId, conceptTitle)
                selectChapter(tangentChapter.id)
                generateChapterContent()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to generate tangent.") }
            }
        }
    }

    fun navigateToChapterByTitle(title: String) {
        viewModelScope.launch {
            val chapter = repository.findChapterByTitle(title)
            if (chapter != null) {
                selectChapter(chapter.id)
            } else {
                _uiState.update { it.copy(errorMessage = "Referenced chapter '$title' not found in workspace.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
