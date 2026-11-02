package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["title"]),
        Index(value = ["createdAt"])
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val chunkCount: Int = 0,
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
