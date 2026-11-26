package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ExamCategory(val displayName: String, val defaultTimePerQuestionMinutes: Float) {
    ENTRANCE("Entrance Exam (JEE, NEET, GRE, GATE, CAT, MCAT)", 2.0f),
    COMPETITIVE("Competitive Exam (UPSC, Banking, SSC, State PSC)", 1.5f),
    CERTIFICATION("Professional Certification (AWS, GCP, Azure, CISSP, CKA)", 1.5f),
    UNIVERSITY("University & College Exam (Midterms, Finals, AP)", 2.0f),
    GENERAL("General Comprehensive Mastery", 1.5f)
}

@Serializable
enum class MockTestStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

@Serializable
@Entity(
    tableName = "mock_tests",
    foreignKeys = [
        ForeignKey(
            entity = StudyPathEntity::class,
            parentColumns = ["id"],
            childColumns = ["pathId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pathId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class MockTestEntity(
    @PrimaryKey
    val id: String,
    val pathId: String,
    val title: String,
    val examCategory: ExamCategory = ExamCategory.GENERAL,
    val targetExamName: String = "",
    val totalQuestions: Int = 10,
    val timeLimitMinutes: Int = 20,
    val status: MockTestStatus = MockTestStatus.NOT_STARTED,
    val score: Int = 0, // Percentage (0-100) or total points
    val totalCorrect: Int = 0,
    val totalAnswered: Int = 0,
    val timeSpentSeconds: Int = 0,
    val completedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
