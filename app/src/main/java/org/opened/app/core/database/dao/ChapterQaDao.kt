package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.ChapterQaEntity

@Dao
interface ChapterQaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(qa: ChapterQaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(qas: List<ChapterQaEntity>)

    @Update
    suspend fun update(qa: ChapterQaEntity)

    @Delete
    suspend fun delete(qa: ChapterQaEntity)

    @Query("UPDATE chapter_qas SET isMergedIntoContent = :isMerged WHERE id = :id")
    suspend fun markAsMerged(id: String, isMerged: Boolean = true)

    @Query("SELECT * FROM chapter_qas WHERE chapterId = :chapterId ORDER BY timestamp ASC")
    fun getQasForChapterFlow(chapterId: String): Flow<List<ChapterQaEntity>>

    @Query("SELECT * FROM chapter_qas WHERE LOWER(userQuestion) LIKE '%' || LOWER(:query) || '%' OR LOWER(aiAnswer) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchQas(query: String): List<ChapterQaEntity>

    @Query("SELECT * FROM chapter_qas")
    suspend fun getAllForExport(): List<ChapterQaEntity>

    @Query("DELETE FROM chapter_qas")
    suspend fun clearAll()
}
