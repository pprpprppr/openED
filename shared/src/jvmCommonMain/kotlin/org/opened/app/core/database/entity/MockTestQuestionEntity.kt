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
enum class QuestionType {
    SINGLE_CHOICE,
    MULTI_SELECT
}

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
    val userSelectedOption: Int = -1, // -1 means unattempted (maintained for backward compat)
    val isFlagged: Boolean = false,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val correctAnswers: List<String> = emptyList(), // All valid answer texts for MULTI_SELECT or SINGLE_CHOICE
    val userSelectedAnswers: List<String> = emptyList() // User selected answer texts
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

    val isMultiSelect: Boolean
        get() = questionType == QuestionType.MULTI_SELECT

    /**
     * Resolves the list of correct option indices based on correctAnswers and correctOptionIndex fallback.
     */
    val effectiveCorrectIndices: List<Int>
        get() {
            if (correctAnswers.isNotEmpty()) {
                val indices = mutableListOf<Int>()
                for (ans in correctAnswers) {
                    val trimmed = ans.trim()
                    val idx = optionsList.indexOfFirst { it.trim().equals(trimmed, ignoreCase = true) }
                    if (idx >= 0) {
                        indices.add(idx)
                    } else {
                        val parsed = trimmed.toIntOrNull()
                        if (parsed != null && parsed in optionsList.indices) {
                            indices.add(parsed)
                        } else if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'Z') {
                            val letterIdx = trimmed[0].uppercaseChar() - 'A'
                            if (letterIdx in optionsList.indices) indices.add(letterIdx)
                        }
                    }
                }
                if (indices.isNotEmpty()) return indices.distinct().sorted()
            }
            return if (correctOptionIndex in optionsList.indices) listOf(correctOptionIndex) else listOf(0)
        }

    /**
     * Resolves the list of user selected option indices based on userSelectedAnswers and userSelectedOption fallback.
     */
    val effectiveSelectedIndices: List<Int>
        get() {
            if (userSelectedAnswers.isNotEmpty()) {
                val indices = mutableListOf<Int>()
                for (ans in userSelectedAnswers) {
                    val trimmed = ans.trim()
                    val idx = optionsList.indexOfFirst { it.trim().equals(trimmed, ignoreCase = true) }
                    if (idx >= 0) {
                        indices.add(idx)
                    } else {
                        val parsed = trimmed.toIntOrNull()
                        if (parsed != null && parsed in optionsList.indices) {
                            indices.add(parsed)
                        } else if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'Z') {
                            val letterIdx = trimmed[0].uppercaseChar() - 'A'
                            if (letterIdx in optionsList.indices) indices.add(letterIdx)
                        }
                    }
                }
                return indices.distinct().sorted()
            }
            return if (userSelectedOption >= 0 && userSelectedOption in optionsList.indices) listOf(userSelectedOption) else emptyList()
        }

    val isAnswered: Boolean
        get() = effectiveSelectedIndices.isNotEmpty()

    /**
     * Exact set equality grading: all correct options selected, zero incorrect options selected.
     */
    val isCorrect: Boolean
        get() {
            val selected = effectiveSelectedIndices.toSet()
            val correct = effectiveCorrectIndices.toSet()
            return selected.isNotEmpty() && selected == correct
        }

    companion object {
        fun createOptionsJson(options: List<String>): String {
            return Json.encodeToString(options)
        }
    }
}
