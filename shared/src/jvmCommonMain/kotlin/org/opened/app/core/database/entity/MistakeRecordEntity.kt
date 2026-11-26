package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "mistake_records",
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
        Index(value = ["conceptKey"])
    ]
)
data class MistakeRecordEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val pathId: String,
    val conceptKey: String,
    val questionPrompt: String,
    val userSelectedAnswer: String,
    val correctAnswer: String,
    val explanation: String,
    val failureCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
