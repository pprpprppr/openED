package org.opened.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.opened.app.core.platform.UserPreferencesStore
import org.opened.app.core.drive.BackupBundle
import org.opened.app.core.drive.SyncConflictResolution
import org.opened.app.domain.repository.OpenEDRepository

sealed class SyncResult {
    data class Success(val message: String, val timestamp: Long) : SyncResult()
    data class ConflictResolved(val message: String, val conflictCount: Int) : SyncResult()
    data class Failed(val reason: String, val throwable: Throwable? = null) : SyncResult()
}

class SyncManager(
    private val preferencesRepository: UserPreferencesStore,
    private val repository: OpenEDRepository,
    private val providers: List<SyncProvider>
) {

    fun getAvailableProviders(): List<SyncProvider> = providers

    suspend fun getActiveProvider(): SyncProvider {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return providers.firstOrNull { it.providerId == prefs.syncProviderId }
            ?: providers.firstOrNull { it.providerId == "LOCAL" }
            ?: providers.first()
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
