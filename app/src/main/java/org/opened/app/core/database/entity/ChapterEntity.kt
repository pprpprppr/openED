package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import org.opened.app.core.database.model.ChapterStatus

@Serializable
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = ModuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moduleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StudyPathEntity::class,
            parentColumns = ["id"],
            childColumns = ["pathId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["moduleId"]),
        Index(value = ["pathId"]),
        Index(value = ["moduleId", "orderIndex"]),
        Index(value = ["title"])
    ]
)
data class ChapterEntity(
    @PrimaryKey
    val id: String,
    val moduleId: String,
    val pathId: String,
    val title: String,
    val orderIndex: Int,
    val markdownContent: String = "",
    val summary: String = "",
    val status: ChapterStatus = ChapterStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
