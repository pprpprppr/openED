package org.opened.app.core.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.GeminiApiClient
import org.opened.app.core.ai.GeminiContent
import org.opened.app.core.ai.GeminiGenerateRequest
import org.opened.app.core.ai.GeminiGenerationConfig
import org.opened.app.core.ai.GeminiPart
import org.opened.app.core.ai.MissingApiKeyException
import org.opened.app.core.security.SecureApiKeyStorage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiProvider @Inject constructor(
    private val geminiApiClient: GeminiApiClient,
    private val okHttpClient: OkHttpClient,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val tokenUsageTracker: TokenUsageTracker
) : AiProviderAdapter {

    override val provider: AiProvider = AiProvider.GEMINI

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        coerceInputValues = true
    }

    override suspend fun generateContent(
        model: String,
        systemInstruction: String,
        prompt: String,
        isJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val capabilities = getCapabilities(model)

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemInstruction))
            ),
            generationConfig = if (isJson) {
                GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.4f, maxOutputTokens = 8192)
            } else {
                GeminiGenerationConfig(temperature = 0.5f, maxOutputTokens = 8192)
            }
        )

        try {
            val result = BackoffRetry.executeWithBackoff {
                geminiApiClient.generateContent(model, request)
            }
            val latency = System.currentTimeMillis() - startTime
            val approxInputTokens = (prompt.length + systemInstruction.length) / 4
            val approxOutputTokens = result.length / 4

            tokenUsageTracker.recordUsage(
                providerId = provider.id,
                modelId = model,
                inputTokens = approxInputTokens,
                outputTokens = approxOutputTokens,
                costPerMillionInput = capabilities.inputCostPerMillion,
                costPerMillionOutput = capabilities.outputCostPerMillion,
                latencyMs = latency
            )
            result
        } catch (e: Exception) {
            tokenUsageTracker.recordFailure(isRateLimit = e is RateLimitException)
            throw e
        }
    }

    override fun streamGenerateContent(
        model: String,
        systemInstruction: String,
        prompt: String
    ): Flow<String> {
        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemInstruction))
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.5f, maxOutputTokens = 8192)
        )
        return geminiApiClient.streamGenerateContent(model, request).flowOn(Dispatchers.IO)
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.GEMINI)
        if (apiKey.isNullOrBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "Missing Gemini API Key. Please configure your key in Settings.",
                error = "NO_KEY"
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ConnectionTestResult(
                        isSuccess = false,
                        message = "Gemini authentication failed (${response.code})",
                        latencyMs = latency,
                        error = body
                    )
                }
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val count = jsonElement["models"]?.jsonArray?.size ?: 0
                ConnectionTestResult(
                    isSuccess = true,
                    message = "Connected to Google Gemini successfully ($count models available)",
                    latencyMs = latency,
                    availableModelsCount = count
                )
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Failed to connect to Gemini: ${e.message}",
                latencyMs = System.currentTimeMillis() - startTime,
                error = e.localizedMessage
            )
        }
    }

    override suspend fun fetchLiveModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.GEMINI)
                ?: throw MissingApiKeyException("Gemini API key is required to sync live models.")

            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Gemini API error (${response.code}): $body")
                }
                val jsonElement = json.parseToJsonElement(body).jsonObject
                val modelsArray = jsonElement["models"]?.jsonArray.orEmpty()
                modelsArray.mapNotNull { modelItem ->
                    val obj = modelItem.jsonObject
                    val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull {
                        it.jsonPrimitive.content
                    }.orEmpty()
                    if (methods.contains("generateContent")) {
                        val name = obj["name"]?.jsonPrimitive?.content?.removePrefix("models/").orEmpty()
                        if (name.isNotBlank() && !SecureApiKeyStorage.isDeprecatedGeminiModel(name)) {
                            name
                        } else null
                    } else null
                }
            }
        }
    }

    override fun getCapabilities(model: String): ModelCapability {
        return when (model) {
            "gemini-3.1-pro-preview" -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = true,
                contextWindowTokens = 2_000_000,
                inputCostPerMillion = 1.25,
                outputCostPerMillion = 5.00,
                tier = ModelTier.QUALITY
            )
            "gemini-3.5-flash-lite", "gemini-3.1-flash-lite" -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 1_000_000,
                inputCostPerMillion = 0.0375,
                outputCostPerMillion = 0.15,
                tier = ModelTier.ECONOMY
            )
            else -> ModelCapability(
                modelId = model,
                displayName = model,
                supportsJson = true,
                supportsStreaming = true,
                supportsVision = true,
                isReasoning = false,
                contextWindowTokens = 1_000_000,
                inputCostPerMillion = 0.075,
                outputCostPerMillion = 0.30,
                tier = ModelTier.BALANCED
            )
        }
    }
}
