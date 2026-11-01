package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "chapter_links",
    primaryKeys = ["sourceChapterId", "targetChapterId"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceChapterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetChapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceChapterId"]),
        Index(value = ["targetChapterId"])
    ]
)
data class ChapterLinkCrossRef(
    val sourceChapterId: String,
    val targetChapterId: String,
    val anchorText: String = ""
)
