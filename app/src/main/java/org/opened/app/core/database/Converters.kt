package org.opened.app.core.database

import androidx.room.TypeConverter
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.MockTestStatus
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
}
