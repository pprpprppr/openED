package org.opened.app.core.database.model

enum class SearchResultType {
    CHAPTER,
    REVISION_NOTE,
    QA_THREAD
}

data class SearchResultItem(
    val id: String,
    val pathId: String,
    val chapterId: String,
    val chapterTitle: String,
    val pathTitle: String,
    val snippet: String,
    val type: SearchResultType,
    val timestamp: Long
)
