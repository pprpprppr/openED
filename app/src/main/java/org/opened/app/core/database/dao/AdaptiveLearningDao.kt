package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.LearningObjectiveEntity
import org.opened.app.core.database.entity.MistakeRecordEntity
import org.opened.app.core.database.entity.RemedialLessonEntity

@Dao
interface AdaptiveLearningDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObjectives(objectives: List<LearningObjectiveEntity>)

    @Update
    suspend fun updateObjective(objective: LearningObjectiveEntity)

    @Query("SELECT * FROM learning_objectives WHERE chapterId = :chapterId")
    fun getObjectivesForChapterFlow(chapterId: String): Flow<List<LearningObjectiveEntity>>

    @Query("SELECT * FROM learning_objectives WHERE chapterId = :chapterId")
    suspend fun getObjectivesForChapter(chapterId: String): List<LearningObjectiveEntity>

    @Query("SELECT * FROM learning_objectives WHERE pathId = :pathId")
    suspend fun getObjectivesForPath(pathId: String): List<LearningObjectiveEntity>

    @Query("SELECT * FROM learning_objectives WHERE status = 'NEEDS_REMEDIAL' OR masteryScore < 60")
    fun getWeakObjectivesFlow(): Flow<List<LearningObjectiveEntity>>

    // Mistake Records
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: MistakeRecordEntity)

    @Query("SELECT * FROM mistake_records WHERE chapterId = :chapterId ORDER BY failureCount DESC")
    suspend fun getMistakesForChapter(chapterId: String): List<MistakeRecordEntity>

    @Query("SELECT * FROM mistake_records WHERE pathId = :pathId ORDER BY failureCount DESC, timestamp DESC")
    fun getMistakesForPathFlow(pathId: String): Flow<List<MistakeRecordEntity>>

    @Query("SELECT * FROM mistake_records WHERE chapterId = :chapterId AND conceptKey = :conceptKey LIMIT 1")
    suspend fun findMistakeByConcept(chapterId: String, conceptKey: String): MistakeRecordEntity?

    @Query("DELETE FROM mistake_records WHERE id = :id")
    suspend fun deleteMistake(id: String)

    // Remedial Lessons
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemedialLesson(lesson: RemedialLessonEntity)

    @Query("SELECT * FROM remedial_lessons WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    fun getRemedialLessonsForChapterFlow(chapterId: String): Flow<List<RemedialLessonEntity>>

    @Query("SELECT * FROM remedial_lessons WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getPendingRemedialLessonsFlow(): Flow<List<RemedialLessonEntity>>

    @Query("UPDATE remedial_lessons SET isCompleted = 1 WHERE id = :id")
    suspend fun markRemedialLessonCompleted(id: String)
}
