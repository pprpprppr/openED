package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "study_paths",
    indices = [
        Index(value = ["topic"]),
        Index(value = ["createdAt"])
    ]
)
data class StudyPathEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val topic: String,
    val userLevel: String, // e.g. "BEGINNER", "INTERMEDIATE", "ADVANCED"
    val learningGoalsJson: String, // JSON array string
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val progress: Float = 0.0f // 0.0 to 1.0
)
