package org.opened.app.domain.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.opened.app.core.database.OpenEDDatabase
import org.opened.app.core.database.dao.ChapterDao
import org.opened.app.core.database.dao.ChapterLinkDao
import org.opened.app.core.database.dao.ChapterQaDao
import org.opened.app.core.database.dao.ChapterRevisionDao
import org.opened.app.core.database.dao.FlashcardDao
import org.opened.app.core.database.dao.MasteryQuizDao
import org.opened.app.core.database.dao.ModuleDao
import org.opened.app.core.database.dao.StudyPathDao
import org.opened.app.core.database.entity.ChapterEntity
import org.opened.app.core.database.entity.ChapterLinkCrossRef
import org.opened.app.core.database.entity.ChapterQaEntity
import org.opened.app.core.database.entity.ChapterRevisionEntity
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.core.database.entity.MasteryQuizEntity
import org.opened.app.core.database.entity.ModuleEntity
import org.opened.app.core.database.entity.StudyPathEntity
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.core.database.model.ChapterStatus
import org.opened.app.core.database.model.ChapterWithDetails
import org.opened.app.core.database.model.SearchResultItem
import org.opened.app.core.database.model.SearchResultType
import org.opened.app.core.database.model.StudyPathWithModules
import org.opened.app.core.drive.BackupBundle
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenEDRepositoryImpl @Inject constructor(
    private val database: OpenEDDatabase,
    private val studyPathDao: StudyPathDao,
    private val moduleDao: ModuleDao,
    private val chapterDao: ChapterDao,
    private val revisionDao: ChapterRevisionDao,
    private val linkDao: ChapterLinkDao,
    private val qaDao: ChapterQaDao,
    private val flashcardDao: FlashcardDao,
    private val masteryQuizDao: MasteryQuizDao
) : OpenEDRepository {

    override fun getAllStudyPathsFlow(): Flow<List<StudyPathWithModules>> {
        return studyPathDao.getAllPathsWithModulesFlow()
    }

    override fun getStudyPathWithModulesFlow(pathId: String): Flow<StudyPathWithModules?> {
        return studyPathDao.getPathWithModulesFlow(pathId)
    }

    override suspend fun getStudyPathWithModules(pathId: String): StudyPathWithModules? {
        return withContext(Dispatchers.IO) {
            studyPathDao.getPathWithModules(pathId)
        }
    }

    override suspend fun saveStudyPathWithModules(
        path: StudyPathEntity,
        modulesWithChapters: List<Pair<ModuleEntity, List<ChapterEntity>>>
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                studyPathDao.insert(path)
                for ((module, chapters) in modulesWithChapters) {
                    moduleDao.insert(module)
                    chapterDao.insertAll(chapters)
                }
            }
        }
    }

    override suspend fun appendModulesWithChapters(
        pathId: String,
        modulesWithChapters: List<Pair<ModuleEntity, List<ChapterEntity>>>
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                for ((module, chapters) in modulesWithChapters) {
                    moduleDao.insert(module)
                    chapterDao.insertAll(chapters)
                }
            }
            recalculatePathProgress(pathId)
        }
    }

    override suspend fun deleteStudyPath(pathId: String) {
        withContext(Dispatchers.IO) {
            studyPathDao.deleteById(pathId)
        }
    }

    override fun getChapterWithDetailsFlow(chapterId: String): Flow<ChapterWithDetails?> {
        return chapterDao.getChapterWithDetailsFlow(chapterId)
    }

    override suspend fun getChapterWithDetails(chapterId: String): ChapterWithDetails? {
        return withContext(Dispatchers.IO) {
            chapterDao.getChapterWithDetails(chapterId)
        }
    }

    override suspend fun getChapter(chapterId: String): ChapterEntity? {
        return withContext(Dispatchers.IO) {
            chapterDao.getById(chapterId)
        }
    }

    override suspend fun updateChapterContent(chapterId: String, content: String, status: ChapterStatus) {
        withContext(Dispatchers.IO) {
            chapterDao.updateMarkdown(chapterId, content, status)
            syncChapterLinks(chapterId, content)
            val chapter = chapterDao.getById(chapterId)
            if (chapter != null) {
                recalculatePathProgress(chapter.pathId)
            }
        }
    }

    override suspend fun updateChapterStatus(chapterId: String, status: ChapterStatus) {
        withContext(Dispatchers.IO) {
            chapterDao.updateStatus(chapterId, status)
            val chapter = chapterDao.getById(chapterId)
            if (chapter != null) {
                recalculatePathProgress(chapter.pathId)
            }
        }
    }

    override suspend fun addChapterRevision(
        chapterId: String,
        previousMarkdown: String,
        changeReason: String
    ): ChapterRevisionEntity {
        return withContext(Dispatchers.IO) {
            val revision = ChapterRevisionEntity(
                id = UUID.randomUUID().toString(),
                chapterId = chapterId,
                previousMarkdown = previousMarkdown,
                changeReason = changeReason,
                timestamp = System.currentTimeMillis()
            )
            revisionDao.insert(revision)
            revision
        }
    }

    override suspend fun addChapterQa(
        chapterId: String,
        userQuestion: String,
        aiAnswer: String
    ): ChapterQaEntity {
        return withContext(Dispatchers.IO) {
            val qa = ChapterQaEntity(
                id = UUID.randomUUID().toString(),
                chapterId = chapterId,
                userQuestion = userQuestion,
                aiAnswer = aiAnswer,
                isMergedIntoContent = false,
                timestamp = System.currentTimeMillis()
            )
            qaDao.insert(qa)
            qa
        }
    }

    override suspend fun markQaAsMerged(qaId: String, isMerged: Boolean) {
        withContext(Dispatchers.IO) {
            qaDao.markAsMerged(qaId, isMerged)
        }
    }

    override suspend fun syncChapterLinks(chapterId: String, content: String) {
        withContext(Dispatchers.IO) {
            val regex = Regex("""\[\[(.*?)\]\]""")
            val matches = regex.findAll(content)
            val crossRefs = mutableListOf<ChapterLinkCrossRef>()

            for (match in matches) {
                val title = match.groupValues[1].trim()
                if (title.isNotEmpty()) {
                    val targetChapter = chapterDao.findByExactTitle(title)
                    if (targetChapter != null && targetChapter.id != chapterId) {
                        crossRefs.add(
                            ChapterLinkCrossRef(
                                sourceChapterId = chapterId,
                                targetChapterId = targetChapter.id,
                                anchorText = title
                            )
                        )
                    }
                }
            }

            database.withTransaction {
                linkDao.deleteOutgoingLinks(chapterId)
                if (crossRefs.isNotEmpty()) {
                    linkDao.insertLinks(crossRefs.distinctBy { it.targetChapterId })
                }
            }
        }
    }

    override suspend fun findChapterByTitle(title: String): ChapterEntity? {
        return withContext(Dispatchers.IO) {
            chapterDao.findByExactTitle(title.trim())
        }
    }

    override suspend fun createTangentChapter(sourceChapterId: String, tangentTitle: String): ChapterEntity {
        return withContext(Dispatchers.IO) {
            val source = requireNotNull(chapterDao.getById(sourceChapterId)) { "Source chapter not found" }
            val existingInModule = chapterDao.getChaptersForPath(source.pathId).filter { it.moduleId == source.moduleId }
            val nextOrder = (existingInModule.maxOfOrNull { it.orderIndex } ?: source.orderIndex) + 1

            val tangentChapter = ChapterEntity(
                id = UUID.randomUUID().toString(),
                moduleId = source.moduleId,
                pathId = source.pathId,
                title = tangentTitle.trim(),
                orderIndex = nextOrder,
                markdownContent = "",
                summary = "Tangent deep-dive exploring $tangentTitle branching from '${source.title}'",
                status = ChapterStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            database.withTransaction {
                chapterDao.insert(tangentChapter)
                linkDao.insertLink(
                    ChapterLinkCrossRef(
                        sourceChapterId = sourceChapterId,
                        targetChapterId = tangentChapter.id,
                        anchorText = tangentTitle
                    )
                )
                linkDao.insertLink(
                    ChapterLinkCrossRef(
                        sourceChapterId = tangentChapter.id,
                        targetChapterId = sourceChapterId,
                        anchorText = source.title
                    )
                )
            }

            recalculatePathProgress(source.pathId)
            tangentChapter
        }
    }

    override suspend fun recalculatePathProgress(pathId: String) {
        withContext(Dispatchers.IO) {
            val chapters = chapterDao.getChaptersForPath(pathId)
            if (chapters.isNotEmpty()) {
                val completedCount = chapters.count { it.status == ChapterStatus.COMPLETED }
                val progress = completedCount.toFloat() / chapters.size.toFloat()
                studyPathDao.updateProgress(pathId, progress)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResultItem> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<SearchResultItem>()
            val paths = studyPathDao.getAllForExport().associateBy { it.id }

            val chapters = chapterDao.searchChapters(query)
            for (ch in chapters) {
                val pathTitle = paths[ch.pathId]?.title ?: "Unknown Course"
                val snippet = if (ch.markdownContent.isNotBlank()) {
                    extractSnippet(ch.markdownContent, query)
                } else {
                    ch.summary.ifBlank { "Unwritten chapter" }
                }
                results.add(
                    SearchResultItem(
                        id = ch.id, pathId = ch.pathId, chapterId = ch.id,
                        chapterTitle = ch.title, pathTitle = pathTitle, snippet = snippet,
                        type = SearchResultType.CHAPTER, timestamp = ch.updatedAt
                    )
                )
            }

            val revisions = revisionDao.searchRevisions(query)
            for (rev in revisions) {
                val ch = chapterDao.getById(rev.chapterId)
                if (ch != null) {
                    val pathTitle = paths[ch.pathId]?.title ?: "Unknown Course"
                    results.add(
                        SearchResultItem(
                            id = rev.id, pathId = ch.pathId, chapterId = ch.id,
                            chapterTitle = ch.title, pathTitle = pathTitle,
                            snippet = "Edit Note: ${rev.changeReason} | ${extractSnippet(rev.previousMarkdown, query)}",
                            type = SearchResultType.REVISION_NOTE, timestamp = rev.timestamp
                        )
                    )
                }
            }

            val qas = qaDao.searchQas(query)
            for (qa in qas) {
                val ch = chapterDao.getById(qa.chapterId)
                if (ch != null) {
                    val pathTitle = paths[ch.pathId]?.title ?: "Unknown Course"
                    results.add(
                        SearchResultItem(
                            id = qa.id, pathId = ch.pathId, chapterId = ch.id,
                            chapterTitle = ch.title, pathTitle = pathTitle,
                            snippet = "Q: ${qa.userQuestion}\nA: ${extractSnippet(qa.aiAnswer, query)}",
                            type = SearchResultType.QA_THREAD, timestamp = qa.timestamp
                        )
                    )
                }
            }

            results.sortedByDescending { it.timestamp }
        }
    }

    private fun extractSnippet(text: String, query: String, maxLength: Int = 160): String {
        val index = text.indexOf(query, ignoreCase = true)
        if (index == -1) {
            return text.take(maxLength).replace("\n", " ") + (if (text.length > maxLength) "..." else "")
        }
        val start = (index - 40).coerceAtLeast(0)
        val end = (index + query.length + 80).coerceAtMost(text.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return prefix + text.substring(start, end).replace("\n", " ") + suffix
    }

    // ---- Flashcards ----

    override fun getFlashcardsForChapterFlow(chapterId: String): Flow<List<FlashcardEntity>> =
        flashcardDao.getFlashcardsForChapter(chapterId)

    override fun getDueFlashcardsFlow(nowMillis: Long): Flow<List<FlashcardEntity>> =
        flashcardDao.getDueFlashcards(nowMillis)

    override fun getDueFlashcardCount(): Flow<Int> =
        flashcardDao.getDueCount(System.currentTimeMillis())

    override suspend fun insertFlashcards(cards: List<FlashcardEntity>) {
        withContext(Dispatchers.IO) { flashcardDao.insertAll(cards) }
    }

    override suspend fun updateFlashcard(card: FlashcardEntity) {
        withContext(Dispatchers.IO) { flashcardDao.update(card) }
    }

    override suspend fun deleteFlashcardsForChapter(chapterId: String) {
        withContext(Dispatchers.IO) { flashcardDao.deleteForChapter(chapterId) }
    }

    override suspend fun getAllFlashcards(): List<FlashcardEntity> =
        withContext(Dispatchers.IO) { flashcardDao.getAllFlashcards() }

    // ---- Mastery / Feynman ----

    override fun getMasteryQuizzesFlow(chapterId: String): Flow<List<MasteryQuizEntity>> =
        masteryQuizDao.getQuizzesForChapter(chapterId)

    override suspend fun saveMasteryQuiz(quiz: MasteryQuizEntity) {
        withContext(Dispatchers.IO) { masteryQuizDao.insert(quiz) }
    }

    override suspend fun getAllQuizzes(): List<MasteryQuizEntity> =
        withContext(Dispatchers.IO) { masteryQuizDao.getAllQuizzes() }

    // ---- Knowledge Graph ----

    override suspend fun getAllChapterLinkNodes(): List<ChapterLinkNode> {
        return withContext(Dispatchers.IO) {
            val allChapters = chapterDao.getAllForExport()
            val allLinks = linkDao.getAllForExport()
            val linksBySource = allLinks.groupBy { it.sourceChapterId }

            allChapters.map { ch ->
                ChapterLinkNode(
                    chapterId = ch.id,
                    chapterTitle = ch.title,
                    pathId = ch.pathId,
                    status = ch.status,
                    outgoingTargetIds = linksBySource[ch.id]?.map { it.targetChapterId } ?: emptyList()
                )
            }
        }
    }

    // ---- Backup/Restore ----

    override suspend fun exportDatabase(): BackupBundle {
        return withContext(Dispatchers.IO) {
            BackupBundle(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                studyPaths = studyPathDao.getAllForExport(),
                modules = moduleDao.getAllForExport(),
                chapters = chapterDao.getAllForExport(),
                revisions = revisionDao.getAllForExport(),
                links = linkDao.getAllForExport(),
                qas = qaDao.getAllForExport(),
                flashcards = flashcardDao.getAllFlashcards(),
                masteryQuizzes = masteryQuizDao.getAllQuizzes()
            )
        }
    }

    override suspend fun importDatabase(bundle: BackupBundle, overwrite: Boolean) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                if (overwrite) {
                    masteryQuizDao.clearAll()
                    flashcardDao.clearAll()
                    qaDao.clearAll()
                    linkDao.clearAll()
                    revisionDao.clearAll()
                    chapterDao.clearAll()
                    moduleDao.clearAll()
                    studyPathDao.clearAll()
                }
                studyPathDao.insertAll(bundle.studyPaths)
                moduleDao.insertAll(bundle.modules)
                chapterDao.insertAll(bundle.chapters)
                revisionDao.insertAll(bundle.revisions)
                linkDao.insertLinks(bundle.links)
                qaDao.insertAll(bundle.qas)
                if (bundle.flashcards.isNotEmpty()) {
                    flashcardDao.insertAll(bundle.flashcards)
                }
                if (bundle.masteryQuizzes.isNotEmpty()) {
                    masteryQuizDao.insertAll(bundle.masteryQuizzes)
                }
            }
        }
    }
}
