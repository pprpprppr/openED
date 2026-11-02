package org.opened.app.core.ai.provider

import org.opened.app.core.ai.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

enum class AiTaskType {
    CURRICULUM_SYNTHESIS,
    CHAPTER_LESSON,
    FLASHCARDS_QUIZ,
    REFINEMENT_QA,
    GENERAL
}

@Singleton
class AiCapabilityRegistry(
    private val geminiProvider: AiProviderAdapter?,
    private val anthropicProvider: AiProviderAdapter?,
    private val openAiProvider: AiProviderAdapter?,
    private val ollamaCompatibleProvider: AiProviderAdapter?,
    val tokenUsageTracker: TokenUsageTracker
) {

    @Inject
    constructor(
        geminiProvider: GeminiProvider,
        anthropicProvider: AnthropicProvider,
        openAiProvider: OpenAiProvider,
        ollamaCompatibleProvider: OllamaCompatibleProvider,
        tokenUsageTracker: TokenUsageTracker
    ) : this(
        geminiProvider as AiProviderAdapter,
        anthropicProvider as AiProviderAdapter,
        openAiProvider as AiProviderAdapter,
        ollamaCompatibleProvider as AiProviderAdapter,
        tokenUsageTracker
    )

    fun getAdapter(provider: AiProvider): AiProviderAdapter {
        return when (provider) {
            AiProvider.GEMINI -> requireNotNull(geminiProvider) { "Gemini provider adapter not initialized" }
            AiProvider.CLAUDE -> requireNotNull(anthropicProvider) { "Claude provider adapter not initialized" }
            AiProvider.OPENAI -> requireNotNull(openAiProvider) { "OpenAI provider adapter not initialized" }
            AiProvider.CUSTOM -> requireNotNull(ollamaCompatibleProvider) { "Custom provider adapter not initialized" }
        }
    }

    /**
     * Resolves the target model for a given provider, selection mode, and task type.
     */
    fun resolveModel(
        provider: AiProvider,
        mode: ModelSelectionMode,
        userSelectedModel: String,
        taskType: AiTaskType = AiTaskType.GENERAL
    ): String {
        if (mode == ModelSelectionMode.MANUAL) {
            return userSelectedModel.ifBlank { provider.defaultModel }
        }

        val effectiveTier = when (mode) {
            ModelSelectionMode.QUALITY -> ModelTier.QUALITY
            ModelSelectionMode.BALANCED -> ModelTier.BALANCED
            ModelSelectionMode.ECONOMY -> ModelTier.ECONOMY
            ModelSelectionMode.AUTO -> {
                when (taskType) {
                    AiTaskType.CURRICULUM_SYNTHESIS -> ModelTier.QUALITY
                    AiTaskType.FLASHCARDS_QUIZ -> ModelTier.ECONOMY
                    AiTaskType.CHAPTER_LESSON -> ModelTier.BALANCED
                    AiTaskType.REFINEMENT_QA -> ModelTier.BALANCED
                    AiTaskType.GENERAL -> ModelTier.BALANCED
                }
            }
            ModelSelectionMode.MANUAL -> ModelTier.BALANCED
        }

        return getModelForTier(provider, effectiveTier, userSelectedModel)
    }

    private fun getModelForTier(provider: AiProvider, tier: ModelTier, fallbackModel: String): String {
        return when (provider) {
            AiProvider.GEMINI -> when (tier) {
                ModelTier.QUALITY -> "gemini-3.1-pro-preview"
                ModelTier.BALANCED -> "gemini-3.8-flash"
                ModelTier.ECONOMY -> "gemini-3.5-flash-lite"
            }
            AiProvider.CLAUDE -> when (tier) {
                ModelTier.QUALITY -> "claude-opus-4-6"
                ModelTier.BALANCED -> "claude-sonnet-4-6"
                ModelTier.ECONOMY -> "claude-haiku-4-5"
            }
            AiProvider.OPENAI -> when (tier) {
                ModelTier.QUALITY -> "gpt-4.1"
                ModelTier.BALANCED -> "gpt-4o"
                ModelTier.ECONOMY -> "o3-mini"
            }
            AiProvider.CUSTOM -> fallbackModel.ifBlank { provider.defaultModel }
        }
    }

    suspend fun testConnection(provider: AiProvider): ConnectionTestResult {
        return getAdapter(provider).testConnection()
    }

    suspend fun fetchLiveModels(provider: AiProvider): Result<List<String>> {
        return getAdapter(provider).fetchLiveModels()
    }

    fun getCapabilities(provider: AiProvider, model: String): ModelCapability {
        return getAdapter(provider).getCapabilities(model)
    }
}
