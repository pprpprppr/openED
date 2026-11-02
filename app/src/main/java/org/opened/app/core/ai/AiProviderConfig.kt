package org.opened.app.core.ai

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultModel = "gemini-3.8-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta"
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        defaultModel = "claude-sonnet-4-6",
        defaultBaseUrl = "https://api.anthropic.com/v1/messages"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI GPT",
        defaultModel = "gpt-4.1",
        defaultBaseUrl = "https://api.openai.com/v1/chat/completions"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom (OpenAI-Compatible)",
        defaultModel = "default",
        defaultBaseUrl = "http://localhost:11434/v1"
    );

    companion object {
        fun fromId(id: String?): AiProvider {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: GEMINI
        }
    }
}

object AiProviderModels {
    val GEMINI_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.1-pro-preview",
        "gemini-3.5-flash-lite",
        "gemini-2.5-pro",
        "gemini-2.5-flash"
    )

    val CLAUDE_MODELS = listOf(
        "claude-sonnet-4-6",
        "claude-opus-4-6",
        "claude-haiku-4-5"
    )

    val OPENAI_MODELS = listOf(
        "gpt-4.1",
        "gpt-4o",
        "o3-mini"
    )

    fun getModelsForProvider(provider: AiProvider): List<String> {
        return when (provider) {
            AiProvider.GEMINI -> GEMINI_MODELS
            AiProvider.CLAUDE -> CLAUDE_MODELS
            AiProvider.OPENAI -> OPENAI_MODELS
            AiProvider.CUSTOM -> emptyList()
        }
    }

    fun getModelDescription(model: String): String {
        return when (model) {
            "gemini-3.8-flash" -> "Latest flagship Flash — high-speed reasoning & agentic workflows"
            "gemini-3.6-flash" -> "Balanced multimodal & fast course generation"
            "gemini-3.1-pro-preview" -> "Deep reasoning, complex math & architecture"
            "gemini-3.5-flash-lite" -> "Ultra-fast, low-cost high-throughput generation"
            "gemini-2.5-pro" -> "Maximum context & mathematical rigor"
            "gemini-2.5-flash" -> "Fast low-latency engine"
            "claude-sonnet-4-6" -> "State-of-the-art coding, analysis, and nuanced reasoning"
            "claude-opus-4-6" -> "Deep academic synthesis and advanced math derivations"
            "claude-haiku-4-5" -> "Ultra-fast response streaming and lightweight Q&A"
            "gpt-4.1" -> "Advanced reasoning, coding, and curriculum structuring"
            "gpt-4o" -> "Flagship multimodal intelligence and structured output"
            "o3-mini" -> "Deep reasoning model for STEM and mathematical proofs"
            else -> ""
        }
    }
}
