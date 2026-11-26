package org.opened.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.opened.app.core.drive.BackupBundle
import org.opened.app.core.drive.SyncConflictResolution
import org.opened.app.core.platform.PlatformBackupGateway
import org.opened.app.core.platform.UserPreferencesStore
import org.opened.app.domain.repository.OpenEDRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DesktopBackupGateway(
    private val repository: OpenEDRepository,
    private val preferencesStore: UserPreferencesStore
) : PlatformBackupGateway {

    override val supportsGoogleDrive: Boolean = false
    override val supportsScheduledBackups: Boolean = false

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override fun getLastSignedInEmail(): String? = null
    override fun isAuthorizedWithDrive(): Boolean = false
    override suspend fun signOutGoogle() {}
    override fun schedulePeriodicBackup() {}
    override fun cancelPeriodicBackup() {}

    override suspend fun uploadBackupToDrive(): Result<String> =
        Result.failure(UnsupportedOperationException("Google Drive OAuth is Android-only. Use Local Archive Backup (.zip) or WebDAV Sync on PC."))

    override suspend fun restoreBackupFromDrive(resolution: SyncConflictResolution): Result<Unit> =
        Result.failure(UnsupportedOperationException("Google Drive OAuth is Android-only. Use Restore from Local .zip or WebDAV Sync on PC."))

    override suspend fun backupToLinkedUri(uriString: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(uriString)
            file.parentFile?.mkdirs()
            file.writeBytes(createBackupZipBytes())
            preferencesStore.setLastBackupTimestamp(System.currentTimeMillis())
        }
    }

    override suspend fun createBackupZipBytes(): ByteArray = withContext(Dispatchers.IO) {
        val bundle = repository.exportDatabase()
        val manifestJson = json.encodeToString(BackupBundle.serializer(), bundle)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("opened_manifest.json"))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        bos.toByteArray()
    }

    override suspend fun restoreFromZipBytes(zipBytes: ByteArray, resolution: SyncConflictResolution) {
        withContext(Dispatchers.IO) {
            var bundle: BackupBundle? = null
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "opened_manifest.json") {
                        val content = zipIn.readBytes().toString(Charsets.UTF_8)
                        bundle = json.decodeFromString(BackupBundle.serializer(), content)
                        break
                    }
                    entry = zipIn.nextEntry
                }
            }
            val parsed = bundle ?: throw IllegalArgumentException("Invalid backup archive: missing opened_manifest.json")
            repository.importDatabase(parsed, overwrite = resolution == SyncConflictResolution.OVERWRITE_LOCAL)
        }
    }

    override suspend fun exportObsidianVault(): File = withContext(Dispatchers.IO) {
        val outputFile = File(DesktopPaths.exportsDir, "openED_obsidian_vault.zip")
        val allPaths = repository.getAllStudyPathsFlow().first()

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            for (pathWithModules in allPaths) {
                val courseFolder = sanitizeFileName(pathWithModules.studyPath.title)
                for (moduleWithChapters in pathWithModules.modules) {
                    val moduleFolder = sanitizeFileName(moduleWithChapters.module.title)
                    for (chapter in moduleWithChapters.chapters) {
                        val chapterFileName = sanitizeFileName(chapter.title) + ".md"
                        val entryPath = "$courseFolder/$moduleFolder/$chapterFileName"
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(chapter.createdAt))
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

    override suspend fun exportAnkiCsv(): File = withContext(Dispatchers.IO) {
        val outputFile = File(DesktopPaths.exportsDir, "openED_anki_flashcards.tsv")
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

    override fun getAppDataDirectoryPath(): String = DesktopPaths.dataDir.absolutePath

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "-").replace(Regex("""\s+"""), " ").trim().take(80)

    private fun sanitizeTag(name: String): String =
        name.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
}
