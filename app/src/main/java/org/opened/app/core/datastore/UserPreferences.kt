package org.opened.app.core.datastore

import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels

data class UserPreferences(
    val selectedProvider: String = AiProvider.GEMINI.id,
    val selectedModel: String = DEFAULT_MODEL,
    val selectionMode: String = "auto",
    val customBaseUrl: String = DEFAULT_CUSTOM_BASE_URL,
    val selectedTutorPersona: String = DEFAULT_TUTOR_PERSONA,
    val customSystemInstructions: String = "",
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val backupAccountEmail: String? = null,
    val backupDriveFileId: String? = null,
    // Phase 8: Accessibility & Localization
    val learningLanguage: String = DEFAULT_LEARNING_LANGUAGE,
    val bilingualTerminologyEnabled: Boolean = false,
    val highContrastEnabled: Boolean = false,
    val reducedMotionEnabled: Boolean = false,
    // Phase 9: Multi-Device Sync Provider
    val syncProviderId: String = DEFAULT_SYNC_PROVIDER,
    val webDavServerUrl: String = "",
    val webDavUsername: String = "",
    val linkedBackupUri: String? = null
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_CUSTOM_BASE_URL = "http://localhost:11434/v1"
        val SUPPORTED_MODELS = AiProviderModels.GEMINI_MODELS
        const val DEFAULT_TUTOR_PERSONA = "ADAPTIVE_POLYMATH"
        const val DEFAULT_LEARNING_LANGUAGE = "English"
        const val DEFAULT_SYNC_PROVIDER = "LOCAL"
        val SUPPORTED_LANGUAGES = listOf("English", "Spanish", "French", "German", "Mandarin", "Hindi", "Arabic", "Portuguese", "Japanese")
        val SUPPORTED_SYNC_PROVIDERS = listOf("LOCAL", "GOOGLE_DRIVE", "WEBDAV")
        const val DEFAULT_SYSTEM_INSTRUCTIONS =
            "You are openED, an elite polymath mentor across the sciences, humanities, commerce, and engineering. Deliver engaging, intuitive, and clearly structured self-learning courses tailored precisely to the subject discipline and the learner's expertise level. Combine crystal-clear conceptual explanations with authentic domain examples, diagrams, and real-world applications."
    }
}
