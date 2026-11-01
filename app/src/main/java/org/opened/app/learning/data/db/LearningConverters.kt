package org.opened.app.learning.data.db

import androidx.room.TypeConverter
import org.opened.app.learning.model.BlockType
import org.opened.app.learning.model.ChapterStatus

class LearningConverters {
    @TypeConverter
    fun fromChapterStatus(status: ChapterStatus?): String {
        return status?.name ?: ChapterStatus.Draft.name
    }

    @TypeConverter
    fun toChapterStatus(value: String?): ChapterStatus {
        return try {
            if (value != null) ChapterStatus.valueOf(value) else ChapterStatus.Draft
        } catch (e: Exception) {
            ChapterStatus.Draft
        }
    }

    @TypeConverter
    fun fromBlockType(type: BlockType?): String {
        return type?.name ?: BlockType.TEXT.name
    }

    @TypeConverter
    fun toBlockType(value: String?): BlockType {
        return try {
            if (value != null) BlockType.valueOf(value) else BlockType.TEXT
        } catch (e: Exception) {
            BlockType.TEXT
        }
    }
}
