package org.opened.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.DocumentChunkEntity
import org.opened.app.core.database.entity.DocumentEntity
import org.opened.app.core.database.entity.ExamCategory
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.LearningObjectiveEntity
import org.opened.app.core.database.entity.MasteryQuizEntity
import org.opened.app.core.database.entity.MistakeRecordEntity
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity
import org.opened.app.core.database.entity.MockTestStatus
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.QuestionType
import org.opened.app.core.database.entity.RemedialLessonEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.entity.SyncTombstoneEntity
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.database.model.ChapterWithDetails
import org.opened.app.core.database.model.MockTestWithQuestions
import org.opened.app.core.database.model.ModuleWithChapters
import org.opened.app.core.database.model.SearchResultItem
import org.opened.app.core.database.model.SearchResultType
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.core.drive.BackupBundle
import org.opened.app.domain.repository.OpenEDRepository
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

/**
 * Pure JVM SQLite-JDBC implementation of OpenEDRepository matching the Android Room v6 schema,
 * ensuring full database & backup interoperability across Android, Linux, and Windows.
 */
class DesktopOpenEDRepository(
    dbPath: String = DesktopPaths.databaseFile.absolutePath
) : OpenEDRepository {

    private val jdbcUrl = "jdbc:sqlite:$dbPath"
    private val dbLock = Any()
    private val changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    init {
        initializeSchema()
    }

    private fun <T> withConnection(transactional: Boolean = false, block: (Connection) -> T): T {
        synchronized(dbLock) {
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.createStatement().use { st ->
                    st.execute("PRAGMA foreign_keys = ON;")
                    st.execute("PRAGMA journal_mode = WAL;")
                    st.execute("PRAGMA busy_timeout = 5000;")
                }
                if (!transactional) {
                    return block(conn)
                }
                conn.autoCommit = false
                try {
                    val result = block(conn)
                    conn.commit()
                    return result
                } catch (e: Throwable) {
                    runCatching { conn.rollback() }
                    throw e
                }
            }
        }
    }

    private fun notifyChanged() {
        changes.tryEmit(Unit)
    }

    private fun <T> reactiveFlow(loader: () -> T): Flow<T> = flow {
        emit(loader())
        changes.collect {
            emit(loader())
        }
    }.flowOn(Dispatchers.IO)

    private fun initializeSchema() {
        withConnection(transactional = true) { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `study_paths` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `topic` TEXT NOT NULL, `userLevel` TEXT NOT NULL, `learningGoalsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `progress` REAL NOT NULL, `customPrompt` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_study_paths_topic` ON `study_paths` (`topic`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_study_paths_createdAt` ON `study_paths` (`createdAt`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_study_paths_updatedAt` ON `study_paths` (`updatedAt`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `modules` (`id` TEXT NOT NULL, `pathId` TEXT NOT NULL, `title` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `summary` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_modules_pathId` ON `modules` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_modules_pathId_orderIndex` ON `modules` (`pathId`, `orderIndex`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `chapters` (`id` TEXT NOT NULL, `moduleId` TEXT NOT NULL, `pathId` TEXT NOT NULL, `title` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `markdownContent` TEXT NOT NULL, `summary` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`moduleId`) REFERENCES `modules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapters_moduleId` ON `chapters` (`moduleId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapters_pathId` ON `chapters` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapters_moduleId_orderIndex` ON `chapters` (`moduleId`, `orderIndex`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapters_title` ON `chapters` (`title`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapters_status` ON `chapters` (`status`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapters_updatedAt` ON `chapters` (`updatedAt`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `chapter_revisions` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `previousMarkdown` TEXT NOT NULL, `changeReason` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapter_revisions_chapterId` ON `chapter_revisions` (`chapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapter_revisions_chapterId_timestamp` ON `chapter_revisions` (`chapterId`, `timestamp`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `chapter_links` (`sourceChapterId` TEXT NOT NULL, `targetChapterId` TEXT NOT NULL, `anchorText` TEXT NOT NULL, PRIMARY KEY(`sourceChapterId`, `targetChapterId`), FOREIGN KEY(`sourceChapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`targetChapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapter_links_sourceChapterId` ON `chapter_links` (`sourceChapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapter_links_targetChapterId` ON `chapter_links` (`targetChapterId`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `chapter_qas` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `userQuestion` TEXT NOT NULL, `aiAnswer` TEXT NOT NULL, `isMergedIntoContent` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapter_qas_chapterId` ON `chapter_qas` (`chapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_chapter_qas_chapterId_timestamp` ON `chapter_qas` (`chapterId`, `timestamp`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `flashcards` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `pathId` TEXT NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, `intervalDays` INTEGER NOT NULL, `easeFactor` REAL NOT NULL, `repetitions` INTEGER NOT NULL, `nextReviewTimestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `stability` REAL NOT NULL, `difficulty` REAL NOT NULL, `state` INTEGER NOT NULL, `elapsedDays` INTEGER NOT NULL, `lastReviewTimestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_flashcards_chapterId` ON `flashcards` (`chapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_flashcards_pathId` ON `flashcards` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_flashcards_nextReviewTimestamp` ON `flashcards` (`nextReviewTimestamp`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_flashcards_state` ON `flashcards` (`state`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `mastery_quizzes` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `quizType` TEXT NOT NULL, `sessionDataJson` TEXT NOT NULL, `score` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mastery_quizzes_chapterId` ON `mastery_quizzes` (`chapterId`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `learning_objectives` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `pathId` TEXT NOT NULL, `objectiveText` TEXT NOT NULL, `conceptKey` TEXT NOT NULL, `masteryScore` INTEGER NOT NULL, `status` TEXT NOT NULL, `confidenceScore` REAL NOT NULL, `studyDurationSeconds` INTEGER NOT NULL, `reviewCount` INTEGER NOT NULL, `lastEvaluatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_learning_objectives_chapterId` ON `learning_objectives` (`chapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_learning_objectives_pathId` ON `learning_objectives` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_learning_objectives_conceptKey` ON `learning_objectives` (`conceptKey`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_learning_objectives_status` ON `learning_objectives` (`status`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `mistake_records` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `pathId` TEXT NOT NULL, `conceptKey` TEXT NOT NULL, `questionPrompt` TEXT NOT NULL, `userSelectedAnswer` TEXT NOT NULL, `correctAnswer` TEXT NOT NULL, `explanation` TEXT NOT NULL, `failureCount` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mistake_records_chapterId` ON `mistake_records` (`chapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mistake_records_pathId` ON `mistake_records` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mistake_records_conceptKey` ON `mistake_records` (`conceptKey`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `remedial_lessons` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `pathId` TEXT NOT NULL, `conceptKey` TEXT NOT NULL, `title` TEXT NOT NULL, `diagnosisReason` TEXT NOT NULL, `targetedMarkdown` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_chapterId` ON `remedial_lessons` (`chapterId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_pathId` ON `remedial_lessons` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_conceptKey` ON `remedial_lessons` (`conceptKey`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_remedial_lessons_isCompleted` ON `remedial_lessons` (`isCompleted`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `pageCount` INTEGER NOT NULL, `chunkCount` INTEGER NOT NULL, `summary` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_documents_title` ON `documents` (`title`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_documents_createdAt` ON `documents` (`createdAt`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `document_chunks` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `pageNumber` INTEGER NOT NULL, `sectionTitle` TEXT NOT NULL, `rawText` TEXT NOT NULL, `tokenEstimate` INTEGER NOT NULL, `keywords` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_document_chunks_documentId` ON `document_chunks` (`documentId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_document_chunks_documentId_chunkIndex` ON `document_chunks` (`documentId`, `chunkIndex`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_document_chunks_pageNumber` ON `document_chunks` (`pageNumber`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `sync_tombstones` (`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_tombstones_entityType_entityId` ON `sync_tombstones` (`entityType`, `entityId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_sync_tombstones_deletedAt` ON `sync_tombstones` (`deletedAt`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `mock_tests` (`id` TEXT NOT NULL, `pathId` TEXT NOT NULL, `title` TEXT NOT NULL, `examCategory` TEXT NOT NULL, `targetExamName` TEXT NOT NULL, `totalQuestions` INTEGER NOT NULL, `timeLimitMinutes` INTEGER NOT NULL, `status` TEXT NOT NULL, `score` INTEGER NOT NULL, `totalCorrect` INTEGER NOT NULL, `totalAnswered` INTEGER NOT NULL, `timeSpentSeconds` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`pathId`) REFERENCES `study_paths`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mock_tests_pathId` ON `mock_tests` (`pathId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mock_tests_status` ON `mock_tests` (`status`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mock_tests_createdAt` ON `mock_tests` (`createdAt`)")

                st.executeUpdate("""CREATE TABLE IF NOT EXISTS `mock_test_questions` (`id` TEXT NOT NULL, `testId` TEXT NOT NULL, `questionIndex` INTEGER NOT NULL, `topic` TEXT NOT NULL, `questionText` TEXT NOT NULL, `optionsJson` TEXT NOT NULL, `correctOptionIndex` INTEGER NOT NULL, `explanation` TEXT NOT NULL, `userSelectedOption` INTEGER NOT NULL, `isFlagged` INTEGER NOT NULL, `questionType` TEXT NOT NULL DEFAULT 'SINGLE_CHOICE', `correctAnswers` TEXT NOT NULL DEFAULT '[]', `userSelectedAnswers` TEXT NOT NULL DEFAULT '[]', PRIMARY KEY(`id`), FOREIGN KEY(`testId`) REFERENCES `mock_tests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mock_test_questions_testId` ON `mock_test_questions` (`testId`)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS `index_mock_test_questions_testId_questionIndex` ON `mock_test_questions` (`testId`, `questionIndex`)")
            }
        }
    }

    // ---- Row Mappers ----

    private fun ResultSet.toStudyPath() = StudyPathEntity(
        id = getString("id"),
        title = getString("title"),
        topic = getString("topic"),
        userLevel = getString("userLevel"),
        learningGoalsJson = getString("learningGoalsJson"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
        progress = getFloat("progress"),
        customPrompt = getString("customPrompt") ?: ""
    )

    private fun ResultSet.toModule() = ModuleEntity(
        id = getString("id"),
        pathId = getString("pathId"),
        title = getString("title"),
        orderIndex = getInt("orderIndex"),
        summary = getString("summary")
    )

    private fun ResultSet.toChapter() = ChapterEntity(
        id = getString("id"),
        moduleId = getString("moduleId"),
        pathId = getString("pathId"),
        title = getString("title"),
        orderIndex = getInt("orderIndex"),
        markdownContent = getString("markdownContent"),
        summary = getString("summary"),
        status = runCatching { ChapterStatus.valueOf(getString("status")) }.getOrDefault(ChapterStatus.PENDING),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt")
    )

    private fun ResultSet.toRevision() = ChapterRevisionEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        previousMarkdown = getString("previousMarkdown"),
        changeReason = getString("changeReason"),
        timestamp = getLong("timestamp")
    )

    private fun ResultSet.toQa() = ChapterQaEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        userQuestion = getString("userQuestion"),
        aiAnswer = getString("aiAnswer"),
        isMergedIntoContent = getInt("isMergedIntoContent") != 0,
        timestamp = getLong("timestamp")
    )

    private fun ResultSet.toFlashcard() = FlashcardEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        pathId = getString("pathId"),
        front = getString("front"),
        back = getString("back"),
        intervalDays = getInt("intervalDays"),
        easeFactor = getFloat("easeFactor"),
        repetitions = getInt("repetitions"),
        nextReviewTimestamp = getLong("nextReviewTimestamp"),
        createdAt = getLong("createdAt"),
        stability = getFloat("stability"),
        difficulty = getFloat("difficulty"),
        state = getInt("state"),
        elapsedDays = getInt("elapsedDays"),
        lastReviewTimestamp = getLong("lastReviewTimestamp")
    )

    private fun ResultSet.toMasteryQuiz() = MasteryQuizEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        quizType = getString("quizType"),
        sessionDataJson = getString("sessionDataJson"),
        score = getInt("score"),
        timestamp = getLong("timestamp")
    )

    private fun ResultSet.toObjective() = LearningObjectiveEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        pathId = getString("pathId"),
        objectiveText = getString("objectiveText"),
        conceptKey = getString("conceptKey"),
        masteryScore = getInt("masteryScore"),
        status = getString("status"),
        confidenceScore = getFloat("confidenceScore"),
        studyDurationSeconds = getLong("studyDurationSeconds"),
        reviewCount = getInt("reviewCount"),
        lastEvaluatedAt = getLong("lastEvaluatedAt")
    )

    private fun ResultSet.toMistake() = MistakeRecordEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        pathId = getString("pathId"),
        conceptKey = getString("conceptKey"),
        questionPrompt = getString("questionPrompt"),
        userSelectedAnswer = getString("userSelectedAnswer"),
        correctAnswer = getString("correctAnswer"),
        explanation = getString("explanation"),
        failureCount = getInt("failureCount"),
        timestamp = getLong("timestamp")
    )

    private fun ResultSet.toRemedial() = RemedialLessonEntity(
        id = getString("id"),
        chapterId = getString("chapterId"),
        pathId = getString("pathId"),
        conceptKey = getString("conceptKey"),
        title = getString("title"),
        diagnosisReason = getString("diagnosisReason"),
        targetedMarkdown = getString("targetedMarkdown"),
        isCompleted = getInt("isCompleted") != 0,
        createdAt = getLong("createdAt")
    )

    private fun ResultSet.toDocument() = DocumentEntity(
        id = getString("id"),
        title = getString("title"),
        fileName = getString("fileName"),
        mimeType = getString("mimeType"),
        fileSizeBytes = getLong("fileSizeBytes"),
        pageCount = getInt("pageCount"),
        chunkCount = getInt("chunkCount"),
        summary = getString("summary"),
        createdAt = getLong("createdAt")
    )

    private fun ResultSet.toDocumentChunk() = DocumentChunkEntity(
        id = getString("id"),
        documentId = getString("documentId"),
        chunkIndex = getInt("chunkIndex"),
        pageNumber = getInt("pageNumber"),
        sectionTitle = getString("sectionTitle"),
        rawText = getString("rawText"),
        tokenEstimate = getInt("tokenEstimate"),
        keywords = getString("keywords"),
        createdAt = getLong("createdAt")
    )

    private fun ResultSet.toMockTest() = MockTestEntity(
        id = getString("id"),
        pathId = getString("pathId"),
        title = getString("title"),
        examCategory = runCatching { ExamCategory.valueOf(getString("examCategory")) }.getOrDefault(ExamCategory.GENERAL),
        targetExamName = getString("targetExamName"),
        totalQuestions = getInt("totalQuestions"),
        timeLimitMinutes = getInt("timeLimitMinutes"),
        status = runCatching { MockTestStatus.valueOf(getString("status")) }.getOrDefault(MockTestStatus.NOT_STARTED),
        score = getInt("score"),
        totalCorrect = getInt("totalCorrect"),
        totalAnswered = getInt("totalAnswered"),
        timeSpentSeconds = getInt("timeSpentSeconds"),
        completedAt = getLong("completedAt"),
        createdAt = getLong("createdAt")
    )

    private fun decodeStringList(raw: String?): List<String> =
        runCatching { if (!raw.isNullOrBlank()) Json.decodeFromString<List<String>>(raw) else emptyList() }.getOrDefault(emptyList())

    private fun ResultSet.toMockTestQuestion() = MockTestQuestionEntity(
        id = getString("id"),
        testId = getString("testId"),
        questionIndex = getInt("questionIndex"),
        topic = getString("topic"),
        questionText = getString("questionText"),
        optionsJson = getString("optionsJson"),
        correctOptionIndex = getInt("correctOptionIndex"),
        explanation = getString("explanation"),
        userSelectedOption = getInt("userSelectedOption"),
        isFlagged = getInt("isFlagged") != 0,
        questionType = runCatching { QuestionType.valueOf(getString("questionType")) }.getOrDefault(QuestionType.SINGLE_CHOICE),
        correctAnswers = decodeStringList(getString("correctAnswers")),
        userSelectedAnswers = decodeStringList(getString("userSelectedAnswers"))
    )

    // ---- Insert / Upsert Helpers ----

    private fun upsertStudyPath(conn: Connection, p: StudyPathEntity) {
        conn.prepareStatement(
            """INSERT OR REPLACE INTO study_paths (id, title, topic, userLevel, learningGoalsJson, createdAt, updatedAt, progress, customPrompt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            ps.setString(1, p.id); ps.setString(2, p.title); ps.setString(3, p.topic); ps.setString(4, p.userLevel)
            ps.setString(5, p.learningGoalsJson); ps.setLong(6, p.createdAt); ps.setLong(7, p.updatedAt)
            ps.setFloat(8, p.progress); ps.setString(9, p.customPrompt)
            ps.executeUpdate()
        }
    }

    private fun upsertModules(conn: Connection, modules: List<ModuleEntity>) {
        if (modules.isEmpty()) return
        conn.prepareStatement("""INSERT OR REPLACE INTO modules (id, pathId, title, orderIndex, summary) VALUES (?, ?, ?, ?, ?)""").use { ps ->
            for (m in modules) {
                ps.setString(1, m.id); ps.setString(2, m.pathId); ps.setString(3, m.title); ps.setInt(4, m.orderIndex); ps.setString(5, m.summary)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertChapters(conn: Connection, chapters: List<ChapterEntity>) {
        if (chapters.isEmpty()) return
        conn.prepareStatement(
            """INSERT OR REPLACE INTO chapters (id, moduleId, pathId, title, orderIndex, markdownContent, summary, status, createdAt, updatedAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            for (c in chapters) {
                ps.setString(1, c.id); ps.setString(2, c.moduleId); ps.setString(3, c.pathId); ps.setString(4, c.title)
                ps.setInt(5, c.orderIndex); ps.setString(6, c.markdownContent); ps.setString(7, c.summary)
                ps.setString(8, c.status.name); ps.setLong(9, c.createdAt); ps.setLong(10, c.updatedAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertRevisions(conn: Connection, revisions: List<ChapterRevisionEntity>) {
        if (revisions.isEmpty()) return
        conn.prepareStatement("""INSERT OR REPLACE INTO chapter_revisions (id, chapterId, previousMarkdown, changeReason, timestamp) VALUES (?, ?, ?, ?, ?)""").use { ps ->
            for (r in revisions) {
                ps.setString(1, r.id); ps.setString(2, r.chapterId); ps.setString(3, r.previousMarkdown)
                ps.setString(4, r.changeReason); ps.setLong(5, r.timestamp)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertLinks(conn: Connection, links: List<ChapterLinkCrossRef>) {
        if (links.isEmpty()) return
        conn.prepareStatement("""INSERT OR REPLACE INTO chapter_links (sourceChapterId, targetChapterId, anchorText) VALUES (?, ?, ?)""").use { ps ->
            for (l in links) {
                ps.setString(1, l.sourceChapterId); ps.setString(2, l.targetChapterId); ps.setString(3, l.anchorText)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertQas(conn: Connection, qas: List<ChapterQaEntity>) {
        if (qas.isEmpty()) return
        conn.prepareStatement("""INSERT OR REPLACE INTO chapter_qas (id, chapterId, userQuestion, aiAnswer, isMergedIntoContent, timestamp) VALUES (?, ?, ?, ?, ?, ?)""").use { ps ->
            for (q in qas) {
                ps.setString(1, q.id); ps.setString(2, q.chapterId); ps.setString(3, q.userQuestion)
                ps.setString(4, q.aiAnswer); ps.setInt(5, if (q.isMergedIntoContent) 1 else 0); ps.setLong(6, q.timestamp)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertFlashcards(conn: Connection, cards: List<FlashcardEntity>) {
        if (cards.isEmpty()) return
        conn.prepareStatement(
            """INSERT OR REPLACE INTO flashcards (id, chapterId, pathId, front, back, intervalDays, easeFactor, repetitions, nextReviewTimestamp, createdAt, stability, difficulty, state, elapsedDays, lastReviewTimestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            for (c in cards) {
                ps.setString(1, c.id); ps.setString(2, c.chapterId); ps.setString(3, c.pathId); ps.setString(4, c.front); ps.setString(5, c.back)
                ps.setInt(6, c.intervalDays); ps.setFloat(7, c.easeFactor); ps.setInt(8, c.repetitions); ps.setLong(9, c.nextReviewTimestamp)
                ps.setLong(10, c.createdAt); ps.setFloat(11, c.stability); ps.setFloat(12, c.difficulty); ps.setInt(13, c.state)
                ps.setInt(14, c.elapsedDays); ps.setLong(15, c.lastReviewTimestamp)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertMasteryQuizzes(conn: Connection, quizzes: List<MasteryQuizEntity>) {
        if (quizzes.isEmpty()) return
        conn.prepareStatement("""INSERT OR REPLACE INTO mastery_quizzes (id, chapterId, quizType, sessionDataJson, score, timestamp) VALUES (?, ?, ?, ?, ?, ?)""").use { ps ->
            for (q in quizzes) {
                ps.setString(1, q.id); ps.setString(2, q.chapterId); ps.setString(3, q.quizType)
                ps.setString(4, q.sessionDataJson); ps.setInt(5, q.score); ps.setLong(6, q.timestamp)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertObjectives(conn: Connection, objectives: List<LearningObjectiveEntity>) {
        if (objectives.isEmpty()) return
        conn.prepareStatement(
            """INSERT OR REPLACE INTO learning_objectives (id, chapterId, pathId, objectiveText, conceptKey, masteryScore, status, confidenceScore, studyDurationSeconds, reviewCount, lastEvaluatedAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            for (o in objectives) {
                ps.setString(1, o.id); ps.setString(2, o.chapterId); ps.setString(3, o.pathId); ps.setString(4, o.objectiveText)
                ps.setString(5, o.conceptKey); ps.setInt(6, o.masteryScore); ps.setString(7, o.status); ps.setFloat(8, o.confidenceScore)
                ps.setLong(9, o.studyDurationSeconds); ps.setInt(10, o.reviewCount); ps.setLong(11, o.lastEvaluatedAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertMistake(conn: Connection, m: MistakeRecordEntity) {
        conn.prepareStatement(
            """INSERT OR REPLACE INTO mistake_records (id, chapterId, pathId, conceptKey, questionPrompt, userSelectedAnswer, correctAnswer, explanation, failureCount, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            ps.setString(1, m.id); ps.setString(2, m.chapterId); ps.setString(3, m.pathId); ps.setString(4, m.conceptKey)
            ps.setString(5, m.questionPrompt); ps.setString(6, m.userSelectedAnswer); ps.setString(7, m.correctAnswer)
            ps.setString(8, m.explanation); ps.setInt(9, m.failureCount); ps.setLong(10, m.timestamp)
            ps.executeUpdate()
        }
    }

    private fun upsertRemedial(conn: Connection, r: RemedialLessonEntity) {
        conn.prepareStatement(
            """INSERT OR REPLACE INTO remedial_lessons (id, chapterId, pathId, conceptKey, title, diagnosisReason, targetedMarkdown, isCompleted, createdAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            ps.setString(1, r.id); ps.setString(2, r.chapterId); ps.setString(3, r.pathId); ps.setString(4, r.conceptKey)
            ps.setString(5, r.title); ps.setString(6, r.diagnosisReason); ps.setString(7, r.targetedMarkdown)
            ps.setInt(8, if (r.isCompleted) 1 else 0); ps.setLong(9, r.createdAt)
            ps.executeUpdate()
        }
    }

    private fun upsertDocument(conn: Connection, d: DocumentEntity) {
        conn.prepareStatement(
            """INSERT OR REPLACE INTO documents (id, title, fileName, mimeType, fileSizeBytes, pageCount, chunkCount, summary, createdAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            ps.setString(1, d.id); ps.setString(2, d.title); ps.setString(3, d.fileName); ps.setString(4, d.mimeType)
            ps.setLong(5, d.fileSizeBytes); ps.setInt(6, d.pageCount); ps.setInt(7, d.chunkCount)
            ps.setString(8, d.summary); ps.setLong(9, d.createdAt)
            ps.executeUpdate()
        }
    }

    private fun upsertDocumentChunks(conn: Connection, chunks: List<DocumentChunkEntity>) {
        if (chunks.isEmpty()) return
        conn.prepareStatement(
            """INSERT OR REPLACE INTO document_chunks (id, documentId, chunkIndex, pageNumber, sectionTitle, rawText, tokenEstimate, keywords, createdAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            for (c in chunks) {
                ps.setString(1, c.id); ps.setString(2, c.documentId); ps.setInt(3, c.chunkIndex); ps.setInt(4, c.pageNumber)
                ps.setString(5, c.sectionTitle); ps.setString(6, c.rawText); ps.setInt(7, c.tokenEstimate)
                ps.setString(8, c.keywords); ps.setLong(9, c.createdAt)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun upsertTombstone(conn: Connection, t: SyncTombstoneEntity) {
        conn.prepareStatement("""INSERT OR REPLACE INTO sync_tombstones (id, entityType, entityId, deletedAt) VALUES (?, ?, ?, ?)""").use { ps ->
            ps.setString(1, t.id); ps.setString(2, t.entityType); ps.setString(3, t.entityId); ps.setLong(4, t.deletedAt)
            ps.executeUpdate()
        }
    }

    private fun upsertMockTest(conn: Connection, t: MockTestEntity) {
        conn.prepareStatement(
            """INSERT OR REPLACE INTO mock_tests (id, pathId, title, examCategory, targetExamName, totalQuestions, timeLimitMinutes, status, score, totalCorrect, totalAnswered, timeSpentSeconds, completedAt, createdAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            ps.setString(1, t.id); ps.setString(2, t.pathId); ps.setString(3, t.title); ps.setString(4, t.examCategory.name)
            ps.setString(5, t.targetExamName); ps.setInt(6, t.totalQuestions); ps.setInt(7, t.timeLimitMinutes); ps.setString(8, t.status.name)
            ps.setInt(9, t.score); ps.setInt(10, t.totalCorrect); ps.setInt(11, t.totalAnswered); ps.setInt(12, t.timeSpentSeconds)
            ps.setLong(13, t.completedAt); ps.setLong(14, t.createdAt)
            ps.executeUpdate()
        }
    }

    private fun upsertMockTestQuestions(conn: Connection, questions: List<MockTestQuestionEntity>) {
        if (questions.isEmpty()) return
        conn.prepareStatement(
            """INSERT OR REPLACE INTO mock_test_questions (id, testId, questionIndex, topic, questionText, optionsJson, correctOptionIndex, explanation, userSelectedOption, isFlagged, questionType, correctAnswers, userSelectedAnswers)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            for (q in questions) {
                ps.setString(1, q.id); ps.setString(2, q.testId); ps.setInt(3, q.questionIndex); ps.setString(4, q.topic)
                ps.setString(5, q.questionText); ps.setString(6, q.optionsJson); ps.setInt(7, q.correctOptionIndex); ps.setString(8, q.explanation)
                ps.setInt(9, q.userSelectedOption); ps.setInt(10, if (q.isFlagged) 1 else 0); ps.setString(11, q.questionType.name)
                ps.setString(12, Json.encodeToString(q.correctAnswers)); ps.setString(13, Json.encodeToString(q.userSelectedAnswers))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    // ---- Queries ----

    private fun queryStudyPathWithModules(conn: Connection, pathId: String): StudyPathWithModules? {
        val path = conn.prepareStatement("SELECT * FROM study_paths WHERE id = ?").use { ps ->
            ps.setString(1, pathId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toStudyPath() else null }
        } ?: return null

        val modules = conn.prepareStatement("SELECT * FROM modules WHERE pathId = ? ORDER BY orderIndex ASC").use { ps ->
            ps.setString(1, pathId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toModule()) }
            }
        }
        val chaptersByModule = conn.prepareStatement("SELECT * FROM chapters WHERE pathId = ? ORDER BY orderIndex ASC").use { ps ->
            ps.setString(1, pathId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toChapter()) }
            }
        }.groupBy { it.moduleId }

        return StudyPathWithModules(
            studyPath = path,
            modules = modules.map { mod -> ModuleWithChapters(module = mod, chapters = chaptersByModule[mod.id].orEmpty()) }
        )
    }

    private fun queryAllStudyPathsWithModules(conn: Connection): List<StudyPathWithModules> {
        val paths = conn.prepareStatement("SELECT * FROM study_paths ORDER BY updatedAt DESC").use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toStudyPath()) } }
        }
        if (paths.isEmpty()) return emptyList()
        val modulesByPath = conn.prepareStatement("SELECT * FROM modules ORDER BY orderIndex ASC").use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toModule()) } }
        }.groupBy { it.pathId }
        val chaptersByModule = conn.prepareStatement("SELECT * FROM chapters ORDER BY orderIndex ASC").use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toChapter()) } }
        }.groupBy { it.moduleId }

        return paths.map { p ->
            val mods = modulesByPath[p.id].orEmpty().map { m ->
                ModuleWithChapters(module = m, chapters = chaptersByModule[m.id].orEmpty())
            }
            StudyPathWithModules(studyPath = p, modules = mods)
        }
    }

    private fun queryChapterWithDetails(conn: Connection, chapterId: String): ChapterWithDetails? {
        val chapter = conn.prepareStatement("SELECT * FROM chapters WHERE id = ?").use { ps ->
            ps.setString(1, chapterId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toChapter() else null }
        } ?: return null

        val revisions = conn.prepareStatement("SELECT * FROM chapter_revisions WHERE chapterId = ? ORDER BY timestamp DESC").use { ps ->
            ps.setString(1, chapterId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRevision()) } }
        }
        val qas = conn.prepareStatement("SELECT * FROM chapter_qas WHERE chapterId = ? ORDER BY timestamp ASC").use { ps ->
            ps.setString(1, chapterId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toQa()) } }
        }
        val outgoing = conn.prepareStatement(
            "SELECT c.* FROM chapters c INNER JOIN chapter_links l ON c.id = l.targetChapterId WHERE l.sourceChapterId = ?"
        ).use { ps ->
            ps.setString(1, chapterId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toChapter()) } }
        }
        val backlinks = conn.prepareStatement(
            "SELECT c.* FROM chapters c INNER JOIN chapter_links l ON c.id = l.sourceChapterId WHERE l.targetChapterId = ?"
        ).use { ps ->
            ps.setString(1, chapterId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toChapter()) } }
        }

        return ChapterWithDetails(
            chapter = chapter,
            revisions = revisions,
            qas = qas,
            outgoingChapters = outgoing,
            backlinkChapters = backlinks
        )
    }

    // ---- OpenEDRepository Interface Implementations ----

    override fun getAllStudyPathsFlow(): Flow<List<StudyPathWithModules>> = reactiveFlow {
        withConnection { queryAllStudyPathsWithModules(it) }
    }

    override fun getStudyPathWithModulesFlow(pathId: String): Flow<StudyPathWithModules?> = reactiveFlow {
        withConnection { queryStudyPathWithModules(it, pathId) }
    }

    override suspend fun getStudyPathWithModules(pathId: String): StudyPathWithModules? = withContext(Dispatchers.IO) {
        withConnection { queryStudyPathWithModules(it, pathId) }
    }

    override suspend fun saveStudyPath(path: StudyPathEntity) {
        withContext(Dispatchers.IO) {
            withConnection { upsertStudyPath(it, path) }
            notifyChanged()
        }
    }

    override suspend fun saveStudyPathWithModules(
        path: StudyPathEntity,
        modulesWithChapters: List<Pair<ModuleEntity, List<ChapterEntity>>>
    ) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                upsertStudyPath(conn, path)
                upsertModules(conn, modulesWithChapters.map { it.first })
                upsertChapters(conn, modulesWithChapters.flatMap { it.second })
            }
            notifyChanged()
        }
    }

    override suspend fun appendModulesWithChapters(
        pathId: String,
        modulesWithChapters: List<Pair<ModuleEntity, List<ChapterEntity>>>
    ) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                upsertModules(conn, modulesWithChapters.map { it.first })
                upsertChapters(conn, modulesWithChapters.flatMap { it.second })
            }
            recalculatePathProgress(pathId)
            notifyChanged()
        }
    }

    override suspend fun deleteStudyPath(pathId: String) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                conn.prepareStatement("DELETE FROM study_paths WHERE id = ?").use { ps ->
                    ps.setString(1, pathId)
                    ps.executeUpdate()
                }
                upsertTombstone(conn, SyncTombstoneEntity(entityType = "STUDY_PATH", entityId = pathId, deletedAt = System.currentTimeMillis()))
            }
            notifyChanged()
        }
    }

    override fun getChapterWithDetailsFlow(chapterId: String): Flow<ChapterWithDetails?> = reactiveFlow {
        withConnection { queryChapterWithDetails(it, chapterId) }
    }

    override suspend fun getChapterWithDetails(chapterId: String): ChapterWithDetails? = withContext(Dispatchers.IO) {
        withConnection { queryChapterWithDetails(it, chapterId) }
    }

    override suspend fun getChapter(chapterId: String): ChapterEntity? = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM chapters WHERE id = ?").use { ps ->
                ps.setString(1, chapterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toChapter() else null }
            }
        }
    }

    override suspend fun updateChapterContent(chapterId: String, content: String, status: ChapterStatus) {
        withContext(Dispatchers.IO) {
            val pathId = withConnection { conn ->
                val now = System.currentTimeMillis()
                conn.prepareStatement("UPDATE chapters SET markdownContent = ?, status = ?, updatedAt = ? WHERE id = ?").use { ps ->
                    ps.setString(1, content); ps.setString(2, status.name); ps.setLong(3, now); ps.setString(4, chapterId)
                    ps.executeUpdate()
                }
                conn.prepareStatement("SELECT pathId FROM chapters WHERE id = ?").use { ps ->
                    ps.setString(1, chapterId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getString("pathId") else null }
                }
            }
            syncChapterLinks(chapterId, content)
            if (pathId != null) recalculatePathProgress(pathId)
            notifyChanged()
        }
    }

    override suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus) {
        withContext(Dispatchers.IO) {
            val pathId = withConnection { conn ->
                val now = System.currentTimeMillis()
                conn.prepareStatement("UPDATE chapters SET status = ?, updatedAt = ? WHERE id = ?").use { ps ->
                    ps.setString(1, status.name); ps.setLong(2, now); ps.setString(3, chapterId)
                    ps.executeUpdate()
                }
                conn.prepareStatement("SELECT pathId FROM chapters WHERE id = ?").use { ps ->
                    ps.setString(1, chapterId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getString("pathId") else null }
                }
            }
            if (pathId != null) recalculatePathProgress(pathId)
            notifyChanged()
        }
    }

    override suspend fun addChapterRevision(chapterId: String, previousMarkdown: String, changeReason: String): ChapterRevisionEntity {
        return withContext(Dispatchers.IO) {
            val rev = ChapterRevisionEntity(
                id = UUID.randomUUID().toString(),
                chapterId = chapterId,
                previousMarkdown = previousMarkdown,
                changeReason = changeReason,
                timestamp = System.currentTimeMillis()
            )
            withConnection { upsertRevisions(it, listOf(rev)) }
            notifyChanged()
            rev
        }
    }

    override suspend fun addChapterQa(chapterId: String, userQuestion: String, aiAnswer: String): ChapterQaEntity {
        return withContext(Dispatchers.IO) {
            val qa = ChapterQaEntity(
                id = UUID.randomUUID().toString(),
                chapterId = chapterId,
                userQuestion = userQuestion,
                aiAnswer = aiAnswer,
                isMergedIntoContent = false,
                timestamp = System.currentTimeMillis()
            )
            withConnection { upsertQas(it, listOf(qa)) }
            notifyChanged()
            qa
        }
    }

    override suspend fun markQaAsMerged(qaId: String, isMerged: Boolean) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                conn.prepareStatement("UPDATE chapter_qas SET isMergedIntoContent = ? WHERE id = ?").use { ps ->
                    ps.setInt(1, if (isMerged) 1 else 0); ps.setString(2, qaId); ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    override suspend fun syncChapterLinks(chapterId: String, content: String) {
        withContext(Dispatchers.IO) {
            val regex = Regex("""\[\[(.*?)\]\]""")
            val titles = regex.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()
            withConnection(transactional = true) { conn ->
                val refs = mutableListOf<ChapterLinkCrossRef>()
                for (title in titles) {
                    val target = conn.prepareStatement("SELECT id FROM chapters WHERE LOWER(title) = LOWER(?) LIMIT 1").use { ps ->
                        ps.setString(1, title)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
                    }
                    if (target != null && target != chapterId) {
                        refs.add(ChapterLinkCrossRef(sourceChapterId = chapterId, targetChapterId = target, anchorText = title))
                    }
                }
                conn.prepareStatement("DELETE FROM chapter_links WHERE sourceChapterId = ?").use { ps ->
                    ps.setString(1, chapterId); ps.executeUpdate()
                }
                upsertLinks(conn, refs.distinctBy { it.targetChapterId })
            }
            notifyChanged()
        }
    }

    override suspend fun findChapterByTitle(title: String): ChapterEntity? = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM chapters WHERE LOWER(title) = LOWER(?) LIMIT 1").use { ps ->
                ps.setString(1, title.trim())
                ps.executeQuery().use { rs -> if (rs.next()) rs.toChapter() else null }
            }
        }
    }

    override suspend fun createTangentChapter(sourceChapterId: String, tangentTitle: String): ChapterEntity = withContext(Dispatchers.IO) {
        val created = withConnection(transactional = true) { conn ->
            val source = conn.prepareStatement("SELECT * FROM chapters WHERE id = ?").use { ps ->
                ps.setString(1, sourceChapterId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toChapter() else null }
            } ?: error("Source chapter not found")
            val maxOrder = conn.prepareStatement("SELECT COALESCE(MAX(orderIndex), ?) FROM chapters WHERE moduleId = ?").use { ps ->
                ps.setInt(1, source.orderIndex); ps.setString(2, source.moduleId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else source.orderIndex }
            }
            val now = System.currentTimeMillis()
            val tangent = ChapterEntity(
                id = UUID.randomUUID().toString(),
                moduleId = source.moduleId,
                pathId = source.pathId,
                title = tangentTitle.trim(),
                orderIndex = maxOrder + 1,
                markdownContent = "",
                summary = "Tangent deep-dive exploring $tangentTitle branching from '${source.title}'",
                status = ChapterStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
            upsertChapters(conn, listOf(tangent))
            upsertLinks(conn, listOf(
                ChapterLinkCrossRef(sourceChapterId, tangent.id, tangentTitle),
                ChapterLinkCrossRef(tangent.id, sourceChapterId, source.title)
            ))
            tangent
        }
        recalculatePathProgress(created.pathId)
        notifyChanged()
        created
    }

    override suspend fun recalculatePathProgress(pathId: String) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                val statuses = conn.prepareStatement("SELECT status FROM chapters WHERE pathId = ?").use { ps ->
                    ps.setString(1, pathId)
                    ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString("status")) } }
                }
                if (statuses.isNotEmpty()) {
                    val completed = statuses.count { it == ChapterStatus.COMPLETED.name }
                    val progress = completed.toFloat() / statuses.size.toFloat()
                    conn.prepareStatement("UPDATE study_paths SET progress = ?, updatedAt = ? WHERE id = ?").use { ps ->
                        ps.setFloat(1, progress); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, pathId)
                        ps.executeUpdate()
                    }
                }
            }
            notifyChanged()
        }
    }

    override suspend fun search(query: String): List<SearchResultItem> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            withConnection { conn ->
                val results = mutableListOf<SearchResultItem>()
                val paths = conn.prepareStatement("SELECT id, title FROM study_paths").use { ps ->
                    ps.executeQuery().use { rs ->
                        buildMap { while (rs.next()) put(rs.getString("id"), rs.getString("title")) }
                    }
                }
                val like = "%${query.trim()}%"

                conn.prepareStatement(
                    "SELECT * FROM chapters WHERE title LIKE ? OR markdownContent LIKE ? OR summary LIKE ?"
                ).use { ps ->
                    ps.setString(1, like); ps.setString(2, like); ps.setString(3, like)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val ch = rs.toChapter()
                            val snippet = if (ch.markdownContent.isNotBlank()) extractSnippet(ch.markdownContent, query) else ch.summary.ifBlank { "Unwritten chapter" }
                            results.add(SearchResultItem(ch.id, ch.pathId, ch.id, ch.title, paths[ch.pathId] ?: "Unknown Course", snippet, SearchResultType.CHAPTER, ch.updatedAt))
                        }
                    }
                }

                conn.prepareStatement(
                    "SELECT r.*, c.pathId, c.title AS chapterTitle FROM chapter_revisions r INNER JOIN chapters c ON r.chapterId = c.id WHERE r.changeReason LIKE ? OR r.previousMarkdown LIKE ?"
                ).use { ps ->
                    ps.setString(1, like); ps.setString(2, like)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val rev = rs.toRevision()
                            val pId = rs.getString("pathId")
                            val cTitle = rs.getString("chapterTitle")
                            results.add(SearchResultItem(rev.id, pId, rev.chapterId, cTitle, paths[pId] ?: "Unknown Course", "Edit Note: ${rev.changeReason} | ${extractSnippet(rev.previousMarkdown, query)}", SearchResultType.REVISION_NOTE, rev.timestamp))
                        }
                    }
                }

                conn.prepareStatement(
                    "SELECT q.*, c.pathId, c.title AS chapterTitle FROM chapter_qas q INNER JOIN chapters c ON q.chapterId = c.id WHERE q.userQuestion LIKE ? OR q.aiAnswer LIKE ?"
                ).use { ps ->
                    ps.setString(1, like); ps.setString(2, like)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val qa = rs.toQa()
                            val pId = rs.getString("pathId")
                            val cTitle = rs.getString("chapterTitle")
                            results.add(SearchResultItem(qa.id, pId, qa.chapterId, cTitle, paths[pId] ?: "Unknown Course", "Q: ${qa.userQuestion}\nA: ${extractSnippet(qa.aiAnswer, query)}", SearchResultType.QA_THREAD, qa.timestamp))
                        }
                    }
                }

                results.sortedByDescending { it.timestamp }
            }
        }
    }

    private fun extractSnippet(text: String, query: String, maxLength: Int = 160): String {
        val index = text.indexOf(query, ignoreCase = true)
        if (index == -1) return text.take(maxLength).replace("\n", " ") + if (text.length > maxLength) "..." else ""
        val start = (index - 40).coerceAtLeast(0)
        val end = (index + query.length + 80).coerceAtMost(text.length)
        return (if (start > 0) "..." else "") + text.substring(start, end).replace("\n", " ") + (if (end < text.length) "..." else "")
    }

    // ---- Flashcards ----

    override fun getFlashcardsForChapterFlow(chapterId: String): Flow<List<FlashcardEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM flashcards WHERE chapterId = ? ORDER BY createdAt ASC").use { ps ->
                ps.setString(1, chapterId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toFlashcard()) } }
            }
        }
    }

    override fun getDueFlashcardsFlow(nowMillis: Long): Flow<List<FlashcardEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM flashcards WHERE nextReviewTimestamp <= ? ORDER BY nextReviewTimestamp ASC").use { ps ->
                ps.setLong(1, System.currentTimeMillis().coerceAtLeast(nowMillis))
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toFlashcard()) } }
            }
        }
    }

    override fun getDueFlashcardCount(): Flow<Int> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM flashcards WHERE nextReviewTimestamp <= ?").use { ps ->
                ps.setLong(1, System.currentTimeMillis())
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }
    }

    override suspend fun insertFlashcards(cards: List<FlashcardEntity>) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { upsertFlashcards(it, cards) }
            notifyChanged()
        }
    }

    override suspend fun updateFlashcard(card: FlashcardEntity) {
        withContext(Dispatchers.IO) {
            withConnection { upsertFlashcards(it, listOf(card)) }
            notifyChanged()
        }
    }

    override suspend fun deleteFlashcardsForChapter(chapterId: String) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                conn.prepareStatement("DELETE FROM flashcards WHERE chapterId = ?").use { ps ->
                    ps.setString(1, chapterId); ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    override suspend fun getAllFlashcards(): List<FlashcardEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM flashcards ORDER BY nextReviewTimestamp ASC").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toFlashcard()) } }
            }
        }
    }

    // ---- Mastery / Feynman ----

    override fun getMasteryQuizzesFlow(chapterId: String): Flow<List<MasteryQuizEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mastery_quizzes WHERE chapterId = ? ORDER BY timestamp DESC").use { ps ->
                ps.setString(1, chapterId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMasteryQuiz()) } }
            }
        }
    }

    override suspend fun saveMasteryQuiz(quiz: MasteryQuizEntity) {
        withContext(Dispatchers.IO) {
            withConnection { upsertMasteryQuizzes(it, listOf(quiz)) }
            notifyChanged()
        }
    }

    override suspend fun getAllQuizzes(): List<MasteryQuizEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mastery_quizzes").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMasteryQuiz()) } }
            }
        }
    }

    // ---- Adaptive Learning ----

    override fun getObjectivesForChapterFlow(chapterId: String): Flow<List<LearningObjectiveEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM learning_objectives WHERE chapterId = ?").use { ps ->
                ps.setString(1, chapterId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toObjective()) } }
            }
        }
    }

    override suspend fun saveObjectives(objectives: List<LearningObjectiveEntity>) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { upsertObjectives(it, objectives) }
            notifyChanged()
        }
    }

    override suspend fun updateObjective(objective: LearningObjectiveEntity) {
        withContext(Dispatchers.IO) {
            withConnection { upsertObjectives(it, listOf(objective)) }
            notifyChanged()
        }
    }

    override fun getWeakObjectivesFlow(): Flow<List<LearningObjectiveEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM learning_objectives WHERE status = 'NEEDS_REMEDIAL' OR masteryScore < 60").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toObjective()) } }
            }
        }
    }

    override suspend fun recordMistake(mistake: MistakeRecordEntity) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                val existing = conn.prepareStatement("SELECT * FROM mistake_records WHERE chapterId = ? AND conceptKey = ? LIMIT 1").use { ps ->
                    ps.setString(1, mistake.chapterId); ps.setString(2, mistake.conceptKey)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.toMistake() else null }
                }
                if (existing != null) {
                    upsertMistake(
                        conn,
                        existing.copy(
                            failureCount = existing.failureCount + 1,
                            timestamp = System.currentTimeMillis(),
                            userSelectedAnswer = mistake.userSelectedAnswer,
                            questionPrompt = mistake.questionPrompt
                        )
                    )
                } else {
                    upsertMistake(conn, mistake)
                }
            }
            notifyChanged()
        }
    }

    override suspend fun getMistakesForChapter(chapterId: String): List<MistakeRecordEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mistake_records WHERE chapterId = ? ORDER BY failureCount DESC").use { ps ->
                ps.setString(1, chapterId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMistake()) } }
            }
        }
    }

    override fun getMistakesForPathFlow(pathId: String): Flow<List<MistakeRecordEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mistake_records WHERE pathId = ? ORDER BY failureCount DESC, timestamp DESC").use { ps ->
                ps.setString(1, pathId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMistake()) } }
            }
        }
    }

    override suspend fun saveRemedialLesson(lesson: RemedialLessonEntity) {
        withContext(Dispatchers.IO) {
            withConnection { upsertRemedial(it, lesson) }
            notifyChanged()
        }
    }

    override fun getRemedialLessonsForChapterFlow(chapterId: String): Flow<List<RemedialLessonEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM remedial_lessons WHERE chapterId = ? ORDER BY createdAt DESC").use { ps ->
                ps.setString(1, chapterId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRemedial()) } }
            }
        }
    }

    override fun getPendingRemedialLessonsFlow(): Flow<List<RemedialLessonEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM remedial_lessons WHERE isCompleted = 0 ORDER BY createdAt DESC").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRemedial()) } }
            }
        }
    }

    override suspend fun markRemedialLessonCompleted(lessonId: String) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                conn.prepareStatement("UPDATE remedial_lessons SET isCompleted = 1 WHERE id = ?").use { ps ->
                    ps.setString(1, lessonId); ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    // ---- Documents ----

    override fun getAllDocumentsFlow(): Flow<List<DocumentEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM documents ORDER BY createdAt DESC").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDocument()) } }
            }
        }
    }

    override suspend fun getDocumentById(id: String): DocumentEntity? = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM documents WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toDocument() else null }
            }
        }
    }

    override suspend fun saveDocumentWithChunks(doc: DocumentEntity, chunks: List<DocumentChunkEntity>) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                upsertDocument(conn, doc)
                upsertDocumentChunks(conn, chunks)
            }
            notifyChanged()
        }
    }

    override suspend fun getChunksForDocument(documentId: String): List<DocumentChunkEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM document_chunks WHERE documentId = ? ORDER BY chunkIndex ASC").use { ps ->
                ps.setString(1, documentId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDocumentChunk()) } }
            }
        }
    }

    override suspend fun getChunksForDocuments(documentIds: List<String>): List<DocumentChunkEntity> {
        if (documentIds.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            withConnection { conn ->
                val placeholders = documentIds.joinToString(",") { "?" }
                conn.prepareStatement("SELECT * FROM document_chunks WHERE documentId IN ($placeholders) ORDER BY chunkIndex ASC").use { ps ->
                    documentIds.forEachIndexed { i, id -> ps.setString(i + 1, id) }
                    ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDocumentChunk()) } }
                }
            }
        }
    }

    override suspend fun searchDocumentChunks(query: String, limit: Int): List<DocumentChunkEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            val like = "%${query.trim()}%"
            conn.prepareStatement("SELECT * FROM document_chunks WHERE rawText LIKE ? OR sectionTitle LIKE ? OR keywords LIKE ? LIMIT ?").use { ps ->
                ps.setString(1, like); ps.setString(2, like); ps.setString(3, like); ps.setInt(4, limit)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDocumentChunk()) } }
            }
        }
    }

    override suspend fun deleteDocument(documentId: String) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                conn.prepareStatement("DELETE FROM document_chunks WHERE documentId = ?").use { ps -> ps.setString(1, documentId); ps.executeUpdate() }
                conn.prepareStatement("DELETE FROM documents WHERE id = ?").use { ps -> ps.setString(1, documentId); ps.executeUpdate() }
                upsertTombstone(conn, SyncTombstoneEntity(entityType = "DOCUMENT", entityId = documentId, deletedAt = System.currentTimeMillis()))
            }
            notifyChanged()
        }
    }

    // ---- Sync Tombstones ----

    override suspend fun getAllTombstones(): List<SyncTombstoneEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM sync_tombstones").use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(SyncTombstoneEntity(rs.getString("id"), rs.getString("entityType"), rs.getString("entityId"), rs.getLong("deletedAt")))
                        }
                    }
                }
            }
        }
    }

    override suspend fun recordTombstone(entityType: String, entityId: String) {
        withContext(Dispatchers.IO) {
            withConnection { upsertTombstone(it, SyncTombstoneEntity(entityType = entityType, entityId = entityId, deletedAt = System.currentTimeMillis())) }
            notifyChanged()
        }
    }

    // ---- Knowledge Graph ----

    override suspend fun getAllChapterLinkNodes(): List<ChapterLinkNode> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            val allChapters = conn.prepareStatement("SELECT * FROM chapters").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toChapter()) } }
            }
            val linksBySource = conn.prepareStatement("SELECT sourceChapterId, targetChapterId FROM chapter_links").use { ps ->
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString("sourceChapterId") to rs.getString("targetChapterId")) }
                }
            }.groupBy({ it.first }, { it.second })

            allChapters.map { ch ->
                ChapterLinkNode(ch.id, ch.title, ch.pathId, ch.status, linksBySource[ch.id].orEmpty())
            }
        }
    }

    // ---- Exam Prep & Mock Tests ----

    override fun getAllMockTestsFlow(): Flow<List<MockTestEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mock_tests ORDER BY createdAt DESC").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTest()) } }
            }
        }
    }

    override fun getMockTestsForPathFlow(pathId: String): Flow<List<MockTestEntity>> = reactiveFlow {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mock_tests WHERE pathId = ? ORDER BY createdAt DESC").use { ps ->
                ps.setString(1, pathId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTest()) } }
            }
        }
    }

    override suspend fun getMockTestById(id: String): MockTestEntity? = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mock_tests WHERE id = ? LIMIT 1").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toMockTest() else null }
            }
        }
    }

    override suspend fun getQuestionsForTest(testId: String): List<MockTestQuestionEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement("SELECT * FROM mock_test_questions WHERE testId = ? ORDER BY questionIndex ASC").use { ps ->
                ps.setString(1, testId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTestQuestion()) } }
            }
        }
    }

    override suspend fun getQuestionsForPath(pathId: String): List<MockTestQuestionEntity> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            conn.prepareStatement(
                "SELECT q.* FROM mock_test_questions q INNER JOIN mock_tests t ON q.testId = t.id WHERE t.pathId = ? ORDER BY t.createdAt DESC, q.questionIndex ASC"
            ).use { ps ->
                ps.setString(1, pathId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTestQuestion()) } }
            }
        }
    }

    private fun queryMockTestWithQuestions(conn: Connection, id: String): MockTestWithQuestions? {
        val test = conn.prepareStatement("SELECT * FROM mock_tests WHERE id = ? LIMIT 1").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toMockTest() else null }
        } ?: return null
        val questions = conn.prepareStatement("SELECT * FROM mock_test_questions WHERE testId = ? ORDER BY questionIndex ASC").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTestQuestion()) } }
        }
        return MockTestWithQuestions(test = test, questions = questions)
    }

    override suspend fun getMockTestWithQuestions(id: String): MockTestWithQuestions? = withContext(Dispatchers.IO) {
        withConnection { queryMockTestWithQuestions(it, id) }
    }

    override fun getMockTestWithQuestionsFlow(id: String): Flow<MockTestWithQuestions?> = reactiveFlow {
        withConnection { queryMockTestWithQuestions(it, id) }
    }

    override suspend fun saveMockTestWithQuestions(test: MockTestEntity, questions: List<MockTestQuestionEntity>) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                upsertMockTest(conn, test)
                upsertMockTestQuestions(conn, questions)
            }
            notifyChanged()
        }
    }

    override suspend fun updateMockTest(test: MockTestEntity) {
        withContext(Dispatchers.IO) {
            withConnection { upsertMockTest(it, test) }
            notifyChanged()
        }
    }

    override suspend fun answerMockTestQuestion(
        questionId: String,
        selectedOptionIndex: Int,
        userSelectedAnswers: List<String>
    ) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                conn.prepareStatement("UPDATE mock_test_questions SET userSelectedOption = ?, userSelectedAnswers = ? WHERE id = ?").use { ps ->
                    ps.setInt(1, selectedOptionIndex)
                    ps.setString(2, Json.encodeToString(userSelectedAnswers))
                    ps.setString(3, questionId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    override suspend fun resetMockTestQuestions(testId: String) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                conn.prepareStatement("UPDATE mock_test_questions SET userSelectedOption = -1, userSelectedAnswers = '[]', isFlagged = 0 WHERE testId = ?").use { ps ->
                    ps.setString(1, testId); ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    override suspend fun toggleQuestionFlag(questionId: String, isFlagged: Boolean) {
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                conn.prepareStatement("UPDATE mock_test_questions SET isFlagged = ? WHERE id = ?").use { ps ->
                    ps.setInt(1, if (isFlagged) 1 else 0); ps.setString(2, questionId); ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    override suspend fun deleteMockTest(id: String) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                conn.prepareStatement("DELETE FROM mock_tests WHERE id = ?").use { ps -> ps.setString(1, id); ps.executeUpdate() }
                upsertTombstone(conn, SyncTombstoneEntity(entityType = "MOCK_TEST", entityId = id, deletedAt = System.currentTimeMillis()))
            }
            notifyChanged()
        }
    }

    // ---- Backup / Restore ----

    override suspend fun exportDatabase(): BackupBundle = withContext(Dispatchers.IO) {
        withConnection { conn ->
            BackupBundle(
                version = 3,
                exportedAt = System.currentTimeMillis(),
                appVersion = "1.7.1",
                studyPaths = conn.prepareStatement("SELECT * FROM study_paths").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toStudyPath()) } } },
                modules = conn.prepareStatement("SELECT * FROM modules").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toModule()) } } },
                chapters = conn.prepareStatement("SELECT * FROM chapters").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toChapter()) } } },
                revisions = conn.prepareStatement("SELECT * FROM chapter_revisions").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRevision()) } } },
                links = conn.prepareStatement("SELECT * FROM chapter_links").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(ChapterLinkCrossRef(rs.getString("sourceChapterId"), rs.getString("targetChapterId"), rs.getString("anchorText"))) } } },
                qas = conn.prepareStatement("SELECT * FROM chapter_qas").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toQa()) } } },
                flashcards = conn.prepareStatement("SELECT * FROM flashcards").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toFlashcard()) } } },
                masteryQuizzes = conn.prepareStatement("SELECT * FROM mastery_quizzes").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMasteryQuiz()) } } },
                learningObjectives = conn.prepareStatement("SELECT * FROM learning_objectives").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toObjective()) } } },
                mistakes = conn.prepareStatement("SELECT * FROM mistake_records").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMistake()) } } },
                remedials = conn.prepareStatement("SELECT * FROM remedial_lessons").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRemedial()) } } },
                documents = conn.prepareStatement("SELECT * FROM documents").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDocument()) } } },
                documentChunks = conn.prepareStatement("SELECT * FROM document_chunks").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDocumentChunk()) } } },
                tombstones = conn.prepareStatement("SELECT * FROM sync_tombstones").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(SyncTombstoneEntity(rs.getString("id"), rs.getString("entityType"), rs.getString("entityId"), rs.getLong("deletedAt"))) } } },
                mockTests = conn.prepareStatement("SELECT * FROM mock_tests").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTest()) } } },
                mockTestQuestions = conn.prepareStatement("SELECT * FROM mock_test_questions").use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMockTestQuestion()) } } }
            )
        }
    }

    override suspend fun importDatabase(bundle: BackupBundle, overwrite: Boolean) {
        withContext(Dispatchers.IO) {
            withConnection(transactional = true) { conn ->
                if (overwrite) {
                    listOf(
                        "mock_test_questions", "mock_tests", "mastery_quizzes", "flashcards",
                        "chapter_qas", "chapter_links", "chapter_revisions", "learning_objectives",
                        "mistake_records", "remedial_lessons", "chapters", "modules", "study_paths",
                        "document_chunks", "documents"
                    ).forEach { table -> conn.createStatement().use { it.executeUpdate("DELETE FROM $table") } }

                    bundle.studyPaths.forEach { upsertStudyPath(conn, it) }
                    upsertModules(conn, bundle.modules)
                    upsertChapters(conn, bundle.chapters)
                    upsertRevisions(conn, bundle.revisions)
                    upsertLinks(conn, bundle.links)
                    upsertQas(conn, bundle.qas)
                    upsertFlashcards(conn, bundle.flashcards)
                    upsertMasteryQuizzes(conn, bundle.masteryQuizzes)
                    upsertObjectives(conn, bundle.learningObjectives)
                    bundle.mistakes.forEach { upsertMistake(conn, it) }
                    bundle.remedials.forEach { upsertRemedial(conn, it) }
                    bundle.documents.forEach { upsertDocument(conn, it) }
                    upsertDocumentChunks(conn, bundle.documentChunks)
                    bundle.tombstones.forEach { upsertTombstone(conn, it) }
                    bundle.mockTests.forEach { upsertMockTest(conn, it) }
                    upsertMockTestQuestions(conn, bundle.mockTestQuestions)
                } else {
                    bundle.tombstones.forEach { upsertTombstone(conn, it) }
                    val tombstones = conn.prepareStatement("SELECT * FROM sync_tombstones").use { ps ->
                        ps.executeQuery().use { rs ->
                            buildMap {
                                while (rs.next()) {
                                    val t = SyncTombstoneEntity(rs.getString("id"), rs.getString("entityType"), rs.getString("entityId"), rs.getLong("deletedAt"))
                                    put("${t.entityType}:${t.entityId}", t)
                                }
                            }
                        }
                    }

                    bundle.studyPaths.filter { p ->
                        val t = tombstones["STUDY_PATH:${p.id}"]
                        t == null || p.updatedAt > t.deletedAt
                    }.forEach { upsertStudyPath(conn, it) }

                    upsertModules(conn, bundle.modules)

                    for (remoteChapter in bundle.chapters) {
                        val t = tombstones["CHAPTER:${remoteChapter.id}"]
                        if (t != null && remoteChapter.updatedAt <= t.deletedAt) continue

                        val local = conn.prepareStatement("SELECT * FROM chapters WHERE id = ?").use { ps ->
                            ps.setString(1, remoteChapter.id)
                            ps.executeQuery().use { rs -> if (rs.next()) rs.toChapter() else null }
                        }
                        if (local == null) {
                            upsertChapters(conn, listOf(remoteChapter))
                        } else if (local.markdownContent != remoteChapter.markdownContent) {
                            if (remoteChapter.updatedAt > local.updatedAt) {
                                upsertRevisions(
                                    conn,
                                    listOf(ChapterRevisionEntity(UUID.randomUUID().toString(), local.id, local.markdownContent, "Pre-sync merge local backup snapshot", System.currentTimeMillis()))
                                )
                                upsertChapters(conn, listOf(remoteChapter))
                            } else {
                                upsertRevisions(
                                    conn,
                                    listOf(ChapterRevisionEntity(UUID.randomUUID().toString(), local.id, remoteChapter.markdownContent, "Remote sync conflict revision backup", remoteChapter.updatedAt))
                                )
                            }
                        } else {
                            upsertChapters(conn, listOf(remoteChapter))
                        }
                    }

                    upsertRevisions(conn, bundle.revisions)
                    upsertLinks(conn, bundle.links)
                    upsertQas(conn, bundle.qas)

                    for (remoteCard in bundle.flashcards) {
                        val localCard = conn.prepareStatement("SELECT * FROM flashcards WHERE id = ?").use { ps ->
                            ps.setString(1, remoteCard.id)
                            ps.executeQuery().use { rs -> if (rs.next()) rs.toFlashcard() else null }
                        }
                        if (localCard == null || remoteCard.repetitions > localCard.repetitions || remoteCard.lastReviewTimestamp > localCard.lastReviewTimestamp) {
                            upsertFlashcards(conn, listOf(remoteCard))
                        }
                    }

                    upsertMasteryQuizzes(conn, bundle.masteryQuizzes)
                    upsertObjectives(conn, bundle.learningObjectives)
                    bundle.mistakes.forEach { upsertMistake(conn, it) }
                    bundle.remedials.forEach { upsertRemedial(conn, it) }
                    bundle.documents.filter { d ->
                        val t = tombstones["DOCUMENT:${d.id}"]
                        t == null || d.createdAt > t.deletedAt
                    }.forEach { upsertDocument(conn, it) }
                    upsertDocumentChunks(conn, bundle.documentChunks)
                    bundle.mockTests.forEach { upsertMockTest(conn, it) }
                    upsertMockTestQuestions(conn, bundle.mockTestQuestions)
                }
            }
            notifyChanged()
        }
    }
}
