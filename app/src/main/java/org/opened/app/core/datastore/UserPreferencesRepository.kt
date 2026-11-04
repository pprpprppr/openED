package org.opened.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.opened.app.core.ai.AiProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opened_user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SELECTION_MODE = stringPreferencesKey("selection_mode")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val SYSTEM_INSTRUCTIONS = stringPreferencesKey("custom_system_instructions")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val BACKUP_ACCOUNT_EMAIL = stringPreferencesKey("backup_account_email")
        val BACKUP_DRIVE_FILE_ID = stringPreferencesKey("backup_drive_file_id")

        // Phase 8: Accessibility & Localization
        val LEARNING_LANGUAGE = stringPreferencesKey("learning_language")
        val BILINGUAL_TERMINOLOGY_ENABLED = booleanPreferencesKey("bilingual_terminology_enabled")
        val HIGH_CONTRAST_ENABLED = booleanPreferencesKey("high_contrast_enabled")
        val REDUCED_MOTION_ENABLED = booleanPreferencesKey("reduced_motion_enabled")

        // Phase 9: Sync Provider
        val SYNC_PROVIDER_ID = stringPreferencesKey("sync_provider_id")
        val WEBDAV_SERVER_URL = stringPreferencesKey("webdav_server_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val LINKED_BACKUP_URI = stringPreferencesKey("linked_backup_uri")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                selectedProvider = preferences[PreferencesKeys.SELECTED_PROVIDER] ?: AiProvider.GEMINI.id,
                selectedModel = preferences[PreferencesKeys.SELECTED_MODEL] ?: UserPreferences.DEFAULT_MODEL,
                selectionMode = preferences[PreferencesKeys.SELECTION_MODE] ?: "auto",
                customBaseUrl = preferences[PreferencesKeys.CUSTOM_BASE_URL] ?: UserPreferences.DEFAULT_CUSTOM_BASE_URL,
                customSystemInstructions = preferences[PreferencesKeys.SYSTEM_INSTRUCTIONS] ?: UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS,
                autoBackupEnabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: true,
                lastBackupTimestamp = preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L,
                backupAccountEmail = preferences[PreferencesKeys.BACKUP_ACCOUNT_EMAIL],
                backupDriveFileId = preferences[PreferencesKeys.BACKUP_DRIVE_FILE_ID],
                learningLanguage = preferences[PreferencesKeys.LEARNING_LANGUAGE] ?: UserPreferences.DEFAULT_LEARNING_LANGUAGE,
                bilingualTerminologyEnabled = preferences[PreferencesKeys.BILINGUAL_TERMINOLOGY_ENABLED] ?: false,
                highContrastEnabled = preferences[PreferencesKeys.HIGH_CONTRAST_ENABLED] ?: false,
                reducedMotionEnabled = preferences[PreferencesKeys.REDUCED_MOTION_ENABLED] ?: false,
                syncProviderId = preferences[PreferencesKeys.SYNC_PROVIDER_ID] ?: UserPreferences.DEFAULT_SYNC_PROVIDER,
                webDavServerUrl = preferences[PreferencesKeys.WEBDAV_SERVER_URL] ?: "",
                webDavUsername = preferences[PreferencesKeys.WEBDAV_USERNAME] ?: "",
                linkedBackupUri = preferences[PreferencesKeys.LINKED_BACKUP_URI]
            )
        }

    suspend fun setSelectedProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_PROVIDER] = provider
        }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = model
        }
    }

    suspend fun setSelectionMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTION_MODE] = mode
        }
    }

    suspend fun setCustomBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_BASE_URL] = url
        }
    }

    suspend fun setCustomSystemInstructions(instructions: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_INSTRUCTIONS] = instructions
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }

    suspend fun setBackupAccountEmail(email: String?) {
        context.dataStore.edit { preferences ->
            if (email != null) {
                preferences[PreferencesKeys.BACKUP_ACCOUNT_EMAIL] = email
            } else {
                preferences.remove(PreferencesKeys.BACKUP_ACCOUNT_EMAIL)
            }
        }
    }

    suspend fun setBackupDriveFileId(fileId: String?) {
        context.dataStore.edit { preferences ->
            if (fileId != null) {
                preferences[PreferencesKeys.BACKUP_DRIVE_FILE_ID] = fileId
            } else {
                preferences.remove(PreferencesKeys.BACKUP_DRIVE_FILE_ID)
            }
        }
    }

    suspend fun setLearningLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LEARNING_LANGUAGE] = language
        }
    }

    suspend fun setBilingualTerminologyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BILINGUAL_TERMINOLOGY_ENABLED] = enabled
        }
    }

    suspend fun setHighContrastEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST_ENABLED] = enabled
        }
    }

    suspend fun setReducedMotionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REDUCED_MOTION_ENABLED] = enabled
        }
    }

    suspend fun setSyncProviderId(providerId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_PROVIDER_ID] = providerId
        }
    }

    suspend fun setWebDavConfig(url: String, username: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBDAV_SERVER_URL] = url
            preferences[PreferencesKeys.WEBDAV_USERNAME] = username
        }
    }

    suspend fun setLinkedBackupUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[PreferencesKeys.LINKED_BACKUP_URI] = uri
            } else {
                preferences.remove(PreferencesKeys.LINKED_BACKUP_URI)
            }
        }
    }
}
