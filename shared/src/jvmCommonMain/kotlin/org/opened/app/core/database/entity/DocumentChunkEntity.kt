package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["documentId", "chunkIndex"]),
        Index(value = ["pageNumber"])
    ]
)
data class DocumentChunkEntity(
    @PrimaryKey
    val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val pageNumber: Int = 1,
    val sectionTitle: String = "",
    val rawText: String,
    val tokenEstimate: Int = 0,
    val keywords: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
