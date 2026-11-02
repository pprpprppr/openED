package org.opened.app.core.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.opened.app.domain.repository.OpenEDRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OpenEDRepository
) {

    private fun getExportDirectory(): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    /**
     * Exports all courses as an Obsidian-compatible Markdown vault ZIP.
     * Structure: CourseTitle/ModuleTitle/ChapterTitle.md
     * Each .md file has YAML frontmatter + [[WikiLinks]] preserved.
     */
    suspend fun exportObsidianVault(): File = withContext(Dispatchers.IO) {
        val outputFile = File(getExportDirectory(), "openED_obsidian_vault.zip")
        val allPaths = repository.getAllStudyPathsFlow().first()

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            for (pathWithModules in allPaths) {
                val courseFolder = sanitizeFileName(pathWithModules.studyPath.title)

                for (moduleWithChapters in pathWithModules.modules) {
                    val moduleFolder = sanitizeFileName(moduleWithChapters.module.title)

                    for (chapter in moduleWithChapters.chapters) {
                        val chapterFileName = sanitizeFileName(chapter.title) + ".md"
                        val entryPath = "$courseFolder/$moduleFolder/$chapterFileName"

                        val dateStr = java.text.SimpleDateFormat(
                            "yyyy-MM-dd",
                            java.util.Locale.US
                        ).format(java.util.Date(chapter.createdAt))

                        val frontmatter = buildString {
                            appendLine("---")
                            appendLine("title: \"${chapter.title.replace("\"", "\\\"")}\"")
                            appendLine("course: \"${pathWithModules.studyPath.title.replace("\"", "\\\"")}\"")
                            appendLine("module: \"${moduleWithChapters.module.title.replace("\"", "\\\"")}\"")
                            appendLine("status: ${chapter.status.name.lowercase()}")
                            appendLine("created: $dateStr")
                            appendLine("tags:")
                            appendLine("  - openED")
                            appendLine("  - ${sanitizeTag(pathWithModules.studyPath.topic)}")
                            appendLine("---")
                            appendLine()
                        }

                        val content = if (chapter.markdownContent.isNotBlank()) {
                            frontmatter + chapter.markdownContent
                        } else {
                            frontmatter + "_Chapter content not yet generated._\n"
                        }

                        zip.putNextEntry(ZipEntry(entryPath))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
        }

        outputFile
    }

    /**
     * Exports all flashcards as an Anki-compatible tab-separated file (TSV).
     * Format per line: Front\tBack\tTags
     */
    suspend fun exportAnkiCsv(): File = withContext(Dispatchers.IO) {
        val outputFile = File(getExportDirectory(), "openED_anki_flashcards.tsv")
        val allCards = repository.getAllFlashcards()

        outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("#separator:tab")
            writer.appendLine("#html:false")
            writer.appendLine("#notetype:Basic")
            writer.appendLine("#deck:openED")
            for (card in allCards) {
                val front = card.front.replace("\t", " ").replace("\n", "<br>")
                val back = card.back.replace("\t", " ").replace("\n", "<br>")
                writer.appendLine("$front\t$back\topenED")
            }
        }

        outputFile
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "-")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(80)

    private fun sanitizeTag(name: String): String =
        name.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
}
