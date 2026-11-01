package org.opened.app.core.drive

import kotlinx.serialization.json.Json
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.ChapterStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupBundleSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testBundleRoundTripSerialization() {
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

        val originalBundle = BackupBundle(
            version = 1,
            exportedAt = 1700000000000L,
            appVersion = "1.0.0",
            studyPaths = listOf(path),
            modules = listOf(module),
            chapters = listOf(chapter),
            revisions = listOf(revision),
            links = listOf(link),
            qas = listOf(qa)
        )

        val encodedJson = json.encodeToString(BackupBundle.serializer(), originalBundle)
        val decodedBundle = json.decodeFromString(BackupBundle.serializer(), encodedJson)

        assertEquals(originalBundle.version, decodedBundle.version)
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
    }
}
