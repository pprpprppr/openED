package org.opened.app.ui.settings

import org.opened.app.core.datastore.UserPreferences

data class SettingsUiState(
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val selectedModel: String = UserPreferences.DEFAULT_MODEL,
    val systemInstructions: String = UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS,
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val googleAccountEmail: String? = null,
    val isAuthorizedWithDrive: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
