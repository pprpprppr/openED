package org.opened.app.core.drive

import kotlinx.serialization.json.Json
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.DocumentEntity
import org.opened.app.core.database.entity.DocumentChunkEntity
import org.opened.app.core.database.entity.LearningObjectiveEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.entity.SyncTombstoneEntity
import org.opened.app.core.database.model.ChapterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.ui.settings.SettingsUiState

class BackupBundleSerializationTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun testBundleRoundTripSerializationV2() {
        val path = StudyPathEntity(
            id = "path-1",
            title = "Modern Android Architecture",
            topic = "Android",
            userLevel = "ADVANCED",
            learningGoalsJson = "[\"MVI\", \"Compose\"]",
            progress = 0.5f
        )

        val module = ModuleEntity(
            id = "mod-1",
            pathId = "path-1",
            title = "Jetpack Compose Internals",
            orderIndex = 0,
            summary = "Compiler and runtime mechanics"
        )

        val chapter = ChapterEntity(
            id = "ch-1",
            moduleId = "mod-1",
            pathId = "path-1",
            title = "Positional Memoization",
            orderIndex = 0,
            markdownContent = "# Positional Memoization\nExplaining `remember` internal slot table.",
            summary = "Slot table deep-dive",
            status = ChapterStatus.COMPLETED
        )

        val revision = ChapterRevisionEntity(
            id = "rev-1",
            chapterId = "ch-1",
            previousMarkdown = "# Positional Memoization\nDraft 1.",
            changeReason = "Added code example"
        )

        val link = ChapterLinkCrossRef(
            sourceChapterId = "ch-1",
            targetChapterId = "ch-2",
            anchorText = "Slot Table Internals"
        )

        val qa = ChapterQaEntity(
            id = "qa-1",
            chapterId = "ch-1",
            userQuestion = "How does slot table manage gaps?",
            aiAnswer = "Gap buffers shift on insert.",
            isMergedIntoContent = true
        )

        val objective = LearningObjectiveEntity(
            id = "obj-1",
            chapterId = "ch-1",
            pathId = "path-1",
            objectiveText = "Understand Gap Buffer mechanics",
            conceptKey = "gap_buffer",
            masteryScore = 85
        )

        val doc = DocumentEntity(
            id = "doc-1",
            title = "Compose Internals Paper",
            fileName = "compose.pdf",
            mimeType = "application/pdf"
        )

        val chunk = DocumentChunkEntity(
            id = "chunk-1",
            documentId = "doc-1",
            chunkIndex = 0,
            rawText = "Slot table stores groups and slots."
        )

        val tombstone = SyncTombstoneEntity(
            id = "tomb-1",
            entityType = "CHAPTER",
            entityId = "ch-old",
            deletedAt = 1700000005000L
        )

        val originalBundle = BackupBundle(
            version = 2,
            exportedAt = 1700000000000L,
            appVersion = "1.0.0",
            studyPaths = listOf(path),
            modules = listOf(module),
            chapters = listOf(chapter),
            revisions = listOf(revision),
            links = listOf(link),
            qas = listOf(qa),
            learningObjectives = listOf(objective),
            documents = listOf(doc),
            documentChunks = listOf(chunk),
            tombstones = listOf(tombstone)
        )

        val encodedJson = json.encodeToString(BackupBundle.serializer(), originalBundle)
        val decodedBundle = json.decodeFromString(BackupBundle.serializer(), encodedJson)

        assertEquals(2, decodedBundle.version)
        assertEquals(1, decodedBundle.studyPaths.size)
        assertEquals("Modern Android Architecture", decodedBundle.studyPaths[0].title)
        assertEquals(1, decodedBundle.modules.size)
        assertEquals(1, decodedBundle.chapters.size)
        assertEquals(ChapterStatus.COMPLETED, decodedBundle.chapters[0].status)
        assertEquals(1, decodedBundle.revisions.size)
        assertEquals(1, decodedBundle.links.size)
        assertEquals("Slot Table Internals", decodedBundle.links[0].anchorText)
        assertEquals(1, decodedBundle.qas.size)
        assertEquals(true, decodedBundle.qas[0].isMergedIntoContent)
        assertEquals(1, decodedBundle.learningObjectives.size)
        assertEquals(85, decodedBundle.learningObjectives[0].masteryScore)
        assertEquals(1, decodedBundle.documents.size)
        assertEquals(1, decodedBundle.documentChunks.size)
        assertEquals(1, decodedBundle.tombstones.size)
        assertEquals("ch-old", decodedBundle.tombstones[0].entityId)
    }

    @Test
    fun testV1BundleBackwardsCompatibility() {
        val legacyJson = """
            {
              "version": 1,
              "exportedAt": 1700000000000,
              "appVersion": "0.9.0",
              "studyPaths": [],
              "modules": [],
              "chapters": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString(BackupBundle.serializer(), legacyJson)
        assertEquals(1, decoded.version)
        assertNotNull(decoded.learningObjectives)
        assertEquals(0, decoded.learningObjectives.size)
        assertNotNull(decoded.tombstones)
        assertEquals(0, decoded.tombstones.size)
    }

    @Test
    fun testSettingsUiStateDriveAuthorizationAlias() {
        val unauthorizedState = SettingsUiState(isAuthorizedWithDrive = false)
        assertFalse(unauthorizedState.isDriveAuthorized)

        val authorizedState = SettingsUiState(isAuthorizedWithDrive = true)
        assertTrue(authorizedState.isDriveAuthorized)
    }
}
