package org.opened.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.opened.app.learning.data.entity.Chapter
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.data.entity.LearningPath
import org.opened.app.learning.model.BlockType
import org.opened.app.learning.model.ChapterStatus
import org.opened.app.learning.model.ChapterWithContentBlocks
import org.opened.app.learning.model.LearningPathWithChapters
import java.util.UUID

class LearningDataModelsTest {

    @Test
    fun testLearningPathWithChaptersSortOrder() {
        val path = LearningPath(
            pathId = UUID.randomUUID().toString(),
            title = "Modern Distributed Systems",
            theme = "Cloud Systems"
        )

        val ch3 = Chapter(
            chapterId = "c3",
            pathId = path.pathId,
            title = "Raft Consensus",
            status = ChapterStatus.Draft,
            sortOrder = 2
        )
        val ch1 = Chapter(
            chapterId = "c1",
            pathId = path.pathId,
            title = "CAP Theorem & Partitioning",
            status = ChapterStatus.Published,
            sortOrder = 0
        )
        val ch2 = Chapter(
            chapterId = "c2",
            pathId = path.pathId,
            title = "Two-Phase Commit (2PC)",
            status = ChapterStatus.Generating,
            sortOrder = 1
        )

        // Insertion order out of sort order
        val relation = LearningPathWithChapters(
            path = path,
            chapters = listOf(ch3, ch1, ch2)
        )

        val ordered = relation.orderedChapters
        assertEquals("c1", ordered[0].chapterId)
        assertEquals("c2", ordered[1].chapterId)
        assertEquals("c3", ordered[2].chapterId)
    }

    @Test
    fun testChapterWithContentBlocksSortOrder() {
        val chapter = Chapter(
            chapterId = "c1",
            pathId = "p1",
            title = "Consensus Algorithms",
            status = ChapterStatus.Published,
            sortOrder = 0
        )

        val b2 = ContentBlock(
            blockId = "b2",
            chapterId = "c1",
            blockType = BlockType.CODE,
            content = "fun appendEntries() { ... }",
            sortOrder = 1
        )
        val b1 = ContentBlock(
            blockId = "b1",
            chapterId = "c1",
            blockType = BlockType.TEXT,
            content = "Introduction to leader election",
            sortOrder = 0
        )
        val b3 = ContentBlock(
            blockId = "b3",
            chapterId = "c1",
            blockType = BlockType.AI_PROMPT,
            content = "Explain log replication edge cases",
            sortOrder = 2
        )

        val relation = ChapterWithContentBlocks(
            chapter = chapter,
            contentBlocks = listOf(b3, b2, b1)
        )

        val orderedBlocks = relation.orderedContentBlocks
        assertEquals("b1", orderedBlocks[0].blockId)
        assertEquals("b2", orderedBlocks[1].blockId)
        assertEquals("b3", orderedBlocks[2].blockId)
        assertEquals(BlockType.TEXT, orderedBlocks[0].blockType)
        assertEquals(BlockType.CODE, orderedBlocks[1].blockType)
        assertEquals(BlockType.AI_PROMPT, orderedBlocks[2].blockType)
    }
}
