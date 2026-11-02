package org.opened.app.core.ai.provider

import kotlinx.coroutines.flow.Flow
import org.opened.app.core.ai.AiProvider

/**
 * Universal adapter interface for AI providers in openED.
 * Encapsulates provider-specific HTTP headers, payloads, SSE streaming,
 * connection diagnostics, and model capabilities.
 */
interface AiProviderAdapter {
    val provider: AiProvider

    /**
     * Executes a non-streaming completion request.
     */
    suspend fun generateContent(
        model: String,
        systemInstruction: String,
        prompt: String,
        isJson: Boolean = false
    ): String

    /**
     * Executes a streaming SSE completion request.
     */
    fun streamGenerateContent(
        model: String,
        systemInstruction: String,
        prompt: String
    ): Flow<String>

    /**
     * Tests connectivity to the provider endpoint and validates credentials.
     */
    suspend fun testConnection(): ConnectionTestResult

    /**
     * Fetches live available models from the provider's remote/local catalog.
     */
    suspend fun fetchLiveModels(): Result<List<String>>

    /**
     * Returns known capabilities for a specific model ID under this provider.
     */
    fun getCapabilities(model: String): ModelCapability
}
