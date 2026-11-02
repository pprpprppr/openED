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
import org.opened.app.core.ai.MissingApiKeyException
import org.opened.app.core.security.SecureApiKeyStorage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val tokenUsageTracker: TokenUsageTracker
) : AiProviderAdapter {

    override val provider: AiProvider = AiProvider.OPENAI

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
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
            ?: throw MissingApiKeyException("OpenAI API Key is missing. Please configure it in Settings.")

        val capabilities = getCapabilities(model)

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", systemInstruction)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestPayload = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 8192)
            if (isJson) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }

        try {
            val responseText = BackoffRetry.executeWithBackoff {
                val httpRequest = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("content-type", "application/json")
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))
                    .build()

                okHttpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    BackoffRetry.checkRateLimitOrUnavailable(response, responseBody)
                    if (!response.isSuccessful) {
                        throw IOException("OpenAI API error (${response.code}): $responseBody")
                    }
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val choices = parsed["choices"]?.jsonArray
                    val content = choices?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content.orEmpty()

                    val usage = parsed["usage"]?.jsonObject
                    val inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                        ?: ((prompt.length + systemInstruction.length) / 4)
                    val outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                        ?: (content.length / 4)

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

                    content
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
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
            ?: throw MissingApiKeyException("OpenAI API Key is missing. Please configure it in Settings.")

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", systemInstruction)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestPayload = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 8192)
            put("stream", true)
        }

        val httpRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", "application/json")
            .post(requestPayload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            response.close()
            BackoffRetry.checkRateLimitOrUnavailable(response, errorBody)
            throw IOException("OpenAI SSE stream error (${response.code}): $errorBody")
        }

        val body = response.body ?: throw IOException("Empty response body from OpenAI endpoint")
        body.byteStream().use { inputStream ->
            SseEventParser.processStream(
                inputStream = inputStream,
                extractDelta = { payload -> SseEventParser.extractOpenAiDelta(payload) },
                onChunk = { chunk -> emit(chunk) }
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
        if (apiKey.isNullOrBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "Missing OpenAI API Key. Please configure your key in Settings.",
                error = "NO_KEY"
            )
        }

        try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ConnectionTestResult(
                        isSuccess = false,
                        message = "OpenAI connection failed (${response.code})",
                        latencyMs = latency,
                        error = body
                    )
                }
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val count = jsonElement["data"]?.jsonArray?.size ?: 0
                ConnectionTestResult(
                    isSuccess = true,
                    message = "Connected to OpenAI successfully ($count models)",
                    latencyMs = latency,
                    availableModelsCount = count
                )
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Failed to connect to OpenAI: ${e.message}",
                latencyMs = System.currentTimeMillis() - startTime,
                error = e.localizedMessage
            )
        }
    }

    override suspend fun fetchLiveModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
                ?: throw MissingApiKeyException("OpenAI API key is required to sync live models.")

            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("OpenAI API error (${response.code}): $body")
                }
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val dataArray = jsonElement["data"]?.jsonArray.orEmpty()
                dataArray.mapNotNull { item ->
                    val id = item.jsonObject["id"]?.jsonPrimitive?.content
                    if (!id.isNullOrBlank() && !id.contains("embedding") && !id.contains("whisper") && !id.contains("tts") && !id.contains("dall-e")) {
                        id
                    } else null
                }
            }
        }
    }

    override fun getCapabilities(model: String): ModelCapability {
        return when {
            model.contains("o3") || model.contains("o1") -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = true,
                contextWindowTokens = 200_000,
                inputCostPerMillion = 1.10,
                outputCostPerMillion = 4.40,
                tier = ModelTier.QUALITY
            )
            model.contains("4.1") || model.contains("4-turbo") -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 128_000,
                inputCostPerMillion = 2.50,
                outputCostPerMillion = 10.00,
                tier = ModelTier.QUALITY
            )
            model.contains("mini") -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 128_000,
                inputCostPerMillion = 0.15,
                outputCostPerMillion = 0.60,
                tier = ModelTier.ECONOMY
            )
            else -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 128_000,
                inputCostPerMillion = 2.50,
                outputCostPerMillion = 10.00,
                tier = ModelTier.BALANCED
            )
        }
    }
}
