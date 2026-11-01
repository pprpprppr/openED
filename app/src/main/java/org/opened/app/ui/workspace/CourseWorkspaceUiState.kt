package org.opened.app.ui.workspace

import org.opened.app.core.database.model.ChapterWithDetails
import org.opened.app.core.database.model.StudyPathWithModules

data class CourseWorkspaceUiState(
    val pathWithModules: StudyPathWithModules? = null,
    val activeChapterId: String? = null,
    val chapterDetails: ChapterWithDetails? = null,
    val streamingContent: String? = null,
    val isStreaming: Boolean = false,
    val isRefining: Boolean = false,
    val isAskingQuestion: Boolean = false,
    val showRefineSheet: Boolean = false,
    val refineSnippet: String = "",
    val errorMessage: String? = null,
    // Feature 1: Flashcards
    val isGeneratingFlashcards: Boolean = false,
    val flashcardSnackMessage: String? = null,
    // Feature 2: Mastery / Feynman
    val showMasterySheet: Boolean = false,
    // Feature 3: Revision History
    val showRevisionSheet: Boolean = false,
    // Feature 4: TTS
    val showTtsPlayer: Boolean = false
)
