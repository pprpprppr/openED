package org.opened.app.core.ai.provider

enum class ModelTier {
    QUALITY,
    BALANCED,
    ECONOMY
}

data class ModelCapability(
    val modelId: String,
    val displayName: String,
    val supportsJson: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val isReasoning: Boolean = false,
    val contextWindowTokens: Int = 128_000,
    val inputCostPerMillion: Double = 0.0,
    val outputCostPerMillion: Double = 0.0,
    val tier: ModelTier = ModelTier.BALANCED
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val availableModelsCount: Int = 0,
    val error: String? = null
)
