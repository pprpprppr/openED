package org.opened.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.opened.app.core.drive.BackupBundle
import org.opened.app.core.platform.UserPreferencesStore
import org.opened.app.core.sync.SyncProvider
import org.opened.app.core.sync.SyncRevisionMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DesktopFileSystemSyncProvider(
    private val preferencesStore: UserPreferencesStore
) : SyncProvider {

    override val providerId: String = "LOCAL"
    override val displayName: String = "Local Archive (PC)"

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val defaultBackupFile: File
        get() = File(DesktopPaths.backupsDir, "opened_backup_v1.zip")

    override suspend fun isConfigured(): Boolean = true

    override suspend fun uploadBundle(bundle: BackupBundle): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = defaultBackupFile
            val manifestJson = json.encodeToString(BackupBundle.serializer(), bundle)
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("opened_manifest.json"))
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            targetFile.writeBytes(bos.toByteArray())
            preferencesStore.setLinkedBackupUri(targetFile.absolutePath)
            targetFile.absolutePath
        }
    }

    override suspend fun downloadLatestBundle(): Result<Pair<String, BackupBundle>?> = withContext(Dispatchers.IO) {
        runCatching {
            val file = defaultBackupFile
            if (!file.exists()) return@runCatching null
            val bytes = file.readBytes()
            val bundle = parseZipBytes(bytes)
            if (bundle != null) file.absolutePath to bundle else null
        }
    }

    override suspend fun listRevisions(): Result<List<SyncRevisionMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = defaultBackupFile
            if (!file.exists()) emptyList()
            else listOf(
                SyncRevisionMetadata(
                    revisionId = "local-pc",
                    providerName = displayName,
                    timestamp = file.lastModified(),
                    sizeBytes = file.length(),
                    description = file.absolutePath
                )
            )
        }
    }

    private fun parseZipBytes(zipBytes: ByteArray): BackupBundle? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == "opened_manifest.json") {
                    val content = zipIn.readBytes().toString(Charsets.UTF_8)
                    return json.decodeFromString(BackupBundle.serializer(), content)
                }
                entry = zipIn.nextEntry
            }
        }
        return null
    }
}
