package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.StudyPathWithModules

@Dao
interface StudyPathDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(path: StudyPathEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(paths: List<StudyPathEntity>)

    @Update
    suspend fun update(path: StudyPathEntity)

    @Delete
    suspend fun delete(path: StudyPathEntity)

    @Query("DELETE FROM study_paths WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM study_paths ORDER BY updatedAt DESC")
    fun getAllPathsFlow(): Flow<List<StudyPathEntity>>

    @Transaction
    @Query("SELECT * FROM study_paths ORDER BY updatedAt DESC")
    fun getAllPathsWithModulesFlow(): Flow<List<StudyPathWithModules>>

    @Transaction
    @Query("SELECT * FROM study_paths WHERE id = :pathId")
    fun getPathWithModulesFlow(pathId: String): Flow<StudyPathWithModules?>

    @Transaction
    @Query("SELECT * FROM study_paths WHERE id = :pathId")
    suspend fun getPathWithModules(pathId: String): StudyPathWithModules?

    @Query("SELECT * FROM study_paths WHERE id = :pathId")
    suspend fun getById(pathId: String): StudyPathEntity?

    @Query("UPDATE study_paths SET progress = :progress, updatedAt = :updatedAt WHERE id = :pathId")
    suspend fun updateProgress(pathId: String, progress: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM study_paths")
    suspend fun getAllForExport(): List<StudyPathEntity>

    @Query("DELETE FROM study_paths")
    suspend fun clearAll()
}
