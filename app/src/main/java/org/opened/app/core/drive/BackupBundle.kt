package org.opened.app.core.drive

import kotlinx.serialization.Serializable
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
import org.opened.app.core.database.entity.SyncTombstoneEntity

@Serializable
data class BackupBundle(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val studyPaths: List<StudyPathEntity> = emptyList(),
    val modules: List<ModuleEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val revisions: List<ChapterRevisionEntity> = emptyList(),
    val links: List<ChapterLinkCrossRef> = emptyList(),
    val qas: List<ChapterQaEntity> = emptyList(),
    val flashcards: List<FlashcardEntity> = emptyList(),
    val masteryQuizzes: List<MasteryQuizEntity> = emptyList(),
    // Phase 4: Adaptive Mastery
    val learningObjectives: List<LearningObjectiveEntity> = emptyList(),
    val mistakes: List<MistakeRecordEntity> = emptyList(),
    val remedials: List<RemedialLessonEntity> = emptyList(),
    // Phase 6: Document Intelligence
    val documents: List<DocumentEntity> = emptyList(),
    val documentChunks: List<DocumentChunkEntity> = emptyList(),
    // Phase 9: Sync Architecture
    val tombstones: List<SyncTombstoneEntity> = emptyList()
)
