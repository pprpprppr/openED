package org.opened.app.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity

data class StudyPathWithModules(
    @Embedded
    val studyPath: StudyPathEntity,

    @Relation(
        entity = ModuleEntity::class,
        parentColumn = "id",
        entityColumn = "pathId"
    )
    val modules: List<ModuleWithChapters> = emptyList()
) {
    val sortedModules: List<ModuleWithChapters>
        get() = modules.sortedBy { it.module.orderIndex }

    val allChapters: List<org.opened.app.core.database.entity.ChapterEntity>
        get() = modules.flatMap { it.chapters }

    val completedChaptersCount: Int
        get() = allChapters.count { it.status == ChapterStatus.COMPLETED }

    val totalChaptersCount: Int
        get() = allChapters.size

    val calculatedProgress: Float
        get() = if (totalChaptersCount > 0) completedChaptersCount.toFloat() / totalChaptersCount.toFloat() else 0f
}
