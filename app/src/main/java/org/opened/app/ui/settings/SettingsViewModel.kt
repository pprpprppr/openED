package org.opened.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.drive.DriveBackupManager
import org.opened.app.core.drive.GoogleAuthManager
import org.opened.app.core.drive.SyncConflictResolution
import org.opened.app.core.security.SecureApiKeyStorage
import org.opened.app.core.worker.BackupScheduler
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStorage: SecureApiKeyStorage,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: GoogleAuthManager,
    private val driveBackupManager: DriveBackupManager,
    private val backupScheduler: BackupScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        checkKeyAndAuth()
    }

    private fun checkKeyAndAuth() {
        val currentKey = apiKeyStorage.getApiKey().orEmpty()
        val hasKey = apiKeyStorage.hasApiKey()
        val account = authManager.getLastSignedInAccount()
        val isAuth = authManager.isAuthorized()

        _uiState.update {
            it.copy(
                apiKey = currentKey,
                hasApiKey = hasKey,
                googleAccountEmail = account?.email,
                isAuthorizedWithDrive = isAuth
            )
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collectLatest { prefs ->
                _uiState.update {
                    it.copy(
                        selectedModel = prefs.selectedModel,
                        systemInstructions = prefs.customSystemInstructions,
                        autoBackupEnabled = prefs.autoBackupEnabled,
                        lastBackupTimestamp = prefs.lastBackupTimestamp,
                        googleAccountEmail = prefs.backupAccountEmail
                    )
                }
            }
        }
    }

    fun setApiKeyInput(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    fun saveApiKey() {
        val key = _uiState.value.apiKey.trim()
        if (key.isNotBlank()) {
            apiKeyStorage.saveApiKey(key)
            _uiState.update {
                it.copy(
                    hasApiKey = true,
                    statusMessage = "Gemini API key securely saved in Android Keystore."
                )
            }
        }
    }

    fun clearApiKey() {
        apiKeyStorage.clearApiKey()
        _uiState.update {
            it.copy(
                apiKey = "",
                hasApiKey = false,
                statusMessage = "API Key cleared."
            )
        }
    }

    fun selectModel(model: String) {
        viewModelScope.launch {
            preferencesRepository.setSelectedModel(model)
        }
    }

    fun setSystemInstructions(instructions: String) {
        _uiState.update { it.copy(systemInstructions = instructions) }
    }

    fun saveSystemInstructions() {
        viewModelScope.launch {
            preferencesRepository.setCustomSystemInstructions(_uiState.value.systemInstructions)
            _uiState.update { it.copy(statusMessage = "System instructions saved.") }
        }
    }

    fun resetSystemInstructions() {
        viewModelScope.launch {
            preferencesRepository.setCustomSystemInstructions(UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS)
            _uiState.update {
                it.copy(
                    systemInstructions = UserPreferences.DEFAULT_SYSTEM_INSTRUCTIONS,
                    statusMessage = "Reset to default instructions."
                )
            }
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoBackupEnabled(enabled)
            if (enabled) {
                backupScheduler.schedulePeriodicBackup()
            } else {
                backupScheduler.cancelPeriodicBackup()
            }
        }
    }

    fun onGoogleSignInResult(account: GoogleSignInAccount?) {
        viewModelScope.launch {
            authManager.handleSignInResult(account)
            checkKeyAndAuth()
            if (account != null) {
                _uiState.update {
                    it.copy(statusMessage = "Connected to Google Drive as ${account.email}")
                }
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            authManager.signOut()
            checkKeyAndAuth()
            _uiState.update { it.copy(statusMessage = "Signed out from Google Drive.") }
        }
    }

    fun backupToGoogleDrive() {
        viewModelScope.launch {
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
                            errorMessage = error.localizedMessage ?: "Backup failed."
                        )
                    }
                }
            )
        }
    }

    fun restoreFromGoogleDrive(resolution: SyncConflictResolution) {
        viewModelScope.launch {
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
                            errorMessage = error.localizedMessage ?: "Restore failed."
                        )
                    }
                }
            )
        }
    }

    suspend fun getExportZipBytes(): ByteArray {
        return driveBackupManager.createBackupZipBytes()
    }

    suspend fun importZipBytes(bytes: ByteArray, resolution: SyncConflictResolution) {
        driveBackupManager.restoreFromZipBytes(bytes, resolution)
        _uiState.update { it.copy(statusMessage = "Imported backup successfully.") }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
