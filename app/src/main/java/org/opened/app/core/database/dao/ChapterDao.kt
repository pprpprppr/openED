package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.database.model.ChapterWithDetails

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Delete
    suspend fun delete(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getById(chapterId: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    fun getByIdFlow(chapterId: String): Flow<ChapterEntity?>

    @Transaction
    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    fun getChapterWithDetailsFlow(chapterId: String): Flow<ChapterWithDetails?>

    @Transaction
    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterWithDetails(chapterId: String): ChapterWithDetails?

    @Query("SELECT * FROM chapters WHERE moduleId = :moduleId ORDER BY orderIndex ASC")
    fun getChaptersForModuleFlow(moduleId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE pathId = :pathId ORDER BY orderIndex ASC")
    fun getChaptersForPathFlow(pathId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE pathId = :pathId ORDER BY orderIndex ASC")
    suspend fun getChaptersForPath(pathId: String): List<ChapterEntity>

    @Query("UPDATE chapters SET markdownContent = :markdown, status = :status, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateMarkdown(
        chapterId: String,
        markdown: String,
        status: ChapterStatus = ChapterStatus.COMPLETED,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chapters SET status = :status, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateStatus(
        chapterId: String,
        status: ChapterStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM chapters WHERE LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun findByExactTitle(title: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(markdownContent) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchChapters(query: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters")
    suspend fun getAllForExport(): List<ChapterEntity>

    @Query("DELETE FROM chapters")
    suspend fun clearAll()
}
