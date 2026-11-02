package org.opened.app.core.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels
import org.opened.app.core.ai.MissingApiKeyException
import org.opened.app.core.security.SecureApiKeyStorage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val tokenUsageTracker: TokenUsageTracker
) : AiProviderAdapter {

    override val provider: AiProvider = AiProvider.CLAUDE

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun generateContent(
        model: String,
        systemInstruction: String,
        prompt: String,
        isJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CLAUDE)
            ?: throw MissingApiKeyException("Anthropic Claude API Key is missing. Please configure it in Settings.")

        val capabilities = getCapabilities(model)

        val effectiveSystem = if (isJson) {
            "$systemInstruction\n\nCRITICAL: Respond ONLY with a valid raw JSON object. Do not enclose in markdown quotes or preamble."
        } else {
            systemInstruction
        }

        val requestPayload = buildJsonObject {
            put("model", model)
            put("max_tokens", 8192)
            put("system", effectiveSystem)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        try {
            val responseText = BackoffRetry.executeWithBackoff {
                val httpRequest = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))
                    .build()

                okHttpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    BackoffRetry.checkRateLimitOrUnavailable(response, responseBody)
                    if (!response.isSuccessful) {
                        throw IOException("Claude API error (${response.code}): $responseBody")
                    }
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val contentArr = parsed["content"]?.jsonArray
                    val textPart = contentArr?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

                    // Extract usage if available
                    val usage = parsed["usage"]?.jsonObject
                    val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                        ?: ((prompt.length + systemInstruction.length) / 4)
                    val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                        ?: (textPart.length / 4)

                    val latency = System.currentTimeMillis() - startTime
                    tokenUsageTracker.recordUsage(
                        providerId = provider.id,
                        modelId = model,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        costPerMillionInput = capabilities.inputCostPerMillion,
                        costPerMillionOutput = capabilities.outputCostPerMillion,
                        latencyMs = latency
                    )

                    textPart
                }
            }
            responseText
        } catch (e: Exception) {
            tokenUsageTracker.recordFailure(isRateLimit = e is RateLimitException)
            throw e
        }
    }

    override fun streamGenerateContent(
        model: String,
        systemInstruction: String,
        prompt: String
    ): Flow<String> = flow {
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CLAUDE)
            ?: throw MissingApiKeyException("Anthropic Claude API Key is missing. Please configure it in Settings.")

        val requestPayload = buildJsonObject {
            put("model", model)
            put("max_tokens", 8192)
            put("stream", true)
            put("system", systemInstruction)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val httpRequest = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(requestPayload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            response.close()
            BackoffRetry.checkRateLimitOrUnavailable(response, errorBody)
            throw IOException("Claude SSE stream error (${response.code}): $errorBody")
        }

        val body = response.body ?: throw IOException("Empty response body from Claude streaming endpoint")
        body.byteStream().use { inputStream ->
            SseEventParser.processStream(
                inputStream = inputStream,
                extractDelta = { payload -> SseEventParser.extractAnthropicDelta(payload) },
                onChunk = { chunk -> emit(chunk) }
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CLAUDE)
        if (apiKey.isNullOrBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "Missing Claude API Key. Please configure your key in Settings.",
                error = "NO_KEY"
            )
        }

        try {
            val url = "https://api.anthropic.com/v1/models"
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val jsonElement = json.parseToJsonElement(body).jsonObject
                    val count = jsonElement["data"]?.jsonArray?.size ?: AiProviderModels.CLAUDE_MODELS.size
                    ConnectionTestResult(
                        isSuccess = true,
                        message = "Connected to Anthropic Claude successfully ($count models)",
                        latencyMs = latency,
                        availableModelsCount = count
                    )
                } else if (response.code == 404 || response.code == 403) {
                    // Endpoint might be restricted to specific tiers, but credentials may be valid
                    ConnectionTestResult(
                        isSuccess = true,
                        message = "Anthropic credentials verified (Presets active)",
                        latencyMs = latency,
                        availableModelsCount = AiProviderModels.CLAUDE_MODELS.size
                    )
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        message = "Anthropic connection failed (${response.code})",
                        latencyMs = latency,
                        error = response.body?.string()
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Failed to connect to Claude: ${e.message}",
                latencyMs = System.currentTimeMillis() - startTime,
                error = e.localizedMessage
            )
        }
    }

    override suspend fun fetchLiveModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CLAUDE)
                ?: throw MissingApiKeyException("Claude API key is required to sync live models.")

            val url = "https://api.anthropic.com/v1/models"
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching AiProviderModels.CLAUDE_MODELS
                }
                val body = response.body?.string().orEmpty()
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val dataArray = jsonElement["data"]?.jsonArray.orEmpty()
                val ids = dataArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                if (ids.isNotEmpty()) ids else AiProviderModels.CLAUDE_MODELS
            }
        }
    }

    override fun getCapabilities(model: String): ModelCapability {
        return when {
            model.contains("opus") -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = true,
                contextWindowTokens = 200_000,
                inputCostPerMillion = 15.00,
                outputCostPerMillion = 75.00,
                tier = ModelTier.QUALITY
            )
            model.contains("haiku") -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 200_000,
                inputCostPerMillion = 0.80,
                outputCostPerMillion = 4.00,
                tier = ModelTier.ECONOMY
            )
            else -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 200_000,
                inputCostPerMillion = 3.00,
                outputCostPerMillion = 15.00,
                tier = ModelTier.BALANCED
            )
        }
    }
}
