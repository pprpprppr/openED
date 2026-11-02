package org.opened.app.ui.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.RefineAction
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.database.model.ChapterWithDetails
import org.opened.app.core.tts.TtsManager
import org.opened.app.core.tts.TtsState
import org.opened.app.domain.repository.OpenEDRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CourseWorkspaceViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val aiLearningEngine: AiLearningEngine,
    private val ttsManager: TtsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        _uiState.update {
            it.copy(
                errorMessage = throwable.localizedMessage ?: "An unexpected error occurred in workspace.",
                isStreaming = false,
                isGeneratingFlashcards = false,
                isRefining = false,
                isAskingQuestion = false
            )
        }
    }

    private val _uiState = MutableStateFlow(CourseWorkspaceUiState())
    val uiState: StateFlow<CourseWorkspaceUiState> = _uiState.asStateFlow()

    val ttsState: StateFlow<TtsState> = ttsManager.state

    private var workspaceJob: Job? = null
    private var chapterJob: Job? = null
    private var flashcardsJob: Job? = null
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
        if (currentPathId == pathId && _uiState.value.pathWithModules != null) {
            if (initialChapterId != null && _uiState.value.activeChapterId != initialChapterId) {
                selectChapter(initialChapterId)
            }
            return
        }

        workspaceJob?.cancel()
        currentPathId = pathId

        workspaceJob = viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                repository.getStudyPathWithModulesFlow(pathId)
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to load course modules.") }
                    }
                    .collectLatest { pathWithModules ->
                        _uiState.update { it.copy(pathWithModules = pathWithModules) }

                        val currentActive = _uiState.value.activeChapterId
                        if (currentActive == null && pathWithModules != null) {
                            val targetChapterId = initialChapterId
                                ?: pathWithModules.sortedModules.firstOrNull()?.sortedChapters?.firstOrNull()?.id
                            if (targetChapterId != null) {
                                selectChapter(targetChapterId)
                            }
                        } else if (currentActive != null && pathWithModules != null) {
                            val cached = pathWithModules.allChapters.find { it.id == currentActive }
                            if (cached != null && _uiState.value.chapterDetails?.chapter?.id == currentActive) {
                                if (_uiState.value.chapterDetails?.chapter != cached) {
                                    _uiState.update { state ->
                                        state.copy(chapterDetails = state.chapterDetails?.copy(chapter = cached))
                                    }
                                }
                            }
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to load workspace.") }
            }
        }
    }

    fun selectChapter(chapterId: String) {
        val sameChapter = _uiState.value.activeChapterId == chapterId
        if (sameChapter && _uiState.value.chapterDetails != null) {
            return
        }

        chapterJob?.cancel()
        flashcardsJob?.cancel()
        // Ensure selectChapter() does NOT cancel an active streamingJob if already streaming for that same chapterId
        val preserveStreaming = sameChapter && streamingJob?.isActive == true
        if (!preserveStreaming) {
            streamingJob?.cancel()
            streamingJob = null
        }
        try {
            ttsManager.stop()
        } catch (_: Exception) {}

        // Instant optimistic switch using cached chapter from pathWithModules
        val cachedChapter = _uiState.value.pathWithModules?.allChapters?.find { it.id == chapterId }
        val immediateDetails = cachedChapter?.let { ChapterWithDetails(chapter = it) }

        _uiState.update {
            it.copy(
                activeChapterId = chapterId,
                chapterDetails = immediateDetails ?: (if (sameChapter) it.chapterDetails else null),
                streamingContent = if (preserveStreaming) it.streamingContent else null,
                isStreaming = if (preserveStreaming) it.isStreaming else false,
                errorMessage = null,
                showTtsPlayer = false,
                showFlashcardDeck = false,
                chapterFlashcards = emptyList()
            )
        }

        chapterJob = viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                repository.getChapterWithDetailsFlow(chapterId)
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.update { it.copy(errorMessage = e.localizedMessage) }
                    }
                    .collectLatest { details ->
                        _uiState.update { it.copy(chapterDetails = details) }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }

        flashcardsJob = viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                repository.getFlashcardsForChapterFlow(chapterId)
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.update { it.copy(errorMessage = e.localizedMessage) }
                    }
                    .collectLatest { cards ->
                        _uiState.update { it.copy(chapterFlashcards = cards) }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun generateChapterContent() {
        val chapterId = _uiState.value.activeChapterId ?: return
        if (streamingJob?.isActive == true && _uiState.value.isStreaming) {
            return
        }
        streamingJob?.cancel()

        streamingJob = viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isStreaming = true, streamingContent = "", errorMessage = null) }
            val accumulatedText = StringBuilder()
            var lastEmitTime = 0L

            try {
                val pathId = _uiState.value.pathWithModules?.studyPath?.id
                val userLevel = _uiState.value.pathWithModules?.studyPath?.userLevel
                    ?: (pathId?.let { repository.getStudyPathWithModules(it)?.studyPath?.userLevel } ?: "BEGINNER")

                aiLearningEngine.streamChapterContent(chapterId, userLevel)
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.update {
                            it.copy(
                                isStreaming = false,
                                errorMessage = e.localizedMessage ?: "Failed to generate chapter content."
                            )
                        }
                    }
                    .collect { chunk ->
                        accumulatedText.append(chunk)
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 200L) {
                            lastEmitTime = now
                            val currentText = accumulatedText.toString()
                            _uiState.update { it.copy(streamingContent = currentText) }
                        }
                    }

                // Final emission and write once to Room SQLite after stream finishes
                val finalText = accumulatedText.toString().trim()
                _uiState.update { it.copy(streamingContent = finalText) }
                if (finalText.isNotEmpty()) {
                    repository.updateChapterContent(chapterId, finalText, ChapterStatus.COMPLETED)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        errorMessage = e.localizedMessage ?: "Failed to generate chapter content."
                    )
                }
            } finally {
                if (accumulatedText.toString().trim().isBlank()) {
                    repository.updateChapterStatus(chapterId, ChapterStatus.PENDING)
                }
                _uiState.update { it.copy(isStreaming = false, streamingContent = null) }
            }
        }
    }

    // ---- Feature 1: Flashcards ----

    fun onFlashcardsClicked() {
        val cards = _uiState.value.chapterFlashcards
        if (cards.isNotEmpty()) {
            _uiState.update { it.copy(showFlashcardDeck = true) }
        } else {
            generateFlashcards()
        }
    }

    fun openFlashcardDeck() = _uiState.update { it.copy(showFlashcardDeck = true) }
    fun closeFlashcardDeck() = _uiState.update { it.copy(showFlashcardDeck = false) }

    fun generateFlashcards() {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isGeneratingFlashcards = true, errorMessage = null) }
            try {
                aiLearningEngine.generateFlashcards(chapterId)
                    .onSuccess { cards ->
                        _uiState.update {
                            it.copy(
                                isGeneratingFlashcards = false,
                                showFlashcardDeck = true,
                                flashcardSnackMessage = "${cards.size} flashcards generated!"
                            )
                        }
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(isGeneratingFlashcards = false, errorMessage = e.localizedMessage ?: "Flashcard generation failed.")
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGeneratingFlashcards = false, errorMessage = e.localizedMessage ?: "Flashcard generation failed.")
                }
            }
        }
    }

    fun rateFlashcard(card: FlashcardEntity, quality: Int) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                aiLearningEngine.reviewFlashcard(card, quality)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to review flashcard.") }
            }
        }
    }

    fun clearFlashcardMessage() = _uiState.update { it.copy(flashcardSnackMessage = null) }

    // ---- Feature 2: Mastery ----

    fun openMasterySheet() = _uiState.update { it.copy(showMasterySheet = true) }
    fun closeMasterySheet() = _uiState.update { it.copy(showMasterySheet = false) }

    // ---- Phase 7: Interactive Laboratory ----
    fun openLabSheet() = _uiState.update { it.copy(showLabSheet = true) }
    fun closeLabSheet() = _uiState.update { it.copy(showLabSheet = false) }

    // ---- Feature 4: TTS ----

    fun toggleTtsPlayer() {
        val show = !_uiState.value.showTtsPlayer
        if (show) {
            try { ttsManager.initialize() } catch (_: Exception) {}
        } else {
            try { ttsManager.stop() } catch (_: Exception) {}
        }
        _uiState.update { it.copy(showTtsPlayer = show) }
    }

    fun ttsPlay() {
        val markdown = _uiState.value.chapterDetails?.chapter?.markdownContent ?: return
        try {
            ttsManager.speak(markdown)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "TTS playback error: ${e.localizedMessage}") }
        }
    }

    fun ttsPause() {
        try { ttsManager.pause() } catch (_: Exception) {}
    }

    fun ttsResume() {
        val markdown = _uiState.value.chapterDetails?.chapter?.markdownContent ?: return
        try { ttsManager.resume(markdown) } catch (_: Exception) {}
    }

    fun ttsStop() {
        try { ttsManager.stop() } catch (_: Exception) {}
    }

    fun ttsSetSpeed(rate: Float) {
        try { ttsManager.setSpeed(rate) } catch (_: Exception) {}
    }

    // ---- Feature 3: Revision History ----

    fun openRevisionSheet() = _uiState.update { it.copy(showRevisionSheet = true) }
    fun closeRevisionSheet() = _uiState.update { it.copy(showRevisionSheet = false) }

    fun restoreRevision(revision: org.opened.app.core.database.entity.ChapterRevisionEntity) {
        val chapterId = _uiState.value.activeChapterId ?: return
        val currentContent = _uiState.value.chapterDetails?.chapter?.markdownContent.orEmpty()
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                repository.addChapterRevision(chapterId, currentContent, "Rollback before restoring '${revision.changeReason}'")
                repository.updateChapterContent(chapterId, revision.previousMarkdown, ChapterStatus.COMPLETED)
                val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(revision.timestamp))
                _uiState.update {
                    it.copy(
                        showRevisionSheet = false,
                        flashcardSnackMessage = "Restored previous revision from $dateStr"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to restore revision.") }
            }
        }
    }

    // ---- Refinement ----

    fun openRefineSheet(snippet: String? = null) {
        _uiState.update { it.copy(showRefineSheet = true, refineSnippet = snippet.orEmpty()) }
    }

    fun closeRefineSheet() = _uiState.update { it.copy(showRefineSheet = false) }

    fun applyRefinement(action: RefineAction, customInstruction: String?, targetSnippet: String? = null) {
        val chapterId = _uiState.value.activeChapterId ?: return
        val snippet = (targetSnippet ?: _uiState.value.refineSnippet).orEmpty()

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isRefining = true, errorMessage = null) }
            try {
                val pathId = _uiState.value.pathWithModules?.studyPath?.id
                val userLevel = _uiState.value.pathWithModules?.studyPath?.userLevel
                    ?: (pathId?.let { repository.getStudyPathWithModules(it)?.studyPath?.userLevel } ?: "BEGINNER")

                val result = aiLearningEngine.refineChapterContent(
                    chapterId = chapterId,
                    selectedText = snippet,
                    action = action,
                    customInstruction = customInstruction,
                    userLevelOverride = userLevel
                )
                result.fold(
                    onSuccess = { _uiState.update { it.copy(isRefining = false, showRefineSheet = false, refineSnippet = "") } },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isRefining = false, errorMessage = error.localizedMessage ?: "Refinement failed.")
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRefining = false, errorMessage = e.localizedMessage ?: "Refinement failed.")
                }
            }
        }
    }

    fun expandCourseSyllabus() {
        val pathId = _uiState.value.pathWithModules?.studyPath?.id ?: return
        if (_uiState.value.isExpandingCourse) return

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isExpandingCourse = true, errorMessage = null) }
            try {
                val result = aiLearningEngine.expandCourseSyllabus(pathId)
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isExpandingCourse = false,
                                flashcardSnackMessage = "Added 2 new modules to the syllabus!"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isExpandingCourse = false,
                                errorMessage = error.localizedMessage ?: "Failed to expand course syllabus."
                            )
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExpandingCourse = false,
                        errorMessage = e.localizedMessage ?: "Failed to expand course syllabus."
                    )
                }
            }
        }
    }

    fun openSocraticQaSheet() = _uiState.update { it.copy(showSocraticQaSheet = true) }
    fun closeSocraticQaSheet() = _uiState.update { it.copy(showSocraticQaSheet = false) }

    fun askSocraticQuestion(question: String) {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isAskingQuestion = true, errorMessage = null) }
            try {
                val result = aiLearningEngine.askSocraticQuestion(chapterId, question)
                result.fold(
                    onSuccess = { _uiState.update { it.copy(isAskingQuestion = false) } },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isAskingQuestion = false, errorMessage = error.localizedMessage ?: "Failed to answer question.")
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAskingQuestion = false, errorMessage = e.localizedMessage ?: "Failed to answer question.")
                }
            }
        }
    }

    fun appendQaToChapter(qa: ChapterQaEntity) {
        val chapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                aiLearningEngine.appendQaToChapter(chapterId, qa)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to append Q&A.") }
            }
        }
    }

    fun generateTangent(conceptTitle: String) {
        val sourceChapterId = _uiState.value.activeChapterId ?: return
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                val tangentChapter = repository.createTangentChapter(sourceChapterId, conceptTitle)
                selectChapter(tangentChapter.id)
                generateChapterContent()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to generate tangent.") }
            }
        }
    }

    fun navigateToChapterByTitle(title: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                val chapter = repository.findChapterByTitle(title)
                if (chapter != null) {
                    selectChapter(chapter.id)
                } else {
                    _uiState.update { it.copy(errorMessage = "Referenced chapter '$title' not found in workspace.") }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Error navigating to chapter.") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            ttsManager.stop()
        } catch (_: Exception) {}
    }
}
