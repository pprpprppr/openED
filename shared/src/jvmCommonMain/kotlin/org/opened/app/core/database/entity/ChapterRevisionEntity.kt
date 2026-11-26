package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "chapter_revisions",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chapterId"]),
        Index(value = ["chapterId", "timestamp"])
    ]
)
data class ChapterRevisionEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val previousMarkdown: String,
    val changeReason: String,
    val timestamp: Long = System.currentTimeMillis()
)
