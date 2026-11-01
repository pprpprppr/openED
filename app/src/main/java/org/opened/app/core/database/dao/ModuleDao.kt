package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.model.ModuleWithChapters

@Dao
interface ModuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(module: ModuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(modules: List<ModuleEntity>)

    @Update
    suspend fun update(module: ModuleEntity)

    @Delete
    suspend fun delete(module: ModuleEntity)

    @Query("SELECT * FROM modules WHERE pathId = :pathId ORDER BY orderIndex ASC")
    fun getModulesForPath(pathId: String): Flow<List<ModuleEntity>>

    @Transaction
    @Query("SELECT * FROM modules WHERE pathId = :pathId ORDER BY orderIndex ASC")
    fun getModulesWithChaptersFlow(pathId: String): Flow<List<ModuleWithChapters>>

    @Query("SELECT * FROM modules WHERE id = :moduleId")
    suspend fun getById(moduleId: String): ModuleEntity?

    @Query("SELECT * FROM modules")
    suspend fun getAllForExport(): List<ModuleEntity>

    @Query("DELETE FROM modules")
    suspend fun clearAll()
}
