package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.ChapterRevisionEntity

@Dao
interface ChapterRevisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(revision: ChapterRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(revisions: List<ChapterRevisionEntity>)

    @Delete
    suspend fun delete(revision: ChapterRevisionEntity)

    @Query("SELECT * FROM chapter_revisions WHERE chapterId = :chapterId ORDER BY timestamp DESC")
    fun getRevisionsForChapterFlow(chapterId: String): Flow<List<ChapterRevisionEntity>>

    @Query("SELECT * FROM chapter_revisions WHERE chapterId = :chapterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRevision(chapterId: String): ChapterRevisionEntity?

    @Query("SELECT * FROM chapter_revisions WHERE LOWER(changeReason) LIKE '%' || LOWER(:query) || '%' OR LOWER(previousMarkdown) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchRevisions(query: String): List<ChapterRevisionEntity>

    @Query("SELECT * FROM chapter_revisions")
    suspend fun getAllForExport(): List<ChapterRevisionEntity>

    @Query("DELETE FROM chapter_revisions")
    suspend fun clearAll()
}
