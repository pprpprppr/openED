package org.opened.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.opened.app.core.database.dao.AdaptiveLearningDao
import org.opened.app.core.database.dao.ChapterDao
import org.opened.app.core.database.dao.ChapterLinkDao
import org.opened.app.core.database.dao.ChapterQaDao
import org.opened.app.core.database.dao.ChapterRevisionDao
import org.opened.app.core.database.dao.DocumentDao
import org.opened.app.core.database.dao.FlashcardDao
import org.opened.app.core.database.dao.MasteryQuizDao
import org.opened.app.core.database.dao.ModuleDao
import org.opened.app.core.database.dao.StudyPathDao
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.DocumentChunkEntity
import org.opened.app.core.database.entity.DocumentEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.LearningObjectiveEntity
import org.opened.app.core.database.entity.MasteryQuizEntity
import org.opened.app.core.database.entity.MistakeRecordEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.RemedialLessonEntity
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
        MasteryQuizEntity::class,
        LearningObjectiveEntity::class,
        MistakeRecordEntity::class,
        RemedialLessonEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class
    ],
    version = 3,
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
    abstract fun adaptiveLearningDao(): AdaptiveLearningDao
    abstract fun documentDao(): DocumentDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Upgrade flashcards table with FSRS columns
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `stability` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `difficulty` REAL NOT NULL DEFAULT 5.0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `state` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `elapsedDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `lastReviewTimestamp` INTEGER NOT NULL DEFAULT 0")

                // 2. Create learning_objectives table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `learning_objectives` (
                        `id` TEXT NOT NULL,
                        `chapterId` TEXT NOT NULL,
                        `pathId` TEXT NOT NULL,
                        `objectiveText` TEXT NOT NULL,
                        `conceptKey` TEXT NOT NULL,
                        `masteryScore` INTEGER NOT NULL DEFAULT 0,
                        `status` TEXT NOT NULL DEFAULT 'NOT_STARTED',
                        `confidenceScore` REAL NOT NULL DEFAULT 0.0,
                        `studyDurationSeconds` INTEGER NOT NULL DEFAULT 0,
                        `reviewCount` INTEGER NOT NULL DEFAULT 0,
                        `lastEvaluatedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_objectives_chapterId` ON `learning_objectives` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_objectives_pathId` ON `learning_objectives` (`pathId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_objectives_conceptKey` ON `learning_objectives` (`conceptKey`)")

                // 3. Create mistake_records table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `mistake_records` (
                        `id` TEXT NOT NULL,
                        `chapterId` TEXT NOT NULL,
                        `pathId` TEXT NOT NULL,
                        `conceptKey` TEXT NOT NULL,
                        `questionPrompt` TEXT NOT NULL,
                        `userSelectedAnswer` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `failureCount` INTEGER NOT NULL DEFAULT 1,
                        `timestamp` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_chapterId` ON `mistake_records` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_pathId` ON `mistake_records` (`pathId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_conceptKey` ON `mistake_records` (`conceptKey`)")

                // 4. Create remedial_lessons table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `remedial_lessons` (
                        `id` TEXT NOT NULL,
                        `chapterId` TEXT NOT NULL,
                        `pathId` TEXT NOT NULL,
                        `conceptKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `diagnosisReason` TEXT NOT NULL,
                        `targetedMarkdown` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_chapterId` ON `remedial_lessons` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_pathId` ON `remedial_lessons` (`pathId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_conceptKey` ON `remedial_lessons` (`conceptKey`)")

                // 5. Create documents table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `documents` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL DEFAULT 0,
                        `pageCount` INTEGER NOT NULL DEFAULT 1,
                        `chunkCount` INTEGER NOT NULL DEFAULT 0,
                        `summary` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_title` ON `documents` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_createdAt` ON `documents` (`createdAt`)")

                // 6. Create document_chunks table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `document_chunks` (
                        `id` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `pageNumber` INTEGER NOT NULL DEFAULT 1,
                        `sectionTitle` TEXT NOT NULL DEFAULT '',
                        `rawText` TEXT NOT NULL,
                        `tokenEstimate` INTEGER NOT NULL DEFAULT 0,
                        `keywords` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_chunks_documentId` ON `document_chunks` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_chunks_documentId_chunkIndex` ON `document_chunks` (`documentId`, `chunkIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_chunks_pageNumber` ON `document_chunks` (`pageNumber`)")
            }
        }
    }
}

typealias OpenEdDatabase = OpenEDDatabase
