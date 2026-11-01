package org.opened.app.learning.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opened.app.learning.model.BlockType
import java.util.UUID

@Entity(
    tableName = "learning_content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chapterId"]),
        Index(value = ["chapterId", "sortOrder"])
    ]
)
data class ContentBlock(
    @PrimaryKey
    val blockId: String = UUID.randomUUID().toString(),
    val chapterId: String,
    val blockType: BlockType,
    val content: String,
    val sortOrder: Int
)
