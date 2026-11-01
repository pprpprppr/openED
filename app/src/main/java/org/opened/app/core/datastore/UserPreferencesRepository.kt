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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opened_user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SYSTEM_INSTRUCTIONS = stringPreferencesKey("custom_system_instructions")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val BACKUP_ACCOUNT_EMAIL = stringPreferencesKey("backup_account_email")
        val BACKUP_DRIVE_FILE_ID = stringPreferencesKey("backup_drive_file_id")
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
                selectedModel = preferences[PreferencesKeys.SELECTED_MODEL] ?: UserPreferences.DEFAULT_MODEL,
                customSystemInstructions = preferences[PreferencesKeys.SYSTEM_INSTRUCTIONS] ?: UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS,
                autoBackupEnabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: true,
                lastBackupTimestamp = preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L,
                backupAccountEmail = preferences[PreferencesKeys.BACKUP_ACCOUNT_EMAIL],
                backupDriveFileId = preferences[PreferencesKeys.BACKUP_DRIVE_FILE_ID]
            )
        }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = model
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
}
