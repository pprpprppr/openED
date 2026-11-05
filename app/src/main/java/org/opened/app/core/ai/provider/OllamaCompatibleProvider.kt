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
import org.opened.app.core.security.SecureApiKeyStorage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OllamaCompatibleProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val tokenUsageTracker: TokenUsageTracker
) : AiProviderAdapter {

    override val provider: AiProvider = AiProvider.CUSTOM

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getBaseUrl(): String {
        return SecureApiKeyStorage.sanitizeCustomBaseUrl(secureApiKeyStorage.getCustomBaseUrl())
    }

    override suspend fun generateContent(
        model: String,
        systemInstruction: String,
        prompt: String,
        isJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()
        val url = "${getBaseUrl()}/chat/completions"

        val effectiveSystem = if (isJson) {
            "$systemInstruction\n\nCRITICAL: Respond ONLY with a valid JSON object. No preamble, no markdown backticks."
        } else {
            systemInstruction
        }

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", effectiveSystem)
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

        val requestBuilder = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .header("HTTP-Referer", "https://github.com/openED")
            .header("X-Title", "openED")
            .post(requestPayload.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            val responseText = BackoffRetry.executeWithBackoff {
                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    BackoffRetry.checkRateLimitOrUnavailable(response, responseBody)
                    if (!response.isSuccessful) {
                        throw IOException("Ollama/Custom API error (${response.code}): $responseBody")
                    }
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val choices = parsed["choices"]?.jsonArray
                    val content = choices?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content.orEmpty()

                    val latency = System.currentTimeMillis() - startTime
                    val approxIn = (prompt.length + systemInstruction.length) / 4
                    val approxOut = content.length / 4

                    tokenUsageTracker.recordUsage(
                        providerId = provider.id,
                        modelId = model,
                        inputTokens = approxIn,
                        outputTokens = approxOut,
                        costPerMillionInput = 0.0,
                        costPerMillionOutput = 0.0,
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
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()
        val url = "${getBaseUrl()}/chat/completions"

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

        val requestBuilder = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .header("HTTP-Referer", "https://github.com/openED")
            .header("X-Title", "openED")
            .post(requestPayload.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            response.close()
            BackoffRetry.checkRateLimitOrUnavailable(response, errorBody)
            throw IOException("Ollama/Custom SSE stream error (${response.code}): $errorBody")
        }

        val body = response.body ?: throw IOException("Empty response body from local endpoint")
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
        val baseUrl = getBaseUrl()
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()

        try {
            val url = "$baseUrl/models"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("HTTP-Referer", "https://github.com/openED")
                .header("X-Title", "openED")
                .get()
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ConnectionTestResult(
                        isSuccess = false,
                        message = "Connection to local endpoint failed (${response.code})",
                        latencyMs = latency,
                        error = body
                    )
                }
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val count = jsonElement["data"]?.jsonArray?.size ?: 0
                ConnectionTestResult(
                    isSuccess = true,
                    message = "Connected to local Ollama / endpoint successfully ($count models)",
                    latencyMs = latency,
                    availableModelsCount = count
                )
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Unable to connect to $baseUrl: ${e.message}",
                latencyMs = System.currentTimeMillis() - startTime,
                error = e.localizedMessage
            )
        }
    }

    override suspend fun fetchLiveModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = getBaseUrl()
            val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()
            val url = "$baseUrl/models"

            val reqBuilder = Request.Builder()
                .url(url)
                .header("HTTP-Referer", "https://github.com/openED")
                .header("X-Title", "openED")
                .get()
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Custom endpoint API error (${response.code}): $body")
                }
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val dataArray = jsonElement["data"]?.jsonArray.orEmpty()
                dataArray.mapNotNull { item ->
                    val id = item.jsonObject["id"]?.jsonPrimitive?.content
                    if (!id.isNullOrBlank() && !id.contains("embedding") && !id.contains("whisper")) {
                        id
                    } else null
                }
            }
        }
    }

    override fun getCapabilities(model: String): ModelCapability {
        return ModelCapability(
            modelId = model,
            displayName = model,
            supportsJson = true,
            supportsStreaming = true,
            supportsVision = false,
            isReasoning = false,
            contextWindowTokens = 32_000,
            inputCostPerMillion = 0.0,
            outputCostPerMillion = 0.0,
            tier = ModelTier.BALANCED
        )
    }
}
