package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.FlashcardEntity

@Dao
interface FlashcardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: FlashcardEntity)

    @Update
    suspend fun update(card: FlashcardEntity)

    @Query("SELECT * FROM flashcards WHERE chapterId = :chapterId ORDER BY createdAt ASC")
    fun getFlashcardsForChapter(chapterId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE pathId = :pathId ORDER BY nextReviewTimestamp ASC")
    fun getFlashcardsForPath(pathId: String): Flow<List<FlashcardEntity>>

    @Query(
        """SELECT * FROM flashcards
           WHERE nextReviewTimestamp <= :nowMillis
           ORDER BY nextReviewTimestamp ASC"""
    )
    fun getDueFlashcards(nowMillis: Long): Flow<List<FlashcardEntity>>

    @Query(
        """SELECT COUNT(*) FROM flashcards
           WHERE nextReviewTimestamp <= :nowMillis"""
    )
    fun getDueCount(nowMillis: Long): Flow<Int>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getById(id: String): FlashcardEntity?

    @Query("DELETE FROM flashcards WHERE chapterId = :chapterId")
    suspend fun deleteForChapter(chapterId: String)

    @Query("SELECT * FROM flashcards ORDER BY nextReviewTimestamp ASC")
    suspend fun getAllFlashcards(): List<FlashcardEntity>

    @Query("DELETE FROM flashcards")
    suspend fun clearAll()
}
