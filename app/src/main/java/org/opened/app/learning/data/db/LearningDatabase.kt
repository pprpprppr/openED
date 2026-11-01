package org.opened.app.learning.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.opened.app.learning.data.dao.ChapterDao
import org.opened.app.learning.data.dao.ContentBlockDao
import org.opened.app.learning.data.dao.LearningPathDao
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.data.entity.LearningPath

@Database(
    entities = [
        LearningPath::class,
        Chapter::class,
        ContentBlock::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(LearningConverters::class)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningPathDao(): LearningPathDao
    abstract fun chapterDao(): ChapterDao
    abstract fun contentBlockDao(): ContentBlockDao

    companion object {
        const val DATABASE_NAME = "opened_learning_paths.db"
    }
}
