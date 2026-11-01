package org.opened.app.learning.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opened.app.learning.data.entity.ContentBlock

@Dao
interface ContentBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: ContentBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<ContentBlock>)

    @Update
    suspend fun updateBlock(block: ContentBlock)

    @Update
    suspend fun updateBlocks(blocks: List<ContentBlock>)

    @Delete
    suspend fun deleteBlock(block: ContentBlock)

    @Query("SELECT * FROM learning_content_blocks WHERE chapterId = :chapterId ORDER BY sortOrder ASC")
    fun getBlocksForChapterFlow(chapterId: String): Flow<List<ContentBlock>>

    @Query("DELETE FROM learning_content_blocks WHERE chapterId = :chapterId")
    suspend fun deleteBlocksForChapter(chapterId: String)

    @Query("UPDATE learning_content_blocks SET sortOrder = :sortOrder WHERE blockId = :blockId")
    suspend fun updateBlockSortOrder(blockId: String, sortOrder: Int)

    /**
     * Batch updates content block positions after drag-and-drop reorganization.
     */
    @Transaction
    suspend fun reorderContentBlocks(orderedBlockIds: List<String>) {
        orderedBlockIds.forEachIndexed { index, blockId ->
            updateBlockSortOrder(blockId, index)
        }
    }
}
