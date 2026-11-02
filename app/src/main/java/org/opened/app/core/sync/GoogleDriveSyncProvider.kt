package org.opened.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opened.app.core.drive.BackupBundle
import org.opened.app.core.drive.DriveBackupManager
import org.opened.app.core.drive.GoogleAuthManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveSyncProvider @Inject constructor(
    private val driveBackupManager: DriveBackupManager,
    private val authManager: GoogleAuthManager
) : SyncProvider {

    override val providerId: String = "GOOGLE_DRIVE"
    override val displayName: String = "Google Drive (App Data)"

    override suspend fun isConfigured(): Boolean {
        return authManager.isAuthorized()
    }

    override suspend fun uploadBundle(bundle: BackupBundle): Result<String> = withContext(Dispatchers.IO) {
        driveBackupManager.uploadBackupToDrive()
    }

    override suspend fun downloadLatestBundle(): Result<Pair<String, BackupBundle>?> = withContext(Dispatchers.IO) {
        runCatching {
            val bytesResult = driveBackupManager.downloadBackupZipBytes()
            val bytes = bytesResult.getOrThrow()
            if (bytes == null || bytes.isEmpty()) return@runCatching null

            val jsonEntryName = "opened_manifest.json"
            val zipIn = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes))
            var bundleJson: String? = null
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == jsonEntryName || entry.name.endsWith(".json")) {
                    bundleJson = zipIn.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                entry = zipIn.nextEntry
            }

            if (bundleJson == null) return@runCatching null
            val decodedBundle = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString(BackupBundle.serializer(), bundleJson)

            "gdrive://opened_backup_v1.zip" to decodedBundle
        }
    }

    override suspend fun listRevisions(): Result<List<SyncRevisionMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured()) return@runCatching emptyList()
            listOf(
                SyncRevisionMetadata(
                    revisionId = "gdrive_latest",
                    providerName = displayName,
                    timestamp = System.currentTimeMillis(),
                    sizeBytes = 0L,
                    description = "Google Drive Cloud Backup"
                )
            )
        }
    }
}
