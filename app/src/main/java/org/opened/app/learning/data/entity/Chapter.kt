package org.opened.app.learning.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opened.app.learning.model.ChapterStatus
import java.util.UUID

@Entity(
    tableName = "learning_chapters",
    foreignKeys = [
        ForeignKey(
            entity = LearningPath::class,
            parentColumns = ["pathId"],
            childColumns = ["pathId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pathId"]),
        Index(value = ["pathId", "sortOrder"])
    ]
)
data class Chapter(
    @PrimaryKey
    val chapterId: String = UUID.randomUUID().toString(),
    val pathId: String,
    val title: String,
    val status: ChapterStatus = ChapterStatus.Draft,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis()
)
