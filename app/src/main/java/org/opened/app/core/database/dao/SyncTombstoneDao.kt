package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<SyncTombstoneEntity>)

    @Query("SELECT * FROM sync_tombstones WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getTombstone(entityType: String, entityId: String): SyncTombstoneEntity?

    @Query("SELECT * FROM sync_tombstones ORDER BY deletedAt DESC")
    suspend fun getAllTombstones(): List<SyncTombstoneEntity>

    @Query("SELECT * FROM sync_tombstones ORDER BY deletedAt DESC")
    fun getAllTombstonesFlow(): Flow<List<SyncTombstoneEntity>>

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :olderThanMillis")
    suspend fun pruneTombstonesOlderThan(olderThanMillis: Long)
}
