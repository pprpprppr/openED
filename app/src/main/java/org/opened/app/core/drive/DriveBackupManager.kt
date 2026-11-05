package org.opened.app.core.drive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.domain.repository.OpenEDRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OpenEDRepository,
    private val authManager: GoogleAuthManager,
    private val preferencesRepository: UserPreferencesRepository
) {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val backupFileName = "opened_backup_v1.zip"
    private val legacyBackupFileName = "cortexa_backup_v1.zip"
    private val jsonEntryName = "opened_manifest.json"

    private fun getDriveService(): Drive? {
        val account = authManager.getLastSignedInAccount() ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("openED Knowledge Studio").build()
    }

    suspend fun createBackupZipBytes(): ByteArray = withContext(Dispatchers.IO) {
        val bundle = repository.exportDatabase()
        val bundleJson = json.encodeToString(BackupBundle.serializer(), bundle)

        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(byteArrayOutputStream).use { zipOut ->
            val entry = ZipEntry(jsonEntryName)
            zipOut.putNextEntry(entry)
            zipOut.write(bundleJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }
        byteArrayOutputStream.toByteArray()
    }

    suspend fun restoreFromZipBytes(zipBytes: ByteArray, resolution: SyncConflictResolution) = withContext(Dispatchers.IO) {
        val zipIn = ZipInputStream(ByteArrayInputStream(zipBytes))
        var bundleJson: String? = null

        var entry = zipIn.nextEntry
        while (entry != null) {
            if (entry.name == jsonEntryName || entry.name.endsWith(".json")) {
                bundleJson = zipIn.bufferedReader(Charsets.UTF_8).readText()
                break
            }
            entry = zipIn.nextEntry
        }

        if (bundleJson == null) {
            throw IllegalArgumentException("Invalid backup archive: missing $jsonEntryName")
        }

        val bundle = json.decodeFromString(BackupBundle.serializer(), bundleJson)
        val overwrite = resolution == SyncConflictResolution.OVERWRITE_LOCAL
        repository.importDatabase(bundle, overwrite)
        preferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
    }

    suspend fun backupToUri(uri: android.net.Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zipBytes = createBackupZipBytes()
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw java.io.IOException("Cannot open output stream for URI: $uri")
            outputStream.use { out ->
                out.write(zipBytes)
                out.flush()
            }
            preferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
        }
    }

    suspend fun restoreFromUri(uri: android.net.Uri, resolution: SyncConflictResolution): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("Cannot open input stream for URI: $uri")
            val zipBytes = inputStream.use { it.readBytes() }
            restoreFromZipBytes(zipBytes, resolution)
        }
    }

    suspend fun uploadBackupToDrive(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = getDriveService()
                ?: throw IllegalStateException("Google Drive account not authorized. Please connect in Settings or use 'Backup Now' for direct file export.")

            val zipBytes = createBackupZipBytes()
            val mediaContent = ByteArrayContent("application/zip", zipBytes)

            // Check if file already exists in Drive
            val existingFiles = drive.files().list()
                .setQ("(name = '$backupFileName' or name = '$legacyBackupFileName') and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")
                .execute()
                .files

            val fileId = if (existingFiles.isNullOrEmpty()) {
                val fileMetadata = File().apply {
                    name = backupFileName
                    description = "openED AI Studio Knowledge Backup"
                }
                val createdFile = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                createdFile.id
            } else {
                val existingFileId = existingFiles[0].id
                val fileMetadata = File().apply {
                    name = backupFileName
                }
                val updatedFile = drive.files().update(existingFileId, fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                updatedFile.id
            }

            preferencesRepository.setBackupDriveFileId(fileId)
            preferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
            fileId
        }
    }

    suspend fun downloadBackupZipBytes(): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = getDriveService() ?: return@runCatching null
            val files = drive.files().list()
                .setQ("(name = '$backupFileName' or name = '$legacyBackupFileName') and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")
                .execute()
                .files

            if (files.isNullOrEmpty()) return@runCatching null
            val fileId = files[0].id
            val inputStream: InputStream = drive.files().get(fileId).executeMediaAsInputStream()
            inputStream.use { it.readBytes() }
        }
    }

    suspend fun restoreBackupFromDrive(resolution: SyncConflictResolution): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = getDriveService()
                ?: throw IllegalStateException("Google Drive account not authorized. Please connect in Settings or use 'Backup Now' for direct file export.")

            val files = drive.files().list()
                .setQ("(name = '$backupFileName' or name = '$legacyBackupFileName') and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")
                .execute()
                .files

            if (files.isNullOrEmpty()) {
                throw IllegalStateException("No openED backup file found on Google Drive.")
            }

            val fileId = files[0].id
            val inputStream: InputStream = drive.files().get(fileId).executeMediaAsInputStream()
            val zipBytes = inputStream.use { it.readBytes() }

            restoreFromZipBytes(zipBytes, resolution)
        }
    }
}
