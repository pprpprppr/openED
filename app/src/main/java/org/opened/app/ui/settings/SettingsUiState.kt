package org.opened.app.ui.settings

import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels
import org.opened.app.core.datastore.UserPreferences

data class SettingsUiState(
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val customBaseUrl: String = UserPreferences.DEFAULT_CUSTOM_BASE_URL,
    val selectedModel: String = UserPreferences.DEFAULT_MODEL,
    val availableModels: List<String> = AiProviderModels.GEMINI_MODELS,
    val isSyncingModels: Boolean = false,
    val customModelInput: String = "",
    val systemInstructions: String = UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS,
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val googleAccountEmail: String? = null,
    val isAuthorizedWithDrive: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
