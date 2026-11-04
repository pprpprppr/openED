package org.opened.app.core.ai

/**
 * Formats AI and network exceptions into concise, actionable, user-friendly 1-line messages.
 */
fun formatUserFriendlyAiError(rawError: String?): String {
    val error = rawError.orEmpty().trim()
    if (error.isEmpty()) {
        return "⚠️ An unexpected AI error occurred. Please try again."
    }

    val lower = error.lowercase()
    if (lower.contains("429") ||
        lower.contains("quota exceeded") ||
        lower.contains("resource_exhausted") ||
        lower.contains("rate-limit") ||
        lower.contains("rate limit") ||
        lower.contains("limit: 0")
    ) {
        return "⚠️ API quota limit reached for this model. Please switch to a Flash/Free model in Settings or try again in a minute."
    }

    if (lower.contains("cleartext")) {
        return "⚠️ Unencrypted http:// URL blocked. Please use https:// (e.g. https://openrouter.ai/api/v1)."
    }

    if (lower.contains("401") ||
        lower.contains("api_key_invalid") ||
        lower.contains("invalid api key") ||
        lower.contains("unauthorized")
    ) {
        return "⚠️ Invalid API key. Please check your key in Settings."
    }

    // Strip URLs, JSON stack traces, and brackets for any other error
    var cleaned = error
        .replace(Regex("https?://[^\\s]+"), "")
        .replace(Regex("\\{[^}]*\\}"), "")
        .replace(Regex("\\[[^\\]]*\\]"), "")
        .replace(Regex("at [a-zA-Z0-9_.]+\\([^)]*\\)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (cleaned.isBlank()) {
        cleaned = "⚠️ An error occurred while communicating with the AI service."
    }

    return if (cleaned.length > 110) {
        cleaned.take(107) + "..."
    } else {
        cleaned
    }
}

fun formatUserFriendlyAiError(throwable: Throwable): String {
    val message = throwable.localizedMessage ?: throwable.message ?: throwable.toString()
    return formatUserFriendlyAiError(message)
}
