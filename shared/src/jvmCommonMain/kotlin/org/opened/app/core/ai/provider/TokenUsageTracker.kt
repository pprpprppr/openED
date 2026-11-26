package org.opened.app.core.ai.provider

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class TokenUsageRecord(
    val providerId: String,
    val modelId: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProviderDiagnostics(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalEstimatedCostUsd: Double,
    val lastLatencyMs: Long,
    val rateLimitCount: Int
)

@Singleton
class TokenUsageTracker @Inject constructor() {
    private val records = ConcurrentLinkedQueue<TokenUsageRecord>()
    private val totalRequests = AtomicInteger(0)
    private val successfulRequests = AtomicInteger(0)
    private val failedRequests = AtomicInteger(0)
    private val rateLimitHits = AtomicInteger(0)
    private val lastLatency = AtomicLong(0L)

    // User-configurable monthly budget in USD (0.0 means unlimited)
    @Volatile
    var monthlyBudgetUsd: Double = 0.0

    fun recordUsage(
        providerId: String,
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        costPerMillionInput: Double = 0.0,
        costPerMillionOutput: Double = 0.0,
        latencyMs: Long = 0L
    ) {
        val cost = (inputTokens.toDouble() / 1_000_000.0 * costPerMillionInput) +
                (outputTokens.toDouble() / 1_000_000.0 * costPerMillionOutput)

        records.add(
            TokenUsageRecord(
                providerId = providerId,
                modelId = modelId,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                estimatedCostUsd = cost
            )
        )
        totalRequests.incrementAndGet()
        successfulRequests.incrementAndGet()
        lastLatency.set(latencyMs)
    }

    fun recordFailure(isRateLimit: Boolean = false) {
        totalRequests.incrementAndGet()
        failedRequests.incrementAndGet()
        if (isRateLimit) {
            rateLimitHits.incrementAndGet()
        }
    }

    fun getTotalInputTokens(): Long = records.sumOf { it.inputTokens.toLong() }
    fun getTotalOutputTokens(): Long = records.sumOf { it.outputTokens.toLong() }
    fun getTotalEstimatedCostUsd(): Double = records.sumOf { it.estimatedCostUsd }

    fun isBudgetExceeded(): Boolean {
        if (monthlyBudgetUsd <= 0.0) return false
        return getTotalEstimatedCostUsd() >= monthlyBudgetUsd
    }

    fun getDiagnostics(): ProviderDiagnostics {
        return ProviderDiagnostics(
            totalRequests = totalRequests.get(),
            successfulRequests = successfulRequests.get(),
            failedRequests = failedRequests.get(),
            totalInputTokens = getTotalInputTokens(),
            totalOutputTokens = getTotalOutputTokens(),
            totalEstimatedCostUsd = getTotalEstimatedCostUsd(),
            lastLatencyMs = lastLatency.get(),
            rateLimitCount = rateLimitHits.get()
        )
    }

    fun clear() {
        records.clear()
        totalRequests.set(0)
        successfulRequests.set(0)
        failedRequests.set(0)
        rateLimitHits.set(0)
        lastLatency.set(0L)
    }
}
