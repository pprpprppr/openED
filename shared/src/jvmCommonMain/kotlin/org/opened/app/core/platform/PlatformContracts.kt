package org.opened.app.core.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.core.drive.SyncConflictResolution
import java.io.File

/**
 * Cross-platform contract for encrypted API key and per-provider model configuration storage.
 */
interface ApiKeyStore {
    val activeApiKey: StateFlow<String?>
    val activeModel: StateFlow<String>

    fun warmUp()
    fun getApiKeyForProvider(provider: AiProvider): String?
    fun saveApiKeyForProvider(provider: AiProvider, apiKey: String)
    fun clearApiKeyForProvider(provider: AiProvider)
    fun hasApiKeyForProvider(provider: AiProvider): Boolean

    fun getModelForProvider(provider: AiProvider): String
    fun saveModelForProvider(provider: AiProvider, model: String)

    fun getCustomBaseUrl(): String
    fun saveCustomBaseUrl(url: String)

    fun getApiKey(): String?
    fun saveApiKey(apiKey: String)
    fun clearApiKey()
    fun hasApiKey(): Boolean
}

object ApiKeyStorageHelper {
    fun sanitizeCustomBaseUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return trimmed

        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            val host = trimmed.substringBefore('/').substringBefore(':')
            val isLocal = host.equals("localhost", ignoreCase = true) ||
                host == "127.0.0.1" ||
                host == "10.0.2.2" ||
                host.startsWith("192.168.") ||
                host.startsWith("10.")
            trimmed = (if (isLocal) "http://" else "https://") + trimmed
        } else if (trimmed.startsWith("http://", ignoreCase = true)) {
            val withoutScheme = trimmed.substring(7)
            val host = withoutScheme.substringBefore('/').substringBefore(':')
            val isLocal = host.equals("localhost", ignoreCase = true) ||
                host == "127.0.0.1" ||
                host == "10.0.2.2" ||
                host.startsWith("192.168.") ||
                host.startsWith("10.")
            if (!isLocal) {
                trimmed = "https://" + withoutScheme
            }
        }
        return trimmed
    }

    fun isDeprecatedGeminiModel(model: String?): Boolean {
        if (model.isNullOrBlank()) return false
        val m = model.trim().lowercase()
        return m.contains("2.5") || m.contains("2.0") || m.contains("1.5") || m == "gemini-3.5-flash"
    }
}

/**
 * Cross-platform contract for user preferences persistence.
 */
interface UserPreferencesStore {
    val userPreferencesFlow: Flow<UserPreferences>

    suspend fun setSelectedProvider(provider: String)
    suspend fun setSelectedModel(model: String)
    suspend fun setSelectionMode(mode: String)
    suspend fun setCustomBaseUrl(url: String)
    suspend fun setCustomSystemInstructions(instructions: String)
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setLastBackupTimestamp(timestamp: Long)
    suspend fun setBackupAccountEmail(email: String?)
    suspend fun setBackupDriveFileId(fileId: String?)
    suspend fun setLearningLanguage(language: String)
    suspend fun setBilingualTerminologyEnabled(enabled: Boolean)
    suspend fun setHighContrastEnabled(enabled: Boolean)
    suspend fun setReducedMotionEnabled(enabled: Boolean)
    suspend fun setSyncProviderId(providerId: String)
    suspend fun setWebDavConfig(url: String, username: String)
    suspend fun setLinkedBackupUri(uri: String?)
}

/**
 * Cross-platform backup & export actions consumed by SettingsViewModel.
 */
interface PlatformBackupGateway {
    val supportsGoogleDrive: Boolean
    val supportsScheduledBackups: Boolean

    fun getLastSignedInEmail(): String?
    fun isAuthorizedWithDrive(): Boolean
    suspend fun signOutGoogle()
    fun schedulePeriodicBackup()
    fun cancelPeriodicBackup()
    suspend fun uploadBackupToDrive(): Result<String>
    suspend fun restoreBackupFromDrive(resolution: SyncConflictResolution): Result<Unit>
    suspend fun backupToLinkedUri(uriString: String): Result<Unit>
    suspend fun createBackupZipBytes(): ByteArray
    suspend fun restoreFromZipBytes(zipBytes: ByteArray, resolution: SyncConflictResolution)
    suspend fun exportObsidianVault(): File
    suspend fun exportAnkiCsv(): File
    fun getAppDataDirectoryPath(): String
}
