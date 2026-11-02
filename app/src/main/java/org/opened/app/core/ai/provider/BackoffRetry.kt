package org.opened.app.core.ai.provider

import kotlinx.coroutines.delay
import okhttp3.Response
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

object BackoffRetry {

    private const val DEFAULT_MAX_RETRIES = 3
    private const val INITIAL_DELAY_MS = 1000L
    private const val MAX_DELAY_MS = 16000L

    /**
     * Executes an operation with exponential backoff for HTTP 429 (Rate Limit) and HTTP 503 (Unavailable).
     */
    suspend fun <T> executeWithBackoff(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = INITIAL_DELAY_MS,
        maxDelayMs: Long = MAX_DELAY_MS,
        onRetry: ((attempt: Int, delayMs: Long, reason: String) -> Unit)? = null,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                return block(attempt)
            } catch (e: RateLimitException) {
                lastException = e
                if (attempt == maxRetries) throw e

                val retryAfterDelay = e.retryAfterSeconds?.let { it * 1000L } ?: currentDelay
                val jitter = Random.nextLong(0, 300)
                val effectiveDelay = min(retryAfterDelay + jitter, maxDelayMs)

                onRetry?.invoke(attempt + 1, effectiveDelay, e.message ?: "Rate limit")
                delay(effectiveDelay)
                currentDelay = min(currentDelay * 2, maxDelayMs)
            } catch (e: ServerUnavailableException) {
                lastException = e
                if (attempt == maxRetries) throw e

                val jitter = Random.nextLong(0, 300)
                val effectiveDelay = min(currentDelay + jitter, maxDelayMs)

                onRetry?.invoke(attempt + 1, effectiveDelay, e.message ?: "Server unavailable")
                delay(effectiveDelay)
                currentDelay = min(currentDelay * 2, maxDelayMs)
            }
        }

        throw lastException ?: IOException("Execution failed after $maxRetries retries")
    }

    /**
     * Inspects an OkHttp response and throws RateLimitException if HTTP 429,
     * or ServerUnavailableException if HTTP 503.
     */
    fun checkRateLimitOrUnavailable(response: Response, errorBody: String) {
        if (response.code == 429) {
            val retryAfterHeader = response.header("Retry-After")?.toLongOrNull()
            throw RateLimitException(
                message = "Rate limit reached (HTTP 429): $errorBody",
                retryAfterSeconds = retryAfterHeader
            )
        } else if (response.code == 503) {
            throw ServerUnavailableException("Service temporarily unavailable (HTTP 503): $errorBody")
        }
    }
}

class RateLimitException(
    message: String,
    val retryAfterSeconds: Long? = null
) : IOException(message)

class ServerUnavailableException(
    message: String
) : IOException(message)
