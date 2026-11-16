package org.opened.app.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.entity.QuestionType
import org.opened.app.core.database.model.ChapterStatus

class Converters {
    @TypeConverter
    fun fromChapterStatus(status: ChapterStatus?): String {
        return status?.name ?: ChapterStatus.PENDING.name
    }

    @TypeConverter
    fun toChapterStatus(value: String?): ChapterStatus {
        return try {
            if (value != null) ChapterStatus.valueOf(value) else ChapterStatus.PENDING
        } catch (e: Exception) {
            ChapterStatus.PENDING
        }
    }

    @TypeConverter
    fun fromExamCategory(category: ExamCategory?): String {
        return category?.name ?: ExamCategory.GENERAL.name
    }

    @TypeConverter
    fun toExamCategory(value: String?): ExamCategory {
        return try {
            if (value != null) ExamCategory.valueOf(value) else ExamCategory.GENERAL
        } catch (e: Exception) {
            ExamCategory.GENERAL
        }
    }

    @TypeConverter
    fun fromMockTestStatus(status: MockTestStatus?): String {
        return status?.name ?: MockTestStatus.NOT_STARTED.name
    }

    @TypeConverter
    fun toMockTestStatus(value: String?): MockTestStatus {
        return try {
            if (value != null) MockTestStatus.valueOf(value) else MockTestStatus.NOT_STARTED
        } catch (e: Exception) {
            MockTestStatus.NOT_STARTED
        }
    }

    @TypeConverter
    fun fromQuestionType(type: QuestionType?): String {
        return type?.name ?: QuestionType.SINGLE_CHOICE.name
    }

    @TypeConverter
    fun toQuestionType(value: String?): QuestionType {
        return try {
            if (value != null) QuestionType.valueOf(value) else QuestionType.SINGLE_CHOICE
        } catch (e: Exception) {
            QuestionType.SINGLE_CHOICE
        }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return if (list != null) Json.encodeToString(list) else "[]"
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return try {
            if (!value.isNullOrBlank()) Json.decodeFromString<List<String>>(value) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
