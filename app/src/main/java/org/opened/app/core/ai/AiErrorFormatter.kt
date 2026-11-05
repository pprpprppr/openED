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

    // 1. Quota / Rate-limit errors
    if (lower.contains("429") ||
        lower.contains("quota exceeded") ||
        lower.contains("resource_exhausted") ||
        lower.contains("rate-limit") ||
        lower.contains("rate limit") ||
        lower.contains("limit: 0")
    ) {
        return "⚠️ API quota limit reached for this model. Please switch to a Flash/Free model in Settings or try again in a minute."
    }

    // 2. Cleartext / HTTP security policy
    if (lower.contains("cleartext")) {
        return "⚠️ Unencrypted http:// URL blocked. Please use https:// (e.g. https://openrouter.ai/api/v1)."
    }

    // 3. Authentication & API Key errors
    if (lower.contains("401") ||
        lower.contains("api_key_invalid") ||
        lower.contains("invalid api key") ||
        lower.contains("unauthorized") ||
        lower.contains("authentication failed")
    ) {
        return "⚠️ Invalid API key. Please check your key in Settings."
    }

    if (lower.contains("api key is missing") ||
        lower.contains("missing api key") ||
        lower.contains("missingapikeyexception")
    ) {
        return "⚠️ API key missing. Please configure your API key in Settings."
    }

    // 4. Model not found or deprecated
    if (lower.contains("404") ||
        lower.contains("not_found") ||
        lower.contains("model not found") ||
        lower.contains("no longer available") ||
        lower.contains("model_not_found")
    ) {
        return "⚠️ Model not found or deprecated. Please choose an active model in Settings."
    }

    // 5. Access forbidden / permissions
    if (lower.contains("403") ||
        lower.contains("permission_denied") ||
        lower.contains("forbidden") ||
        lower.contains("access denied")
    ) {
        return "⚠️ Access denied. Please ensure your API key has permission to use this model."
    }

    // 6. Network connectivity & Host resolution
    if (lower.contains("unable to resolve host") ||
        lower.contains("unknownhostexception") ||
        lower.contains("failed to connect") ||
        lower.contains("connectexception") ||
        lower.contains("no route to host") ||
        lower.contains("network unreachable") ||
        lower.contains("no address associated") ||
        lower.contains("socketexception")
    ) {
        return "⚠️ Unable to connect. Please check your internet connection and try again."
    }

    // 7. Timeouts
    if (lower.contains("sockettimeoutexception") ||
        lower.contains("timed out") ||
        lower.contains("timeout") ||
        lower.contains("read time out")
    ) {
        return "⚠️ Request timed out. The AI model is taking too long to respond. Please try again."
    }

    // 8. SSL / TLS handshake failures
    if (lower.contains("sslhandshakeexception") ||
        lower.contains("sslpeerunverifiedexception") ||
        lower.contains("certpathvalidatorexception")
    ) {
        return "⚠️ Secure connection (SSL/TLS) failed. Please check your network or custom endpoint URL."
    }

    // 9. Server errors & Overload
    if (lower.contains("500") ||
        lower.contains("502") ||
        lower.contains("503") ||
        lower.contains("504") ||
        lower.contains("bad gateway") ||
        lower.contains("service unavailable") ||
        lower.contains("gateway timeout") ||
        lower.contains("internal_error") ||
        lower.contains("internal server error") ||
        lower.contains("overloaded")
    ) {
        return "⚠️ AI service is temporarily unavailable or overloaded. Please try again in a few moments."
    }

    // 10. JSON parsing, EOF truncation & Serialization
    if (lower.contains("serializationexception") ||
        lower.contains("jsondecodingexception") ||
        lower.contains("unexpected json token") ||
        lower.contains("expected end of the object") ||
        lower.contains("eof") ||
        lower.contains("malformed json")
    ) {
        return "⚠️ The AI returned an incomplete response. Please regenerate or try a different model."
    }

    // 11. Custom URL format errors
    if (lower.contains("custom base url") ||
        lower.contains("malformed url") ||
        lower.contains("invalid url")
    ) {
        return "⚠️ Custom endpoint URL is invalid or missing. Please configure it in Settings."
    }

    // 12. Strip URLs, JSON stack traces, and brackets for any other error
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
