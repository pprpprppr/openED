package org.opened.app.learning.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.model.ChapterStatus
import org.opened.app.learning.model.ChapterWithContentBlocks

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: Chapter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Update
    suspend fun updateChapter(chapter: Chapter)

    @Update
    suspend fun updateChapters(chapters: List<Chapter>)

    @Delete
    suspend fun deleteChapter(chapter: Chapter)

    @Query("SELECT * FROM learning_chapters WHERE chapterId = :chapterId")
    suspend fun getChapterById(chapterId: String): Chapter?

    @Query("SELECT * FROM learning_chapters WHERE pathId = :pathId ORDER BY sortOrder ASC")
    fun getChaptersForPathFlow(pathId: String): Flow<List<Chapter>>

    /**
     * Loads the specific chapter and its ordered content blocks on-demand.
     * Conserves memory by ensuring only the active reading chapter is materialized.
     */
    @Transaction
    @Query("SELECT * FROM learning_chapters WHERE chapterId = :chapterId")
    fun getChapterWithContentBlocksFlow(chapterId: String): Flow<ChapterWithContentBlocks?>

    @Transaction
    @Query("SELECT * FROM learning_chapters WHERE chapterId = :chapterId")
    suspend fun getChapterWithContentBlocks(chapterId: String): ChapterWithContentBlocks?

    @Query("UPDATE learning_chapters SET status = :status WHERE chapterId = :chapterId")
    suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus)

    @Query("UPDATE learning_chapters SET sortOrder = :sortOrder WHERE chapterId = :chapterId")
    suspend fun updateChapterSortOrder(chapterId: String, sortOrder: Int)

    /**
     * Batch updates chapter order after drag-and-drop reorganization.
     */
    @Transaction
    suspend fun reorderChapters(orderedChapterIds: List<String>) {
        orderedChapterIds.forEachIndexed { index, chapterId ->
            updateChapterSortOrder(chapterId, index)
        }
    }
}
