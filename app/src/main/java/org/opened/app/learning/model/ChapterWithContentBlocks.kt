package org.opened.app.learning.model

import androidx.room.Embedded
import androidx.room.Relation
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock

/**
 * On-demand detailed relation: Loads full content blocks (text, code, image URLs, AI prompts)
 * ONLY for the currently active/selected chapter.
 */
data class ChapterWithContentBlocks(
    @Embedded
    val chapter: Chapter,

    @Relation(
        parentColumn = "chapterId",
        entityColumn = "chapterId"
    )
    val contentBlocks: List<ContentBlock> = emptyList()
) {
    val orderedContentBlocks: List<ContentBlock>
        get() = contentBlocks.sortedBy { it.sortOrder }
}
