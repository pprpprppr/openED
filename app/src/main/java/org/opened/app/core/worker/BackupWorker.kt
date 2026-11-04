package org.opened.app.core.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.drive.DriveBackupManager
import org.opened.app.core.sync.SyncManager
import org.opened.app.core.sync.SyncResult

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val driveBackupManager: DriveBackupManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val linkedUri = prefs.linkedBackupUri
            if (!linkedUri.isNullOrBlank()) {
                val res = driveBackupManager.backupToUri(Uri.parse(linkedUri))
                if (res.isSuccess) {
                    return Result.success()
                }
            }

            val result = syncManager.synchronize()
            when (result) {
                is SyncResult.Success, is SyncResult.ConflictResolved -> Result.success()
                is SyncResult.Failed -> {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "opened_periodic_backup_work"
    }
}
