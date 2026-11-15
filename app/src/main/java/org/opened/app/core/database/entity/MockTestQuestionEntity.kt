package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@Entity(
    tableName = "mock_test_questions",
    foreignKeys = [
        ForeignKey(
            entity = MockTestEntity::class,
            parentColumns = ["id"],
            childColumns = ["testId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["testId"]),
        Index(value = ["testId", "questionIndex"])
    ]
)
data class MockTestQuestionEntity(
    @PrimaryKey
    val id: String,
    val testId: String,
    val questionIndex: Int,
    val topic: String,
    val questionText: String,
    val optionsJson: String,
    val correctOptionIndex: Int,
    val explanation: String,
    val userSelectedOption: Int = -1, // -1 means unattempted
    val isFlagged: Boolean = false
) {
    @delegate:Ignore
    val optionsList: List<String> by lazy {
        try {
            val parsed = Json.decodeFromString<List<String>>(optionsJson)
            if (parsed.isNotEmpty()) parsed else listOf("Option A", "Option B", "Option C", "Option D")
        } catch (e: Exception) {
            listOf("Option A", "Option B", "Option C", "Option D")
        }
    }

    companion object {
        fun createOptionsJson(options: List<String>): String {
            return Json.encodeToString(options)
        }
    }
}
