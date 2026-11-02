package org.opened.app.core.datastore

import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels

data class UserPreferences(
    val selectedProvider: String = AiProvider.GEMINI.id,
    val selectedModel: String = DEFAULT_MODEL,
    val customBaseUrl: String = DEFAULT_CUSTOM_BASE_URL,
    val customSystemInstructions: String = DEFAULT_SYSTEM_INSTRUCTIONS,
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val backupAccountEmail: String? = null,
    val backupDriveFileId: String? = null
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_CUSTOM_BASE_URL = "http://localhost:11434/v1"
        val SUPPORTED_MODELS = AiProviderModels.GEMINI_MODELS
        const val DEFAULT_SYSTEM_INSTRUCTIONS =
            "You are openED, an elite polymath mentor. Deliver engaging, intuitive, and clearly structured self-learning courses tailored precisely to the learner's expertise level. Combine crystal-clear conceptual explanations with practical code examples, diagrams, and real-world applications."
    }
}
