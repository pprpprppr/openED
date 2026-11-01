package org.opened.app.core.datastore

data class UserPreferences(
    val selectedModel: String = DEFAULT_MODEL,
    val customSystemInstructions: String = DEFAULT_SYSTEM_INSTRUCTIONS,
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val backupAccountEmail: String? = null,
    val backupDriveFileId: String? = null
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        val SUPPORTED_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-1.5-pro",
            "gemini-1.5-flash"
        )
        const val DEFAULT_SYSTEM_INSTRUCTIONS =
            "You are openED, an elite polymath mentor. Deliver rigorous, engaging, and structured self-learning courses. Blend clear conceptual intuition with technical depth, mathematical precision, code examples, and practical applications."
    }
}
