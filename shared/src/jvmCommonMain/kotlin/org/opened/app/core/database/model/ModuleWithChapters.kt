package org.opened.app.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ModuleEntity

data class ModuleWithChapters(
    @Embedded
    val module: ModuleEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "moduleId"
    )
    val chapters: List<ChapterEntity> = emptyList()
) {
    val sortedChapters: List<ChapterEntity>
        get() = chapters.sortedBy { it.orderIndex }
}
