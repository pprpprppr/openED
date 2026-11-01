package org.opened.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.opened.app.core.database.dao.ChapterDao
import org.opened.app.core.database.dao.ChapterLinkDao
import org.opened.app.core.database.dao.ChapterQaDao
import org.opened.app.core.database.dao.ChapterRevisionDao
import org.opened.app.core.database.dao.ModuleDao
import org.opened.app.core.database.dao.StudyPathDao
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity

@Database(
    entities = [
        StudyPathEntity::class,
        ModuleEntity::class,
        ChapterEntity::class,
        ChapterRevisionEntity::class,
        ChapterLinkCrossRef::class,
        ChapterQaEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OpenEDDatabase : RoomDatabase() {
    abstract fun studyPathDao(): StudyPathDao
    abstract fun moduleDao(): ModuleDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterRevisionDao(): ChapterRevisionDao
    abstract fun chapterLinkDao(): ChapterLinkDao
    abstract fun chapterQaDao(): ChapterQaDao

    companion object {
        const val DATABASE_NAME = "opened_database.db"
    }
}

typealias OpenEdDatabase = OpenEDDatabase

