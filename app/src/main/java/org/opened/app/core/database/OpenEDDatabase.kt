package org.opened.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.opened.app.core.database.dao.ChapterDao
import org.opened.app.core.database.dao.ChapterLinkDao
import org.opened.app.core.database.dao.ChapterQaDao
import org.opened.app.core.database.dao.ChapterRevisionDao
import org.opened.app.core.database.dao.FlashcardDao
import org.opened.app.core.database.dao.MasteryQuizDao
import org.opened.app.core.database.dao.ModuleDao
import org.opened.app.core.database.dao.StudyPathDao
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.MasteryQuizEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity

@Database(
    entities = [
        StudyPathEntity::class,
        ModuleEntity::class,
        ChapterEntity::class,
        ChapterRevisionEntity::class,
        ChapterLinkCrossRef::class,
        ChapterQaEntity::class,
        FlashcardEntity::class,
        MasteryQuizEntity::class
    ],
    version = 2,
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
    abstract fun flashcardDao(): FlashcardDao
    abstract fun masteryQuizDao(): MasteryQuizDao

    companion object {
        const val DATABASE_NAME = "opened_database.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add flashcards table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `flashcards` (
                        `id` TEXT NOT NULL,
                        `chapterId` TEXT NOT NULL,
                        `pathId` TEXT NOT NULL,
                        `front` TEXT NOT NULL,
                        `back` TEXT NOT NULL,
                        `intervalDays` INTEGER NOT NULL DEFAULT 1,
                        `easeFactor` REAL NOT NULL DEFAULT 2.5,
                        `repetitions` INTEGER NOT NULL DEFAULT 0,
                        `nextReviewTimestamp` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_chapterId` ON `flashcards` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_pathId` ON `flashcards` (`pathId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_nextReviewTimestamp` ON `flashcards` (`nextReviewTimestamp`)")

                // Add mastery_quizzes table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `mastery_quizzes` (
                        `id` TEXT NOT NULL,
                        `chapterId` TEXT NOT NULL,
                        `quizType` TEXT NOT NULL,
                        `sessionDataJson` TEXT NOT NULL,
                        `score` INTEGER NOT NULL DEFAULT 0,
                        `timestamp` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mastery_quizzes_chapterId` ON `mastery_quizzes` (`chapterId`)")
            }
        }
    }
}

typealias OpenEdDatabase = OpenEDDatabase
