package org.opened.app.learning.domain

import kotlinx.coroutines.flow.Flow
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.data.entity.LearningPath
import org.opened.app.learning.model.BlockType
import org.opened.app.learning.model.ChapterStatus
import org.opened.app.learning.model.ChapterWithContentBlocks
import org.opened.app.learning.model.LearningPathWithChapters

interface LearningRepository {
    fun getAllLearningPaths(): Flow<List<LearningPath>>
    fun getLearningPathWithChapters(pathId: String): Flow<LearningPathWithChapters?>
    fun getActiveChapterWithBlocks(chapterId: String): Flow<ChapterWithContentBlocks?>

    suspend fun createLearningPath(
        title: String,
        theme: String,
        initialChapterTitles: List<String> = emptyList()
    ): String

    suspend fun updatePath(path: LearningPath)
    suspend fun deletePath(pathId: String)
    suspend fun touchLastAccessed(pathId: String)

    suspend fun addChapter(pathId: String, title: String): Chapter
    suspend fun updateChapter(chapter: Chapter)
    suspend fun deleteChapter(chapterId: String)
    suspend fun reorderChapters(orderedChapterIds: List<String>)
    suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus)

    suspend fun addContentBlock(chapterId: String, blockType: BlockType, content: String): ContentBlock
    suspend fun updateContentBlock(block: ContentBlock)
    suspend fun deleteContentBlock(block: ContentBlock)
    suspend fun reorderContentBlocks(orderedBlockIds: List<String>)
}
