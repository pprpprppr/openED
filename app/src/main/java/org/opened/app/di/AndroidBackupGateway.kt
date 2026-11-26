package org.opened.app.di

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.opened.app.core.drive.DriveBackupManager
import org.opened.app.core.drive.GoogleAuthManager
import org.opened.app.core.drive.SyncConflictResolution
import org.opened.app.core.export.ExportManager
import org.opened.app.core.platform.PlatformBackupGateway
import org.opened.app.core.worker.BackupScheduler
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager,
    private val driveBackupManager: DriveBackupManager,
    private val backupScheduler: BackupScheduler,
    private val exportManager: ExportManager
) : PlatformBackupGateway {

    override val supportsGoogleDrive: Boolean = true
    override val supportsScheduledBackups: Boolean = true

    override fun getLastSignedInEmail(): String? = authManager.getLastSignedInAccount()?.email

    override fun isAuthorizedWithDrive(): Boolean = authManager.isAuthorized()

    override suspend fun signOutGoogle() = authManager.signOut()

    override fun schedulePeriodicBackup() = backupScheduler.schedulePeriodicBackup()

    override fun cancelPeriodicBackup() = backupScheduler.cancelPeriodicBackup()

    override suspend fun uploadBackupToDrive(): Result<String> = driveBackupManager.uploadBackupToDrive()

    override suspend fun restoreBackupFromDrive(resolution: SyncConflictResolution): Result<Unit> =
        driveBackupManager.restoreBackupFromDrive(resolution)

    override suspend fun backupToLinkedUri(uriString: String): Result<Unit> =
        driveBackupManager.backupToUri(Uri.parse(uriString))

    override suspend fun createBackupZipBytes(): ByteArray = driveBackupManager.createBackupZipBytes()

    override suspend fun restoreFromZipBytes(zipBytes: ByteArray, resolution: SyncConflictResolution) {
        driveBackupManager.restoreFromZipBytes(zipBytes, resolution)
    }

    override suspend fun exportObsidianVault(): File = exportManager.exportObsidianVault()

    override suspend fun exportAnkiCsv(): File = exportManager.exportAnkiCsv()

    override fun getAppDataDirectoryPath(): String = context.filesDir.absolutePath
}
