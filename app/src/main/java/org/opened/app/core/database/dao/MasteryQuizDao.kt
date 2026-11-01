package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.MasteryQuizEntity

@Dao
interface MasteryQuizDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quiz: MasteryQuizEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quizzes: List<MasteryQuizEntity>)

    @Query("SELECT * FROM mastery_quizzes WHERE chapterId = :chapterId ORDER BY timestamp DESC")
    fun getQuizzesForChapter(chapterId: String): Flow<List<MasteryQuizEntity>>

    @Query("SELECT * FROM mastery_quizzes WHERE chapterId = :chapterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForChapter(chapterId: String): MasteryQuizEntity?

    @Query("SELECT * FROM mastery_quizzes ORDER BY timestamp DESC")
    suspend fun getAllQuizzes(): List<MasteryQuizEntity>

    @Query("DELETE FROM mastery_quizzes")
    suspend fun clearAll()
}
