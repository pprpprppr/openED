package org.opened.app.core.sync

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.drive.BackupBundle
import org.opened.app.core.drive.DriveBackupManager
import org.opened.app.core.drive.GoogleAuthManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveSyncProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveBackupManager: DriveBackupManager,
    private val authManager: GoogleAuthManager,
    private val preferencesRepository: UserPreferencesRepository
) : SyncProvider {

    override val providerId: String = "GOOGLE_DRIVE"
    override val displayName: String = "Google Drive (App Data)"

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun isConfigured(): Boolean {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return authManager.isAuthorized() || !prefs.linkedBackupUri.isNullOrBlank()
    }

    override suspend fun uploadBundle(bundle: BackupBundle): Result<String> = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val linkedUri = prefs.linkedBackupUri
        if (!linkedUri.isNullOrBlank()) {
            driveBackupManager.backupToUri(Uri.parse(linkedUri)).map { linkedUri }
        } else {
            driveBackupManager.uploadBackupToDrive()
        }
    }

    override suspend fun downloadLatestBundle(): Result<Pair<String, BackupBundle>?> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val linkedUri = prefs.linkedBackupUri
            val bytes = if (!linkedUri.isNullOrBlank()) {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(linkedUri))
                inputStream?.use { it.readBytes() }
            } else {
                driveBackupManager.downloadBackupZipBytes().getOrThrow()
            }
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
            val decodedBundle = json.decodeFromString(BackupBundle.serializer(), bundleJson)

            (linkedUri ?: "gdrive://opened_backup_v1.zip") to decodedBundle
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
