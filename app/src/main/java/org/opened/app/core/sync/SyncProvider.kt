package org.opened.app.core.sync

import org.opened.app.core.drive.BackupBundle

interface SyncProvider {
    val providerId: String
    val displayName: String

    suspend fun isConfigured(): Boolean
    suspend fun uploadBundle(bundle: BackupBundle): Result<String>
    suspend fun downloadLatestBundle(): Result<Pair<String, BackupBundle>?>
    suspend fun listRevisions(): Result<List<SyncRevisionMetadata>>
}
