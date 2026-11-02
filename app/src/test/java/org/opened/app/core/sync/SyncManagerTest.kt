package org.opened.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.database.entity.SyncTombstoneEntity

class SyncManagerTest {

    @Test
    fun testTombstoneFilteringLogic() {
        val tombstones = listOf(
            SyncTombstoneEntity(
                entityType = "CHAPTER",
                entityId = "chapter-123",
                deletedAt = 1000L
            ),
            SyncTombstoneEntity(
                entityType = "DOCUMENT",
                entityId = "doc-456",
                deletedAt = 2000L
            )
        )

        val tombstoneMap = tombstones.associateBy { "${it.entityType}:${it.entityId}" }

        // An update to chapter-123 prior to deletion should be rejected
        val oldUpdateTimestamp = 800L
        val chapterTombstone = tombstoneMap["CHAPTER:chapter-123"]
        assertTrue(chapterTombstone != null && oldUpdateTimestamp <= chapterTombstone.deletedAt)

        // An entity with a later timestamp than the tombstone can be legitimately resurrected or recreated
        val newUpdateTimestamp = 1500L
        assertFalse(chapterTombstone != null && newUpdateTimestamp <= chapterTombstone.deletedAt)

        // An untombstoned entity passes through cleanly
        val untombstoned = tombstoneMap["CHAPTER:chapter-789"]
        assertEquals(null, untombstoned)
    }

    @Test
    fun testSyncRevisionMetadataGeneration() {
        val metadata = SyncRevisionMetadata(
            revisionId = "rev-001",
            providerName = "Local Archive",
            timestamp = 1700000000000L,
            sizeBytes = 2048L,
            description = "Test snapshot"
        )

        assertEquals("rev-001", metadata.revisionId)
        assertEquals(2048L, metadata.sizeBytes)
        assertEquals("Local Archive", metadata.providerName)
    }
}
