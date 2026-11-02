package org.opened.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.security.SecureApiKeyStorage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiAiClient @Inject constructor(
    private val geminiApiClient: GeminiApiClient,
    private val okHttpClient: OkHttpClient,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateContent(
        systemInstruction: String,
        prompt: String,
        isJson: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val provider = AiProvider.fromId(prefs.selectedProvider)
        val model = secureApiKeyStorage.getModelForProvider(provider).ifBlank {
            prefs.selectedModel.ifBlank { provider.defaultModel }
        }

        when (provider) {
            AiProvider.GEMINI -> {
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
                geminiApiClient.generateContent(model, request)
            }

            AiProvider.CLAUDE -> {
                val apiKey = secureApiKeyStorage.getApiKeyForProvider(AiProvider.CLAUDE)
                    ?: throw MissingApiKeyException("Anthropic Claude API Key is missing. Please configure it in Settings.")

                val requestPayload = buildJsonObject {
                    put("model", model)
                    put("max_tokens", 8192)
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

                okHttpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Claude API error (${response.code}): $responseBody")
                    }
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val contentArr = parsed["content"]?.jsonArray
                    val textPart = contentArr?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                    textPart
                }
            }

            AiProvider.OPENAI, AiProvider.CUSTOM -> {
                val isCustom = (provider == AiProvider.CUSTOM)
                val apiKey = if (isCustom) {
                    secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()
                } else {
                    secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
                        ?: throw MissingApiKeyException("OpenAI API Key is missing. Please configure it in Settings.")
                }

                val baseUrl = if (isCustom) {
                    secureApiKeyStorage.getCustomBaseUrl().trimEnd('/')
                } else {
                    "https://api.openai.com/v1"
                }

                val url = "$baseUrl/chat/completions"

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
                    if (isJson && !isCustom) {
                        put("response_format", buildJsonObject { put("type", "json_object") })
                    }
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("content-type", "application/json")
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))

                if (apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("${provider.displayName} API error (${response.code}): $responseBody")
                    }
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val choices = parsed["choices"]?.jsonArray
                    val content = choices?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content.orEmpty()
                    content
                }
            }
        }
    }

    fun streamGenerateContent(
        systemInstruction: String,
        prompt: String
    ): Flow<String> = flow {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val provider = AiProvider.fromId(prefs.selectedProvider)
        val model = secureApiKeyStorage.getModelForProvider(provider).ifBlank {
            prefs.selectedModel.ifBlank { provider.defaultModel }
        }

        when (provider) {
            AiProvider.GEMINI -> {
                val request = GeminiGenerateRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    ),
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = systemInstruction))
                    ),
                    generationConfig = GeminiGenerationConfig(temperature = 0.5f, maxOutputTokens = 8192)
                )
                geminiApiClient.streamGenerateContent(model, request).collect { chunk ->
                    emit(chunk)
                }
            }

            AiProvider.CLAUDE -> {
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
                    throw IOException("Claude SSE stream error (${response.code}): $errorBody")
                }

                val body = response.body ?: throw IOException("Empty response body from Claude streaming endpoint")
                body.byteStream().use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val raw = line?.trim().orEmpty()
                            if (raw.startsWith("data:")) {
                                val dataStr = raw.removePrefix("data:").trim()
                                if (dataStr.isNotEmpty() && dataStr != "[DONE]") {
                                    try {
                                        val obj = json.parseToJsonElement(dataStr).jsonObject
                                        val type = obj["type"]?.jsonPrimitive?.content
                                        if (type == "content_block_delta") {
                                            val delta = obj["delta"]?.jsonObject
                                            val text = delta?.get("text")?.jsonPrimitive?.content
                                            if (!text.isNullOrEmpty()) {
                                                emit(text)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // Ignore incomplete chunks
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AiProvider.OPENAI, AiProvider.CUSTOM -> {
                val isCustom = (provider == AiProvider.CUSTOM)
                val apiKey = if (isCustom) {
                    secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()
                } else {
                    secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
                        ?: throw MissingApiKeyException("OpenAI API Key is missing. Please configure it in Settings.")
                }

                val baseUrl = if (isCustom) {
                    secureApiKeyStorage.getCustomBaseUrl().trimEnd('/')
                } else {
                    "https://api.openai.com/v1"
                }

                val url = "$baseUrl/chat/completions"

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
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))

                if (apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    response.close()
                    throw IOException("${provider.displayName} SSE stream error (${response.code}): $errorBody")
                }

                val body = response.body ?: throw IOException("Empty response body from ${provider.displayName}")
                body.byteStream().use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val raw = line?.trim().orEmpty()
                            if (raw.startsWith("data:")) {
                                val payload = raw.removePrefix("data:").trim()
                                if (payload == "[DONE]") {
                                    break
                                }
                                if (payload.isNotEmpty()) {
                                    try {
                                        val obj = json.parseToJsonElement(payload).jsonObject
                                        val choices = obj["choices"]?.jsonArray
                                        val delta = choices?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                                        val chunk = delta?.get("content")?.jsonPrimitive?.content
                                        if (!chunk.isNullOrEmpty()) {
                                            emit(chunk)
                                        }
                                    } catch (_: Exception) {
                                        // Ignore incomplete stream chunks
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches live available models from the provider's API.
     * - For Gemini: GET https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey
     * - For OpenAI / Custom: GET $baseUrl/models
     * - For Claude: GET https://api.anthropic.com/v1/models (or fallback presets)
     */
    suspend fun fetchLiveModels(provider: AiProvider): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            when (provider) {
                AiProvider.GEMINI -> {
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
                        val modelIds = modelsArray.mapNotNull { modelItem ->
                            val obj = modelItem.jsonObject
                            val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull {
                                it.jsonPrimitive.content
                            }.orEmpty()
                            // Filter models where supportedGenerationMethods contains "generateContent"
                            if (methods.contains("generateContent")) {
                                obj["name"]?.jsonPrimitive?.content?.removePrefix("models/")
                            } else null
                        }
                        modelIds
                    }
                }

                AiProvider.OPENAI, AiProvider.CUSTOM -> {
                    val isCustom = (provider == AiProvider.CUSTOM)
                    val apiKey = if (isCustom) {
                        secureApiKeyStorage.getApiKeyForProvider(AiProvider.CUSTOM).orEmpty()
                    } else {
                        secureApiKeyStorage.getApiKeyForProvider(AiProvider.OPENAI)
                            ?: throw MissingApiKeyException("OpenAI API key is required to sync live models.")
                    }

                    val baseUrl = if (isCustom) {
                        secureApiKeyStorage.getCustomBaseUrl().trimEnd('/')
                    } else {
                        "https://api.openai.com/v1"
                    }

                    val url = "$baseUrl/models"
                    val reqBuilder = Request.Builder().url(url).get()
                    if (apiKey.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer $apiKey")
                    }

                    okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IOException("${provider.displayName} API error (${response.code}): $body")
                        }
                        val jsonElement = json.parseToJsonElement(body).jsonObject
                        val dataArray = jsonElement["data"]?.jsonArray.orEmpty()
                        val modelIds = dataArray.mapNotNull { item ->
                            val id = item.jsonObject["id"]?.jsonPrimitive?.content
                            // Keep chat/instruct/completion models
                            if (!id.isNullOrBlank() && !id.contains("embedding") && !id.contains("whisper") && !id.contains("tts") && !id.contains("dall-e")) {
                                id
                            } else null
                        }
                        modelIds
                    }
                }

                AiProvider.CLAUDE -> {
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
                            // Fallback to presets if /models is restricted or unavailable
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
        }
    }
}
