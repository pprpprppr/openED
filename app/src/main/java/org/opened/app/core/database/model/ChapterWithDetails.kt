package org.opened.app.core.database.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity

data class ChapterWithDetails(
    @Embedded
    val chapter: ChapterEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "chapterId"
    )
    val revisions: List<ChapterRevisionEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "chapterId"
    )
    val qas: List<ChapterQaEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ChapterLinkCrossRef::class,
            parentColumn = "sourceChapterId",
            entityColumn = "targetChapterId"
        )
    )
    val outgoingChapters: List<ChapterEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ChapterLinkCrossRef::class,
            parentColumn = "targetChapterId",
            entityColumn = "sourceChapterId"
        )
    )
    val backlinkChapters: List<ChapterEntity> = emptyList()
)
