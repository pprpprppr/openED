package org.opened.app.learning.ui

import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.model.ChapterWithContentBlocks
import org.opened.app.learning.model.LearningPathWithChapters

enum class LearningPathPane {
    LIST,   // Chapter outline / timeline
    DETAIL  // Active chapter content block editor
}

data class LearningPathUiState(
    val pathWithChapters: LearningPathWithChapters? = null,
    val activeChapterId: String? = null,
    val activeChapterWithBlocks: ChapterWithContentBlocks? = null,
    val activePane: LearningPathPane = LearningPathPane.LIST,

    /**
     * Local temporary lists to support real-time smooth animations during drag-and-drop.
     * When drop completes, these are persisted transactionally to Room.
     */
    val localChapters: List<Chapter> = emptyList(),
    val localContentBlocks: List<ContentBlock> = emptyList(),

    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)
