package org.opened.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toOkioPath
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.core.platform.UserPreferencesStore
import java.io.IOException

class DesktopUserPreferencesRepository : UserPreferencesStore {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        produceFile = { DesktopPaths.preferencesFile.toOkioPath() }
    )

    private object Keys {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SELECTION_MODE = stringPreferencesKey("selection_mode")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val SYSTEM_INSTRUCTIONS = stringPreferencesKey("custom_system_instructions")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val BACKUP_ACCOUNT_EMAIL = stringPreferencesKey("backup_account_email")
        val BACKUP_DRIVE_FILE_ID = stringPreferencesKey("backup_drive_file_id")
        val LEARNING_LANGUAGE = stringPreferencesKey("learning_language")
        val BILINGUAL_TERMINOLOGY_ENABLED = booleanPreferencesKey("bilingual_terminology_enabled")
        val HIGH_CONTRAST_ENABLED = booleanPreferencesKey("high_contrast_enabled")
        val REDUCED_MOTION_ENABLED = booleanPreferencesKey("reduced_motion_enabled")
        val SYNC_PROVIDER_ID = stringPreferencesKey("sync_provider_id")
        val WEBDAV_SERVER_URL = stringPreferencesKey("webdav_server_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val LINKED_BACKUP_URI = stringPreferencesKey("linked_backup_uri")
    }

    override val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            UserPreferences(
                selectedProvider = p[Keys.SELECTED_PROVIDER] ?: AiProvider.GEMINI.id,
                selectedModel = p[Keys.SELECTED_MODEL] ?: UserPreferences.DEFAULT_MODEL,
                selectionMode = p[Keys.SELECTION_MODE] ?: "auto",
                customBaseUrl = p[Keys.CUSTOM_BASE_URL] ?: UserPreferences.DEFAULT_CUSTOM_BASE_URL,
                customSystemInstructions = run {
                    val raw = p[Keys.SYSTEM_INSTRUCTIONS] ?: ""
                    if (raw == UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS) "" else raw
                },
                autoBackupEnabled = p[Keys.AUTO_BACKUP_ENABLED] ?: true,
                lastBackupTimestamp = p[Keys.LAST_BACKUP_TIMESTAMP] ?: 0L,
                backupAccountEmail = p[Keys.BACKUP_ACCOUNT_EMAIL],
                backupDriveFileId = p[Keys.BACKUP_DRIVE_FILE_ID],
                learningLanguage = p[Keys.LEARNING_LANGUAGE] ?: UserPreferences.DEFAULT_LEARNING_LANGUAGE,
                bilingualTerminologyEnabled = p[Keys.BILINGUAL_TERMINOLOGY_ENABLED] ?: false,
                highContrastEnabled = p[Keys.HIGH_CONTRAST_ENABLED] ?: false,
                reducedMotionEnabled = p[Keys.REDUCED_MOTION_ENABLED] ?: false,
                syncProviderId = p[Keys.SYNC_PROVIDER_ID] ?: UserPreferences.DEFAULT_SYNC_PROVIDER,
                webDavServerUrl = p[Keys.WEBDAV_SERVER_URL] ?: "",
                webDavUsername = p[Keys.WEBDAV_USERNAME] ?: "",
                linkedBackupUri = p[Keys.LINKED_BACKUP_URI]
            )
        }

    override suspend fun setSelectedProvider(provider: String) { dataStore.edit { it[Keys.SELECTED_PROVIDER] = provider } }
    override suspend fun setSelectedModel(model: String) { dataStore.edit { it[Keys.SELECTED_MODEL] = model } }
    override suspend fun setSelectionMode(mode: String) { dataStore.edit { it[Keys.SELECTION_MODE] = mode } }
    override suspend fun setCustomBaseUrl(url: String) { dataStore.edit { it[Keys.CUSTOM_BASE_URL] = url } }
    override suspend fun setCustomSystemInstructions(instructions: String) { dataStore.edit { it[Keys.SYSTEM_INSTRUCTIONS] = instructions } }
    override suspend fun setAutoBackupEnabled(enabled: Boolean) { dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled } }
    override suspend fun setLastBackupTimestamp(timestamp: Long) { dataStore.edit { it[Keys.LAST_BACKUP_TIMESTAMP] = timestamp } }
    override suspend fun setBackupAccountEmail(email: String?) {
        dataStore.edit { if (email != null) it[Keys.BACKUP_ACCOUNT_EMAIL] = email else it.remove(Keys.BACKUP_ACCOUNT_EMAIL) }
    }
    override suspend fun setBackupDriveFileId(fileId: String?) {
        dataStore.edit { if (fileId != null) it[Keys.BACKUP_DRIVE_FILE_ID] = fileId else it.remove(Keys.BACKUP_DRIVE_FILE_ID) }
    }
    override suspend fun setLearningLanguage(language: String) { dataStore.edit { it[Keys.LEARNING_LANGUAGE] = language } }
    override suspend fun setBilingualTerminologyEnabled(enabled: Boolean) { dataStore.edit { it[Keys.BILINGUAL_TERMINOLOGY_ENABLED] = enabled } }
    override suspend fun setHighContrastEnabled(enabled: Boolean) { dataStore.edit { it[Keys.HIGH_CONTRAST_ENABLED] = enabled } }
    override suspend fun setReducedMotionEnabled(enabled: Boolean) { dataStore.edit { it[Keys.REDUCED_MOTION_ENABLED] = enabled } }
    override suspend fun setSyncProviderId(providerId: String) { dataStore.edit { it[Keys.SYNC_PROVIDER_ID] = providerId } }
    override suspend fun setWebDavConfig(url: String, username: String) {
        dataStore.edit {
            it[Keys.WEBDAV_SERVER_URL] = url
            it[Keys.WEBDAV_USERNAME] = username
        }
    }
    override suspend fun setLinkedBackupUri(uri: String?) {
        dataStore.edit { if (uri != null) it[Keys.LINKED_BACKUP_URI] = uri else it.remove(Keys.LINKED_BACKUP_URI) }
    }
}
