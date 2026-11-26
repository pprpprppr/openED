package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "sync_tombstones",
    indices = [
        Index(value = ["entityType", "entityId"], unique = true),
        Index(value = ["deletedAt"])
    ]
)
data class SyncTombstoneEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val entityType: String, // "STUDY_PATH", "MODULE", "CHAPTER", "FLASHCARD", "DOCUMENT"
    val entityId: String,
    val deletedAt: Long = System.currentTimeMillis()
)
