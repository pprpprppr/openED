package org.opened.app.learning.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "learning_paths",
    indices = [
        Index(value = ["lastAccessed"]),
        Index(value = ["createdAt"])
    ]
)
data class LearningPath(
    @PrimaryKey
    val pathId: String = UUID.randomUUID().toString(),
    val title: String,
    val theme: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis()
)
