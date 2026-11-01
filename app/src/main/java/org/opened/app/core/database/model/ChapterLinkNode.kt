package org.opened.app.core.database.model

/**
 * Lightweight projection used by the Knowledge Graph screen.
 * Carries all chapters and their link cross-refs without loading full markdown content.
 */
data class ChapterLinkNode(
    val chapterId: String,
    val chapterTitle: String,
    val pathId: String,
    val status: ChapterStatus,
    val outgoingTargetIds: List<String>
)
