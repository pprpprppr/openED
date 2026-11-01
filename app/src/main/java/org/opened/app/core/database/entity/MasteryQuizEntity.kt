package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Stores the result of a Feynman/quiz session for a chapter.
 */
@Serializable
@Entity(
    tableName = "mastery_quizzes",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chapterId"])]
)
data class MasteryQuizEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    /** "QUIZ" or "FEYNMAN" */
    val quizType: String,
    /** JSON-encoded quiz questions + answers or Feynman evaluation */
    val sessionDataJson: String,
    /** 0-100 score for Feynman, percentage correct for Quiz */
    val score: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
