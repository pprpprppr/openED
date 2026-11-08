package org.opened.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels
import org.opened.app.core.ai.MultiAiClient
import org.opened.app.core.ai.TutorPersonaCategory
import org.opened.app.core.ai.TutorPersonaRegistry
import org.opened.app.core.ai.formatUserFriendlyAiError
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.drive.DriveBackupManager
import org.opened.app.core.drive.GoogleAuthManager
import org.opened.app.core.drive.SyncConflictResolution
import org.opened.app.core.export.ExportManager
import org.opened.app.core.security.SecureApiKeyStorage
import org.opened.app.core.worker.BackupScheduler
import org.opened.app.core.sync.SyncManager
import org.opened.app.core.sync.SyncResult
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStorage: SecureApiKeyStorage,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: GoogleAuthManager,
    private val driveBackupManager: DriveBackupManager,
    private val backupScheduler: BackupScheduler,
    private val exportManager: ExportManager,
    private val multiAiClient: MultiAiClient,
    private val syncManager: SyncManager,
    private val aiLearningEngine: AiLearningEngine
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        _uiState.update {
            it.copy(
                isBusy = false,
                isSyncingModels = false,
                errorMessage = throwable.localizedMessage ?: "An unexpected error occurred in Settings."
            )
        }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        checkKeyAndAuth()
    }

    private fun checkKeyAndAuth() {
        try {
            val account = authManager.getLastSignedInAccount()
            val isAuth = authManager.isAuthorized()

            _uiState.update {
                it.copy(
                    googleAccountEmail = account?.email,
                    isAuthorizedWithDrive = isAuth
                )
            }
        } catch (_: Exception) {}
    }

    private fun loadPreferences() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.userPreferencesFlow
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = e.localizedMessage) }
                }
                .collectLatest { prefs ->
                    val provider = AiProvider.fromId(prefs.selectedProvider)
                    val currentKey = apiKeyStorage.getApiKeyForProvider(provider).orEmpty()
                    val hasKey = apiKeyStorage.hasApiKeyForProvider(provider)
                    val rawModel = apiKeyStorage.getModelForProvider(provider).ifBlank {
                        prefs.selectedModel.ifBlank { provider.defaultModel }
                    }
                    val currentModel = if (provider == AiProvider.GEMINI && SecureApiKeyStorage.isDeprecatedGeminiModel(rawModel)) {
                        val migrated = "gemini-3.8-flash"
                        apiKeyStorage.saveModelForProvider(AiProvider.GEMINI, migrated)
                        preferencesRepository.setSelectedModel(migrated)
                        migrated
                    } else {
                        rawModel
                    }
                    if (provider == AiProvider.GEMINI && SecureApiKeyStorage.isDeprecatedGeminiModel(prefs.selectedModel)) {
                        preferencesRepository.setSelectedModel("gemini-3.8-flash")
                    }
                    val presets = AiProviderModels.getModelsForProvider(provider)

                    val selectionMode = org.opened.app.core.ai.provider.ModelSelectionMode.fromId(prefs.selectionMode)

                    _uiState.update {
                        it.copy(
                            selectedProvider = provider,
                            apiKey = currentKey,
                            hasApiKey = hasKey,
                            customBaseUrl = apiKeyStorage.getCustomBaseUrl(),
                            selectedModel = currentModel,
                            selectionMode = selectionMode,
                            availableModels = if (presets.isNotEmpty()) presets else it.availableModels,
                            selectedTutorPersonaId = prefs.selectedTutorPersona,
                            customSystemInstructions = prefs.customSystemInstructions,
                            systemInstructions = run {
                                val persona = TutorPersonaRegistry.getById(prefs.selectedTutorPersona)
                                if (prefs.customSystemInstructions.isNotBlank()) {
                                    "${persona.instruction}\n\nADDITIONAL USER CUSTOM INSTRUCTIONS:\n${prefs.customSystemInstructions}"
                                } else {
                                    persona.instruction
                                }
                            },
                            autoBackupEnabled = prefs.autoBackupEnabled,
                            lastBackupTimestamp = prefs.lastBackupTimestamp,
                            googleAccountEmail = prefs.backupAccountEmail,
                            learningLanguage = prefs.learningLanguage,
                            bilingualTerminologyEnabled = prefs.bilingualTerminologyEnabled,
                            highContrastEnabled = prefs.highContrastEnabled,
                            reducedMotionEnabled = prefs.reducedMotionEnabled,
                            syncProviderId = prefs.syncProviderId,
                            webDavServerUrl = prefs.webDavServerUrl,
                            webDavUsername = prefs.webDavUsername,
                            linkedBackupUri = prefs.linkedBackupUri
                        )
                    }
                }
        }
    }

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setSelectedProvider(provider.id)
                val currentKey = apiKeyStorage.getApiKeyForProvider(provider).orEmpty()
                val hasKey = apiKeyStorage.hasApiKeyForProvider(provider)
                val savedModel = apiKeyStorage.getModelForProvider(provider).ifBlank { provider.defaultModel }
                val presets = AiProviderModels.getModelsForProvider(provider)
                preferencesRepository.setSelectedModel(savedModel)

                _uiState.update {
                    it.copy(
                        selectedProvider = provider,
                        apiKey = currentKey,
                        hasApiKey = hasKey,
                        availableModels = presets,
                        selectedModel = savedModel,
                        customModelInput = ""
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun setApiKeyInput(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    fun saveApiKey() {
        val provider = _uiState.value.selectedProvider
        val key = _uiState.value.apiKey.trim()
        if (key.isNotBlank()) {
            viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
                try {
                    apiKeyStorage.saveApiKeyForProvider(provider, key)
                    _uiState.update {
                        it.copy(
                            hasApiKey = true,
                            statusMessage = "${provider.displayName} API key securely saved in Android Keystore."
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to save key.") }
                }
            }
        }
    }

    fun clearApiKey() {
        val provider = _uiState.value.selectedProvider
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                apiKeyStorage.clearApiKeyForProvider(provider)
                _uiState.update {
                    it.copy(
                        apiKey = "",
                        hasApiKey = false,
                        statusMessage = "${provider.displayName} API Key cleared."
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to clear key.") }
            }
        }
    }

    fun selectCustomPreset(presetUrl: String) {
        val sanitized = SecureApiKeyStorage.sanitizeCustomBaseUrl(presetUrl)
        _uiState.update { it.copy(customBaseUrl = sanitized) }
        apiKeyStorage.saveCustomBaseUrl(sanitized)
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setCustomBaseUrl(sanitized)
            _uiState.update { it.copy(statusMessage = "Custom API endpoint set to $sanitized") }
        }
    }

    fun setCustomBaseUrlInput(url: String) {
        _uiState.update { it.copy(customBaseUrl = url) }
    }

    fun saveCustomBaseUrl() {
        val raw = _uiState.value.customBaseUrl.trim()
        val sanitized = SecureApiKeyStorage.sanitizeCustomBaseUrl(raw)
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setCustomBaseUrl(sanitized)
                apiKeyStorage.saveCustomBaseUrl(sanitized)
                _uiState.update {
                    it.copy(
                        customBaseUrl = sanitized,
                        statusMessage = "Custom API endpoint saved."
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to save endpoint.")) }
            }
        }
    }

    fun selectModel(model: String) {
        val cleanModel = model.trim()
        if (cleanModel.isBlank()) return
        val provider = _uiState.value.selectedProvider

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setSelectedModel(cleanModel)
                apiKeyStorage.saveModelForProvider(provider, cleanModel)
                preferencesRepository.setSelectionMode(org.opened.app.core.ai.provider.ModelSelectionMode.MANUAL.id)
                _uiState.update {
                    val updatedModels = if (cleanModel !in it.availableModels) {
                        listOf(cleanModel) + it.availableModels
                    } else {
                        it.availableModels
                    }
                    it.copy(
                        selectedModel = cleanModel,
                        availableModels = updatedModels,
                        selectionMode = org.opened.app.core.ai.provider.ModelSelectionMode.MANUAL,
                        statusMessage = "Active model set to '$cleanModel' (Manual mode)"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to select model.") }
            }
        }
    }

    fun setCustomModelInput(model: String) {
        _uiState.update { it.copy(customModelInput = model) }
    }

    fun applyCustomModel() {
        val model = _uiState.value.customModelInput.trim()
        if (model.isNotBlank()) {
            selectModel(model)
            _uiState.update { it.copy(statusMessage = "Active model set to '$model'") }
        }
    }

    fun syncLiveModels() {
        val provider = _uiState.value.selectedProvider
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isSyncingModels = true, errorMessage = null) }
            multiAiClient.fetchLiveModels(provider)
                .onSuccess { liveModels ->
                    val filteredLiveModels = if (provider == AiProvider.GEMINI) {
                        liveModels.filterNot { SecureApiKeyStorage.isDeprecatedGeminiModel(it) }
                    } else {
                        liveModels
                    }
                    val presets = AiProviderModels.getModelsForProvider(provider)
                    val combined = (filteredLiveModels + presets).distinct().filter { it.isNotBlank() }
                    _uiState.update {
                        it.copy(
                            isSyncingModels = false,
                            availableModels = combined,
                            statusMessage = "Synced ${filteredLiveModels.size} live models from ${provider.displayName} API!"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSyncingModels = false,
                            errorMessage = formatUserFriendlyAiError(error.localizedMessage ?: "Failed to sync models from API.")
                        )
                    }
                }
        }
    }

    fun selectSelectionMode(mode: org.opened.app.core.ai.provider.ModelSelectionMode) {
        val provider = _uiState.value.selectedProvider
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setSelectionMode(mode.id)
                var updatedModel = _uiState.value.selectedModel
                if (provider == AiProvider.GEMINI) {
                    val targetModel = when (mode) {
                        org.opened.app.core.ai.provider.ModelSelectionMode.AUTO,
                        org.opened.app.core.ai.provider.ModelSelectionMode.BALANCED -> "gemini-3.8-flash"
                        org.opened.app.core.ai.provider.ModelSelectionMode.QUALITY -> "gemini-3.8-flash"
                        org.opened.app.core.ai.provider.ModelSelectionMode.ECONOMY -> "gemini-3.5-flash-lite"
                        org.opened.app.core.ai.provider.ModelSelectionMode.MANUAL -> updatedModel
                    }
                    updatedModel = targetModel
                    preferencesRepository.setSelectedModel(targetModel)
                    apiKeyStorage.saveModelForProvider(provider, targetModel)
                }
                _uiState.update {
                    it.copy(
                        selectionMode = mode,
                        selectedModel = updatedModel,
                        statusMessage = "AI selection mode set to ${mode.displayName}"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to set mode.")) }
            }
        }
    }

    fun testConnection() {
        val provider = _uiState.value.selectedProvider
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
            val result = multiAiClient.testConnection(provider)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionTestResult = if (result.isSuccess) {
                        "Connected (${result.latencyMs}ms, ${result.availableModelsCount} models)"
                    } else {
                        "Failed: ${result.error ?: result.message}"
                    },
                    statusMessage = if (result.isSuccess) result.message else null,
                    errorMessage = if (!result.isSuccess) formatUserFriendlyAiError(result.error ?: result.message) else null
                )
            }
        }
    }

    fun selectTutorPersona(personaId: String) {
        _uiState.update { it.copy(selectedTutorPersonaId = personaId) }
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setSelectedTutorPersona(personaId)
            val persona = TutorPersonaRegistry.getById(personaId)
            _uiState.update { it.copy(statusMessage = "Tutor persona selected: ${persona.title}") }
        }
    }

    fun selectPersonaCategory(category: TutorPersonaCategory) {
        _uiState.update { it.copy(selectedPersonaCategory = category) }
    }

    fun setCustomSystemInstructions(instructions: String) {
        _uiState.update { it.copy(customSystemInstructions = instructions) }
    }

    fun toggleCustomTemplate(templateText: String) {
        val snippet = templateText.trim()
        val current = _uiState.value.customSystemInstructions.trim()
        val isPresent = current.contains(snippet, ignoreCase = true)
        val updated = if (isPresent) {
            current.replace(snippet, "")
                .lines()
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .trim()
        } else {
            if (current.isBlank()) {
                snippet
            } else {
                "$current\n\n$snippet"
            }
        }

        _uiState.update {
            it.copy(
                customSystemInstructions = updated,
                statusMessage = if (isPresent) "Template removed." else "Template applied & saved."
            )
        }

        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setCustomSystemInstructions(updated)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun saveCustomSystemInstructions() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setCustomSystemInstructions(_uiState.value.customSystemInstructions)
                _uiState.update { it.copy(statusMessage = "Custom instructions saved successfully.") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun clearCustomSystemInstructions() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setCustomSystemInstructions("")
                _uiState.update {
                    it.copy(
                        customSystemInstructions = "",
                        statusMessage = "Custom instructions cleared."
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun toggleCustomInstructionsExpanded() {
        _uiState.update { it.copy(isCustomInstructionsExpanded = !it.isCustomInstructionsExpanded) }
    }

    fun openGeneratePromptDialog(initialNotes: String = "") {
        val defaultNotes = if (initialNotes.isNotBlank()) {
            initialNotes
        } else {
            _uiState.value.customSystemInstructions
        }
        _uiState.update {
            it.copy(
                showGeneratePromptDialog = true,
                promptIdeaDraft = defaultNotes,
                generatedPromptPreview = null,
                promptGenerationError = null
            )
        }
    }

    fun closeGeneratePromptDialog() {
        _uiState.update {
            it.copy(
                showGeneratePromptDialog = false,
                isGeneratingPrompt = false,
                generatedPromptPreview = null,
                promptGenerationError = null
            )
        }
    }

    fun setPromptIdeaDraft(draft: String) {
        _uiState.update { it.copy(promptIdeaDraft = draft) }
    }

    fun generateSystemInstructionPrompt() {
        val draft = _uiState.value.promptIdeaDraft.trim()
        val targetSubject = TutorPersonaRegistry.getById(_uiState.value.selectedTutorPersonaId).title
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isGeneratingPrompt = true, promptGenerationError = null) }
            val effectiveNotes = if (draft.isNotBlank()) {
                draft
            } else {
                "Be a patient, clear, and rigorous mentor in $targetSubject who uses active recall, practical analogies, and step-by-step examples."
            }
            val result = aiLearningEngine.generateSystemInstruction(
                userNotes = effectiveNotes,
                targetSubject = targetSubject
            )
            result.onSuccess { generatedText ->
                _uiState.update {
                    it.copy(
                        isGeneratingPrompt = false,
                        generatedPromptPreview = generatedText
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingPrompt = false,
                        promptGenerationError = formatUserFriendlyAiError(err.localizedMessage ?: "Failed to generate prompt.")
                    )
                }
            }
        }
    }

    fun applyGeneratedPrompt() {
        val generated = _uiState.value.generatedPromptPreview
        if (!generated.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    customSystemInstructions = generated,
                    showGeneratePromptDialog = false,
                    generatedPromptPreview = null,
                    statusMessage = "AI prompt applied to Custom Instructions! Saved successfully."
                )
            }
            viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
                try {
                    preferencesRepository.setCustomSystemInstructions(generated)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = e.localizedMessage) }
                }
            }
        }
    }

    fun setSystemInstructions(instructions: String) {
        _uiState.update { it.copy(systemInstructions = instructions) }
    }

    fun saveSystemInstructions() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setCustomSystemInstructions(_uiState.value.systemInstructions)
                _uiState.update { it.copy(statusMessage = "System instructions saved.") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun applySystemInstructionPreset(presetInstructions: String) {
        _uiState.update { it.copy(systemInstructions = presetInstructions) }
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setCustomSystemInstructions(presetInstructions)
                _uiState.update { it.copy(statusMessage = "Preset persona applied & saved.") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun resetSystemInstructions() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                val defaultInstructions = UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS
                preferencesRepository.setCustomSystemInstructions(defaultInstructions)
                _uiState.update {
                    it.copy(
                        systemInstructions = defaultInstructions,
                        statusMessage = "Reset to default instructions."
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                preferencesRepository.setAutoBackupEnabled(enabled)
                if (enabled) {
                    backupScheduler.schedulePeriodicBackup()
                } else {
                    backupScheduler.cancelPeriodicBackup()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun getSignInIntent(): android.content.Intent = authManager.signInClient.signInIntent

    fun onGoogleSignInResult(account: GoogleSignInAccount?) {
        if (account == null) {
            onGoogleSignInError(-1, "No Google account selected")
            return
        }
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                authManager.handleSignInResult(account)
                checkKeyAndAuth()
                _uiState.update {
                    it.copy(statusMessage = "Connected to Google Drive as ${account.email}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Sign in failed.") }
            }
        }
    }

    fun onGoogleSignInError(statusCode: Int, message: String?) {
        _uiState.update {
            it.copy(
                isBusy = false,
                statusMessage = "Direct Google Drive file backup via 'Backup Now' is ready to use! (Background OAuth auto-sync requires registering your build's SHA-1 fingerprint in Google Cloud Console)."
            )
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                authManager.signOut()
                checkKeyAndAuth()
                _uiState.update { it.copy(statusMessage = "Signed out from Google Drive.") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun syncWithActiveProvider() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            val result = syncManager.synchronize()
            when (result) {
                is SyncResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = result.message,
                            lastBackupTimestamp = result.timestamp
                        )
                    }
                }
                is SyncResult.ConflictResolved -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = result.message
                        )
                    }
                }
                is SyncResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = formatUserFriendlyAiError(result.reason)
                        )
                    }
                }
            }
        }
    }

    fun backupToGoogleDrive() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            val result = driveBackupManager.uploadBackupToDrive()
            result.fold(
                onSuccess = { fileId ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Successfully uploaded backup to Google Drive!"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = formatUserFriendlyAiError(error.localizedMessage ?: "Backup failed.")
                        )
                    }
                }
            )
        }
    }

    fun restoreFromGoogleDrive(resolution: SyncConflictResolution) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            val result = driveBackupManager.restoreBackupFromDrive(resolution)
            result.fold(
                onSuccess = {
                    val modeLabel = if (resolution == SyncConflictResolution.OVERWRITE_LOCAL) "overwritten" else "merged"
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Knowledge Base successfully restored and $modeLabel!"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = formatUserFriendlyAiError(error.localizedMessage ?: "Restore failed.")
                        )
                    }
                }
            )
        }
    }

    fun setLinkedBackupUri(uriString: String?) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setLinkedBackupUri(uriString)
            _uiState.update { it.copy(linkedBackupUri = uriString) }
        }
    }

    fun backupToLinkedUri(uriString: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            try {
                val uri = android.net.Uri.parse(uriString)
                val result = driveBackupManager.backupToUri(uri)
                result.fold(
                    onSuccess = {
                        val now = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                lastBackupTimestamp = now,
                                statusMessage = "Backup successfully saved to linked Google Drive / storage file!"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = formatUserFriendlyAiError(error.localizedMessage ?: "Failed to write to linked backup file.")
                            )
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to write to linked backup file.")
                    )
                }
            }
        }
    }

    fun backupToStream(outputStream: OutputStream) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            try {
                val zipBytes = driveBackupManager.createBackupZipBytes()
                outputStream.use { it.write(zipBytes) }
                val now = System.currentTimeMillis()
                preferencesRepository.setLastBackupTimestamp(now)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastBackupTimestamp = now,
                        statusMessage = "Backup successfully saved to selected storage!"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to save backup to storage.")
                    )
                }
            }
        }
    }

    fun restoreFromStream(inputStream: InputStream, resolution: SyncConflictResolution) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            try {
                val zipBytes = inputStream.use { it.readBytes() }
                driveBackupManager.restoreFromZipBytes(zipBytes, resolution)
                val modeLabel = if (resolution == SyncConflictResolution.OVERWRITE_LOCAL) "overwritten" else "merged"
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Knowledge Base successfully restored and $modeLabel!"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = formatUserFriendlyAiError(e.localizedMessage ?: "Failed to restore backup from selected file.")
                    )
                }
            }
        }
    }

    suspend fun getExportZipBytes(): ByteArray = withContext(Dispatchers.IO) {
        driveBackupManager.createBackupZipBytes()
    }

    suspend fun importZipBytes(bytes: ByteArray, resolution: SyncConflictResolution) = withContext(Dispatchers.IO) {
        driveBackupManager.restoreFromZipBytes(bytes, resolution)
        _uiState.update { it.copy(statusMessage = "Imported backup successfully.") }
    }

    fun setLearningLanguage(language: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setLearningLanguage(language)
            _uiState.update { it.copy(learningLanguage = language) }
        }
    }

    fun setBilingualTerminologyEnabled(enabled: Boolean) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setBilingualTerminologyEnabled(enabled)
            _uiState.update { it.copy(bilingualTerminologyEnabled = enabled) }
        }
    }

    fun setHighContrastEnabled(enabled: Boolean) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setHighContrastEnabled(enabled)
            _uiState.update { it.copy(highContrastEnabled = enabled) }
        }
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setReducedMotionEnabled(enabled)
            _uiState.update { it.copy(reducedMotionEnabled = enabled) }
        }
    }

    fun setSyncProviderId(providerId: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setSyncProviderId(providerId)
            _uiState.update { it.copy(syncProviderId = providerId) }
        }
    }

    fun setWebDavConfig(url: String, username: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            preferencesRepository.setWebDavConfig(url, username)
            _uiState.update { it.copy(webDavServerUrl = url, webDavUsername = username) }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun exportObsidianVault(onResult: (File) -> Unit) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching { exportManager.exportObsidianVault() }
                .onSuccess { file ->
                    _uiState.update { it.copy(isBusy = false, statusMessage = "Obsidian Vault exported.") }
                    withContext(Dispatchers.Main) { onResult(file) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = e.message ?: "Export failed.") }
                }
        }
    }

    fun exportAnkiCsv(onResult: (File) -> Unit) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching { exportManager.exportAnkiCsv() }
                .onSuccess { file ->
                    _uiState.update { it.copy(isBusy = false, statusMessage = "Anki deck exported.") }
                    withContext(Dispatchers.Main) { onResult(file) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = e.message ?: "Export failed.") }
                }
        }
    }
}
