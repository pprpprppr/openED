package org.opened.app.ui.settings

import androidx.compose.runtime.Immutable
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels
import org.opened.app.core.ai.provider.ModelSelectionMode
import org.opened.app.core.datastore.UserPreferences

@Immutable
data class SettingsUiState(
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val customBaseUrl: String = UserPreferences.DEFAULT_CUSTOM_BASE_URL,
    val selectedModel: String = UserPreferences.DEFAULT_MODEL,
    val selectionMode: ModelSelectionMode = ModelSelectionMode.AUTO,
    val availableModels: List<String> = AiProviderModels.GEMINI_MODELS,
    val isSyncingModels: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null,
    val customModelInput: String = "",
    val systemInstructions: String = UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS,
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val googleAccountEmail: String? = null,
    val isAuthorizedWithDrive: Boolean = false,
    // Phase 8: Accessibility & Localization
    val learningLanguage: String = UserPreferences.DEFAULT_LEARNING_LANGUAGE,
    val bilingualTerminologyEnabled: Boolean = false,
    val highContrastEnabled: Boolean = false,
    val reducedMotionEnabled: Boolean = false,
    // Phase 9: Sync Architecture
    val syncProviderId: String = UserPreferences.DEFAULT_SYNC_PROVIDER,
    val webDavServerUrl: String = "",
    val webDavUsername: String = "",
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val isDriveAuthorized: Boolean get() = isAuthorizedWithDrive
}
