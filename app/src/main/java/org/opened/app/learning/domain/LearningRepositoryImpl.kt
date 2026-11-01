package org.opened.app.learning.domain

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.opened.app.learning.data.dao.ChapterDao
import org.opened.app.learning.data.dao.ContentBlockDao
import org.opened.app.learning.data.dao.LearningPathDao
import org.opened.app.learning.data.db.LearningDatabase
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.data.entity.LearningPath
import org.opened.app.learning.model.BlockType
import org.opened.app.learning.model.ChapterStatus
import org.opened.app.learning.model.ChapterWithContentBlocks
import org.opened.app.learning.model.LearningPathWithChapters
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningRepositoryImpl @Inject constructor(
    private val database: LearningDatabase,
    private val pathDao: LearningPathDao,
    private val chapterDao: ChapterDao,
    private val blockDao: ContentBlockDao
) : LearningRepository {

    override fun getAllLearningPaths(): Flow<List<LearningPath>> {
        return pathDao.getAllPathsFlow()
    }

    override fun getLearningPathWithChapters(pathId: String): Flow<LearningPathWithChapters?> {
        return pathDao.getPathWithChaptersFlow(pathId)
    }

    override fun getActiveChapterWithBlocks(chapterId: String): Flow<ChapterWithContentBlocks?> {
        return chapterDao.getChapterWithContentBlocksFlow(chapterId)
    }

    override suspend fun createLearningPath(
        title: String,
        theme: String,
        initialChapterTitles: List<String>
    ): String = withContext(Dispatchers.IO) {
        val pathId = UUID.randomUUID().toString()
        val path = LearningPath(
            pathId = pathId,
            title = title,
            theme = theme,
            createdAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis()
        )

        val chapters = initialChapterTitles.mapIndexed { index, chapterTitle ->
            Chapter(
                chapterId = UUID.randomUUID().toString(),
                pathId = pathId,
                title = chapterTitle,
                status = ChapterStatus.Draft,
                sortOrder = index,
                createdAt = System.currentTimeMillis()
            )
        }

        database.withTransaction {
            pathDao.insertPath(path)
            if (chapters.isNotEmpty()) {
                chapterDao.insertChapters(chapters)
            }
        }

        pathId
    }

    override suspend fun updatePath(path: LearningPath) = withContext(Dispatchers.IO) {
        pathDao.updatePath(path)
    }

    override suspend fun deletePath(pathId: String) = withContext(Dispatchers.IO) {
        pathDao.deletePathById(pathId)
    }

    override suspend fun touchLastAccessed(pathId: String) = withContext(Dispatchers.IO) {
        pathDao.touchLastAccessed(pathId, System.currentTimeMillis())
    }

    override suspend fun addChapter(pathId: String, title: String): Chapter = withContext(Dispatchers.IO) {
        val existing = pathDao.getPathWithChapters(pathId)?.chapters.orEmpty()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val chapter = Chapter(
            chapterId = UUID.randomUUID().toString(),
            pathId = pathId,
            title = title,
            status = ChapterStatus.Draft,
            sortOrder = nextOrder,
            createdAt = System.currentTimeMillis()
        )
        chapterDao.insertChapter(chapter)
        chapter
    }

    override suspend fun updateChapter(chapter: Chapter) = withContext(Dispatchers.IO) {
        chapterDao.updateChapter(chapter)
    }

    override suspend fun deleteChapter(chapterId: String) = withContext(Dispatchers.IO) {
        val chapter = chapterDao.getChapterById(chapterId)
        if (chapter != null) {
            chapterDao.deleteChapter(chapter)
        }
    }

    override suspend fun reorderChapters(orderedChapterIds: List<String>) = withContext(Dispatchers.IO) {
        chapterDao.reorderChapters(orderedChapterIds)
    }

    override suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus) = withContext(Dispatchers.IO) {
        chapterDao.updateChapterStatus(chapterId, status)
    }

    override suspend fun addContentBlock(
        chapterId: String,
        blockType: BlockType,
        content: String
    ): ContentBlock = withContext(Dispatchers.IO) {
        val existing = chapterDao.getChapterWithContentBlocks(chapterId)?.contentBlocks.orEmpty()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val block = ContentBlock(
            blockId = UUID.randomUUID().toString(),
            chapterId = chapterId,
            blockType = blockType,
            content = content,
            sortOrder = nextOrder
        )
        blockDao.insertBlock(block)
        block
    }

    override suspend fun updateContentBlock(block: ContentBlock) = withContext(Dispatchers.IO) {
        blockDao.updateBlock(block)
    }

    override suspend fun deleteContentBlock(block: ContentBlock) = withContext(Dispatchers.IO) {
        blockDao.deleteBlock(block)
    }

    override suspend fun reorderContentBlocks(orderedBlockIds: List<String>) = withContext(Dispatchers.IO) {
        blockDao.reorderContentBlocks(orderedBlockIds)
    }
}
