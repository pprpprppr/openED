package org.opened.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.opened.app.core.ai.provider.AiCapabilityRegistry
import org.opened.app.core.ai.provider.AiTaskType
import org.opened.app.core.ai.provider.ConnectionTestResult
import org.opened.app.core.ai.provider.ModelSelectionMode
import org.opened.app.core.ai.provider.ProviderDiagnostics
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.security.SecureApiKeyStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiAiClient @Inject constructor(
    val capabilityRegistry: AiCapabilityRegistry,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val preferencesRepository: UserPreferencesRepository
) {

    suspend fun generateContent(
        systemInstruction: String,
        prompt: String,
        isJson: Boolean = false,
        taskType: AiTaskType = AiTaskType.GENERAL
    ): String = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val provider = AiProvider.fromId(prefs.selectedProvider)
        val selectionMode = ModelSelectionMode.fromId(prefs.selectionMode)

        val rawSavedModel = secureApiKeyStorage.getModelForProvider(provider).ifBlank {
            prefs.selectedModel.ifBlank { provider.defaultModel }
        }

        val effectiveModel = capabilityRegistry.resolveModel(
            provider = provider,
            mode = selectionMode,
            userSelectedModel = rawSavedModel,
            taskType = taskType
        )

        val adapter = capabilityRegistry.getAdapter(provider)
        adapter.generateContent(
            model = effectiveModel,
            systemInstruction = systemInstruction,
            prompt = prompt,
            isJson = isJson
        )
    }

    fun streamGenerateContent(
        systemInstruction: String,
        prompt: String,
        taskType: AiTaskType = AiTaskType.CHAPTER_LESSON
    ): Flow<String> = flow {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val provider = AiProvider.fromId(prefs.selectedProvider)
        val selectionMode = ModelSelectionMode.fromId(prefs.selectionMode)

        val rawSavedModel = secureApiKeyStorage.getModelForProvider(provider).ifBlank {
            prefs.selectedModel.ifBlank { provider.defaultModel }
        }

        val effectiveModel = capabilityRegistry.resolveModel(
            provider = provider,
            mode = selectionMode,
            userSelectedModel = rawSavedModel,
            taskType = taskType
        )

        val adapter = capabilityRegistry.getAdapter(provider)
        adapter.streamGenerateContent(
            model = effectiveModel,
            systemInstruction = systemInstruction,
            prompt = prompt
        ).collect { chunk ->
            emit(chunk)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Tests connection and credentials for the specified provider.
     */
    suspend fun testConnection(provider: AiProvider): ConnectionTestResult {
        return capabilityRegistry.testConnection(provider)
    }

    /**
     * Fetches live available models from the provider's API.
     */
    suspend fun fetchLiveModels(provider: AiProvider): Result<List<String>> {
        return capabilityRegistry.fetchLiveModels(provider)
    }

    /**
     * Retrieves token usage and error diagnostics across all provider calls.
     */
    fun getDiagnostics(): ProviderDiagnostics {
        return capabilityRegistry.tokenUsageTracker.getDiagnostics()
    }
}
