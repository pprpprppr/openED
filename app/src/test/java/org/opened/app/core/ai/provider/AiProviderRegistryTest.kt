package org.opened.app.core.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.ai.AiProvider

class AiProviderRegistryTest {

    private class StubAdapter(override val provider: AiProvider) : AiProviderAdapter {
        override suspend fun generateContent(model: String, systemInstruction: String, prompt: String, isJson: Boolean) = "stub"
        override fun streamGenerateContent(model: String, systemInstruction: String, prompt: String): Flow<String> = emptyFlow()
        override suspend fun testConnection() = ConnectionTestResult(true, "OK")
        override suspend fun fetchLiveModels(): Result<List<String>> = Result.success(listOf("model-1"))
        override fun getCapabilities(model: String) = ModelCapability(model, model)
    }

    @Test
    fun testModelSelectionModesForGemini() {
        val tracker = TokenUsageTracker()
        val registry = AiCapabilityRegistry(
            geminiProvider = StubAdapter(AiProvider.GEMINI),
            anthropicProvider = StubAdapter(AiProvider.CLAUDE),
            openAiProvider = StubAdapter(AiProvider.OPENAI),
            ollamaCompatibleProvider = StubAdapter(AiProvider.CUSTOM),
            tokenUsageTracker = tracker
        )

        // QUALITY mode -> gemini-3.8-flash
        val qualityModel = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.QUALITY, "")
        assertEquals("gemini-3.8-flash", qualityModel)

        // BALANCED mode -> gemini-3.8-flash
        val balancedModel = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.BALANCED, "")
        assertEquals("gemini-3.8-flash", balancedModel)

        // ECONOMY mode -> gemini-3.5-flash-lite
        val economyModel = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.ECONOMY, "")
        assertEquals("gemini-3.5-flash-lite", economyModel)

        // AUTO mode: defaults to gemini-3.8-flash
        val autoCurriculum = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.AUTO, "", AiTaskType.CURRICULUM_SYNTHESIS)
        assertEquals("gemini-3.8-flash", autoCurriculum)

        val autoLesson = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.AUTO, "", AiTaskType.CHAPTER_LESSON)
        assertEquals("gemini-3.8-flash", autoLesson)

        val autoQuiz = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.AUTO, "", AiTaskType.FLASHCARDS_QUIZ)
        assertEquals("gemini-3.8-flash", autoQuiz)

        // MANUAL mode -> uses user's explicit model
        val manual = registry.resolveModel(AiProvider.GEMINI, ModelSelectionMode.MANUAL, "custom-gemini-test")
        assertEquals("custom-gemini-test", manual)
    }

    @Test
    fun testModelSelectionModesForClaudeAndOpenAi() {
        val tracker = TokenUsageTracker()
        val registry = AiCapabilityRegistry(
            geminiProvider = StubAdapter(AiProvider.GEMINI),
            anthropicProvider = StubAdapter(AiProvider.CLAUDE),
            openAiProvider = StubAdapter(AiProvider.OPENAI),
            ollamaCompatibleProvider = StubAdapter(AiProvider.CUSTOM),
            tokenUsageTracker = tracker
        )

        assertEquals("claude-opus-4-6", registry.resolveModel(AiProvider.CLAUDE, ModelSelectionMode.QUALITY, ""))
        assertEquals("claude-sonnet-4-6", registry.resolveModel(AiProvider.CLAUDE, ModelSelectionMode.BALANCED, ""))
        assertEquals("claude-haiku-4-5", registry.resolveModel(AiProvider.CLAUDE, ModelSelectionMode.ECONOMY, ""))

        assertEquals("gpt-4.1", registry.resolveModel(AiProvider.OPENAI, ModelSelectionMode.QUALITY, ""))
        assertEquals("gpt-4o", registry.resolveModel(AiProvider.OPENAI, ModelSelectionMode.BALANCED, ""))
        assertEquals("o3-mini", registry.resolveModel(AiProvider.OPENAI, ModelSelectionMode.ECONOMY, ""))
    }

    @Test
    fun testTokenUsageTrackerAndBudget() {
        val tracker = TokenUsageTracker()
        tracker.monthlyBudgetUsd = 5.0

        assertFalse(tracker.isBudgetExceeded())

        // Record a request: 10,000 input tokens at $1.25/M, 2,000 output tokens at $5.00/M
        // Cost = (10,000 / 1M * 1.25) + (2,000 / 1M * 5.00) = $0.0125 + $0.010 = $0.0225
        tracker.recordUsage(
            providerId = "gemini",
            modelId = "gemini-3.1-pro-preview",
            inputTokens = 10_000,
            outputTokens = 2_000,
            costPerMillionInput = 1.25,
            costPerMillionOutput = 5.00,
            latencyMs = 450L
        )

        assertEquals(10_000L, tracker.getTotalInputTokens())
        assertEquals(2_000L, tracker.getTotalOutputTokens())
        assertEquals(0.0225, tracker.getTotalEstimatedCostUsd(), 0.0001)
        assertFalse(tracker.isBudgetExceeded())

        val diag = tracker.getDiagnostics()
        assertEquals(1, diag.totalRequests)
        assertEquals(1, diag.successfulRequests)
        assertEquals(450L, diag.lastLatencyMs)

        // Exceed budget
        tracker.recordUsage(
            providerId = "openai",
            modelId = "gpt-4.1",
            inputTokens = 500_000,
            outputTokens = 500_000,
            costPerMillionInput = 10.0,
            costPerMillionOutput = 10.0
        )
        assertTrue(tracker.isBudgetExceeded())
    }

    @Test
    fun testBackoffRetryOn429() = runBlocking {
        var callCount = 0
        val result = BackoffRetry.executeWithBackoff(
            maxRetries = 2,
            initialDelayMs = 10L,
            maxDelayMs = 50L
        ) { attempt ->
            callCount++
            if (attempt == 0) {
                throw RateLimitException("Simulated rate limit 429", retryAfterSeconds = 0)
            }
            "Success on attempt $attempt"
        }

        assertEquals("Success on attempt 1", result)
        assertEquals(2, callCount)
    }
}
