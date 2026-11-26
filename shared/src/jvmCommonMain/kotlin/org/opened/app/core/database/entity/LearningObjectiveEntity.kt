package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "learning_objectives",
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
        Index(value = ["conceptKey"]),
        Index(value = ["status"])
    ]
)
data class LearningObjectiveEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val pathId: String,
    val objectiveText: String,
    val conceptKey: String,
    /** Mastery percentage 0 - 100 */
    val masteryScore: Int = 0,
    /** Status: NOT_STARTED, IN_PROGRESS, MASTERED, NEEDS_REMEDIAL */
    val status: String = "NOT_STARTED",
    /** Self-assessed or inferred confidence (0.0 to 5.0) */
    val confidenceScore: Float = 0.0f,
    /** Study duration in seconds spent on this objective */
    val studyDurationSeconds: Long = 0L,
    /** Number of assessments or reviews */
    val reviewCount: Int = 0,
    val lastEvaluatedAt: Long = System.currentTimeMillis()
)
