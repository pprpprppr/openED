package org.opened.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.drive.BackupBundle
import org.opened.app.core.drive.SyncConflictResolution
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    data class Success(val message: String, val timestamp: Long) : SyncResult()
    data class ConflictResolved(val message: String, val conflictCount: Int) : SyncResult()
    data class Failed(val reason: String, val throwable: Throwable? = null) : SyncResult()
}

@Singleton
class SyncManager @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val repository: OpenEDRepository,
    private val localProvider: LocalFileSystemSyncProvider,
    private val googleDriveProvider: GoogleDriveSyncProvider,
    private val webDavProvider: WebDavSyncProvider
) {

    fun getAvailableProviders(): List<SyncProvider> = listOf(
        localProvider,
        googleDriveProvider,
        webDavProvider
    )

    suspend fun getActiveProvider(): SyncProvider {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return when (prefs.syncProviderId) {
            "GOOGLE_DRIVE" -> googleDriveProvider
            "WEBDAV" -> webDavProvider
            else -> localProvider
        }
    }

    suspend fun synchronize(
        resolution: SyncConflictResolution = SyncConflictResolution.MERGE_LOCAL
    ): SyncResult = withContext(Dispatchers.IO) {
        val provider = getActiveProvider()

        if (!provider.isConfigured()) {
            return@withContext SyncResult.Failed("Provider ${provider.displayName} is not configured.")
        }

        try {
            // 1. Download remote bundle if available
            val remoteResult = provider.downloadLatestBundle()
            val remotePair = remoteResult.getOrNull()

            if (remotePair != null) {
                val (_, remoteBundle) = remotePair
                val overwrite = resolution == SyncConflictResolution.OVERWRITE_LOCAL
                repository.importDatabase(remoteBundle, overwrite = overwrite)
            }

            // 2. Export local merged state (local Room is source of truth)
            val mergedBundle = repository.exportDatabase()

            // 3. Upload merged state to provider
            val uploadResult = provider.uploadBundle(mergedBundle)
            val uploadUri = uploadResult.getOrThrow()

            val now = System.currentTimeMillis()
            preferencesRepository.setLastBackupTimestamp(now)

            SyncResult.Success(
                message = "Synced successfully with ${provider.displayName} ($uploadUri)",
                timestamp = now
            )
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Synchronization failed", e)
        }
    }
}
