package org.opened.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.MasteryQuizEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.database.model.ChapterWithDetails
import org.opened.app.core.database.model.SearchResultItem
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.core.drive.BackupBundle

interface OpenEDRepository {
    fun getAllStudyPathsFlow(): Flow<List<StudyPathWithModules>>
    fun getStudyPathWithModulesFlow(pathId: String): Flow<StudyPathWithModules?>
    suspend fun getStudyPathWithModules(pathId: String): StudyPathWithModules?
    suspend fun saveStudyPathWithModules(
        path: StudyPathEntity,
        modulesWithChapters: List<Pair<ModuleEntity, List<ChapterEntity>>>
    )
    suspend fun appendModulesWithChapters(
        pathId: String,
        modulesWithChapters: List<Pair<ModuleEntity, List<ChapterEntity>>>
    )
    suspend fun deleteStudyPath(pathId: String)

    fun getChapterWithDetailsFlow(chapterId: String): Flow<ChapterWithDetails?>
    suspend fun getChapterWithDetails(chapterId: String): ChapterWithDetails?
    suspend fun getChapter(chapterId: String): ChapterEntity?
    suspend fun updateChapterContent(chapterId: String, content: String, status: ChapterStatus = ChapterStatus.COMPLETED)
    suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus)

    suspend fun addChapterRevision(chapterId: String, previousMarkdown: String, changeReason: String): ChapterRevisionEntity
    suspend fun addChapterQa(chapterId: String, userQuestion: String, aiAnswer: String): ChapterQaEntity
    suspend fun markQaAsMerged(qaId: String, isMerged: Boolean = true)

    suspend fun syncChapterLinks(chapterId: String, content: String)
    suspend fun findChapterByTitle(title: String): ChapterEntity?
    suspend fun createTangentChapter(sourceChapterId: String, tangentTitle: String): ChapterEntity

    suspend fun recalculatePathProgress(pathId: String)
    suspend fun search(query: String): List<SearchResultItem>

    // --- Flashcards ---
    fun getFlashcardsForChapterFlow(chapterId: String): Flow<List<FlashcardEntity>>
    fun getDueFlashcardsFlow(nowMillis: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>>
    fun getDueFlashcardCount(): Flow<Int>
    suspend fun insertFlashcards(cards: List<FlashcardEntity>)
    suspend fun updateFlashcard(card: FlashcardEntity)
    suspend fun deleteFlashcardsForChapter(chapterId: String)
    suspend fun getAllFlashcards(): List<FlashcardEntity>

    // --- Mastery / Feynman ---
    fun getMasteryQuizzesFlow(chapterId: String): Flow<List<MasteryQuizEntity>>
    suspend fun saveMasteryQuiz(quiz: MasteryQuizEntity)
    suspend fun getAllQuizzes(): List<MasteryQuizEntity>

    // --- Knowledge Graph ---
    suspend fun getAllChapterLinkNodes(): List<ChapterLinkNode>

    // --- Backup/Restore ---
    suspend fun exportDatabase(): BackupBundle
    suspend fun importDatabase(bundle: BackupBundle, overwrite: Boolean)
}
