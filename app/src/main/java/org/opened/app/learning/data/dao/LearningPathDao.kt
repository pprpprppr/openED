package org.opened.app.learning.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.learning.data.entity.LearningPath
import org.opened.app.learning.model.LearningPathWithChapters

@Dao
interface LearningPathDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(path: LearningPath)

    @Update
    suspend fun updatePath(path: LearningPath)

    @Delete
    suspend fun deletePath(path: LearningPath)

    @Query("DELETE FROM learning_paths WHERE pathId = :pathId")
    suspend fun deletePathById(pathId: String)

    @Query("SELECT * FROM learning_paths ORDER BY lastAccessed DESC")
    fun getAllPathsFlow(): Flow<List<LearningPath>>

    @Query("SELECT * FROM learning_paths WHERE pathId = :pathId")
    suspend fun getPathById(pathId: String): LearningPath?

    /**
     * Observes the learning path and its chapters reactively.
     * Memory-efficient: ContentBlocks are NOT fetched in this query.
     */
    @Transaction
    @Query("SELECT * FROM learning_paths WHERE pathId = :pathId")
    fun getPathWithChaptersFlow(pathId: String): Flow<LearningPathWithChapters?>

    @Transaction
    @Query("SELECT * FROM learning_paths WHERE pathId = :pathId")
    suspend fun getPathWithChapters(pathId: String): LearningPathWithChapters?

    @Query("UPDATE learning_paths SET lastAccessed = :timestamp WHERE pathId = :pathId")
    suspend fun touchLastAccessed(pathId: String, timestamp: Long = System.currentTimeMillis())
}
