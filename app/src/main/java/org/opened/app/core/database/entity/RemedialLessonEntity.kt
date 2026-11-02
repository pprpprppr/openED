package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "remedial_lessons",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
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
        Index(value = ["chapterId"]),
        Index(value = ["pathId"]),
        Index(value = ["conceptKey"]),
        Index(value = ["isCompleted"])
    ]
)
data class RemedialLessonEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val pathId: String,
    val conceptKey: String,
    val title: String,
    val diagnosisReason: String,
    val targetedMarkdown: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
