package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "flashcards",
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
        Index(value = ["nextReviewTimestamp"])
    ]
)
data class FlashcardEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val pathId: String,
    val front: String,
    val back: String,
    /** SM-2 interval in days */
    val intervalDays: Int = 1,
    /** SM-2 ease factor (starts at 2.5) */
    val easeFactor: Float = 2.5f,
    /** Number of successful reviews */
    val repetitions: Int = 0,
    /** Epoch millis of next review */
    val nextReviewTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    /** FSRS memory stability in days */
    val stability: Float = 1.0f,
    /** FSRS card difficulty (1.0 to 10.0) */
    val difficulty: Float = 5.0f,
    /** FSRS state: 0=New, 1=Learning, 2=Review, 3=Relearning */
    val state: Int = 0,
    /** Elapsed days since last review */
    val elapsedDays: Int = 0,
    /** Timestamp of last review */
    val lastReviewTimestamp: Long = 0L
)
