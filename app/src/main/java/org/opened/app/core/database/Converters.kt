package org.opened.app.core.database

import androidx.room.TypeConverter
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
}
