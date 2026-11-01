package org.opened.app.learning.model

import androidx.room.Embedded
import androidx.room.Relation
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.LearningPath

/**
 * Memory-efficient relation: Loads a LearningPath along with its ordered chapters.
 * Does NOT eagerly load ContentBlock payload to prevent large memory overhead
 * when navigating learning paths or reordering chapters.
 */
data class LearningPathWithChapters(
    @Embedded
    val path: LearningPath,

    @Relation(
        parentColumn = "pathId",
        entityColumn = "pathId"
    )
    val chapters: List<Chapter> = emptyList()
) {
    val orderedChapters: List<Chapter>
        get() = chapters.sortedBy { it.sortOrder }
}
