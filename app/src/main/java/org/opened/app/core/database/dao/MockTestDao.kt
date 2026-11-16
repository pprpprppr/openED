package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.model.MockTestWithQuestions

@Dao
interface MockTestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMockTest(test: MockTestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<MockTestQuestionEntity>)

    @Update
    suspend fun updateMockTest(test: MockTestEntity)

    @Update
    suspend fun updateQuestion(question: MockTestQuestionEntity)

    @Query("SELECT * FROM mock_tests WHERE pathId = :pathId ORDER BY createdAt DESC")
    fun getMockTestsForPathFlow(pathId: String): Flow<List<MockTestEntity>>

    @Query("SELECT * FROM mock_tests WHERE pathId = :pathId ORDER BY createdAt DESC")
    suspend fun getMockTestsForPath(pathId: String): List<MockTestEntity>

    @Query("SELECT * FROM mock_tests ORDER BY createdAt DESC")
    fun getAllMockTestsFlow(): Flow<List<MockTestEntity>>

    @Query("SELECT * FROM mock_tests WHERE id = :id LIMIT 1")
    suspend fun getMockTestById(id: String): MockTestEntity?

    @Transaction
    @Query("SELECT * FROM mock_tests WHERE id = :id LIMIT 1")
    suspend fun getMockTestWithQuestions(id: String): MockTestWithQuestions?

    @Transaction
    @Query("SELECT * FROM mock_tests WHERE id = :id LIMIT 1")
    fun getMockTestWithQuestionsFlow(id: String): Flow<MockTestWithQuestions?>

    @Query("SELECT * FROM mock_test_questions WHERE testId = :testId ORDER BY questionIndex ASC")
    suspend fun getQuestionsForTest(testId: String): List<MockTestQuestionEntity>

    @Query("""
        SELECT q.* FROM mock_test_questions q
        INNER JOIN mock_tests t ON q.testId = t.id
        WHERE t.pathId = :pathId
        ORDER BY t.createdAt DESC, q.questionIndex ASC
    """)
    suspend fun getQuestionsForPath(pathId: String): List<MockTestQuestionEntity>

    @Query("UPDATE mock_test_questions SET userSelectedOption = :userSelectedOption WHERE id = :questionId")
    suspend fun updateQuestionAnswer(questionId: String, userSelectedOption: Int)

    @Query("UPDATE mock_test_questions SET userSelectedOption = :userSelectedOption, userSelectedAnswers = :userSelectedAnswers WHERE id = :questionId")
    suspend fun updateQuestionMultiAnswer(questionId: String, userSelectedOption: Int, userSelectedAnswers: List<String>)

    @Query("UPDATE mock_test_questions SET userSelectedOption = -1, userSelectedAnswers = '[]', isFlagged = 0 WHERE testId = :testId")
    suspend fun resetQuestionsForTest(testId: String)

    @Query("UPDATE mock_test_questions SET isFlagged = :isFlagged WHERE id = :questionId")
    suspend fun updateQuestionFlag(questionId: String, isFlagged: Boolean)

    @Query("DELETE FROM mock_tests WHERE id = :id")
    suspend fun deleteMockTest(id: String)

    @Query("DELETE FROM mock_tests WHERE pathId = :pathId")
    suspend fun deleteMockTestsForPath(pathId: String)

    @Query("SELECT * FROM mock_tests ORDER BY createdAt DESC")
    suspend fun getAllMockTests(): List<MockTestEntity>

    @Query("SELECT * FROM mock_test_questions")
    suspend fun getAllMockTestQuestions(): List<MockTestQuestionEntity>
}
