package org.opened.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.opened.app.core.security.SecureApiKeyStorage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

class MissingApiKeyException(
    message: String = "Gemini API Key is missing. Please configure your key in Settings."
) : IllegalStateException(message)

class GeminiApiException(
    val statusCode: Int,
    override val message: String
) : IOException("Gemini API error ($statusCode): $message")

@Singleton
class GeminiApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureApiKeyStorage: SecureApiKeyStorage
) {
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    private fun executeGenerateCall(targetModel: String, requestBodyJson: String, apiKey: String): okhttp3.Response {
        val url = "$baseUrl/$targetModel:generateContent?key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(httpRequest).execute()
    }

    private fun executeStreamCall(targetModel: String, requestBodyJson: String, apiKey: String): okhttp3.Response {
        val url = "$baseUrl/$targetModel:streamGenerateContent?alt=sse&key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(httpRequest).execute()
    }

    suspend fun generateContent(
        model: String,
        request: GeminiGenerateRequest
    ): String = withContext(Dispatchers.IO) {
        val apiKey = secureApiKeyStorage.getApiKey()
            ?: throw MissingApiKeyException()

        val effectiveRequest = if (request.generationConfig?.maxOutputTokens == null) {
            val cfg = (request.generationConfig ?: GeminiGenerationConfig()).copy(maxOutputTokens = 8192)
            request.copy(generationConfig = cfg)
        } else {
            request
        }
        val requestBodyJson = json.encodeToString(GeminiGenerateRequest.serializer(), effectiveRequest)

        val fallbackChain = buildFallbackChain(model)
        var response: okhttp3.Response? = null
        var lastErrorMsg: String? = null
        var lastStatusCode = 0

        for (targetModel in fallbackChain) {
            val currentResponse = executeGenerateCall(targetModel, requestBodyJson, apiKey)
            if (currentResponse.isSuccessful) {
                response = currentResponse
                break
            }

            lastStatusCode = currentResponse.code
            val errorBody = currentResponse.body?.string().orEmpty()
            lastErrorMsg = try {
                val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), errorBody)
                parsed.error?.message ?: errorBody
            } catch (e: Exception) {
                errorBody
            }
            currentResponse.close()

            // Automatically fall back along active Gemini 3 chain only on 404 or 429
            if (lastStatusCode != 404 && lastStatusCode != 429) {
                throw GeminiApiException(lastStatusCode, lastErrorMsg)
            }
        }

        val finalResponse = response ?: throw GeminiApiException(
            lastStatusCode.takeIf { it != 0 } ?: 429,
            lastErrorMsg ?: "Request failed for model $model"
        )

        finalResponse.use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            val parsedResponse = json.decodeFromString(GeminiGenerateResponse.serializer(), responseBody)
            val text = parsedResponse.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.joinToString("") { it.text }
                .orEmpty()

            text
        }
    }

    fun streamGenerateContent(
        model: String,
        request: GeminiGenerateRequest
    ): Flow<String> = flow {
        val apiKey = secureApiKeyStorage.getApiKey()
            ?: throw MissingApiKeyException()

        val effectiveRequest = if (request.generationConfig?.maxOutputTokens == null) {
            val cfg = (request.generationConfig ?: GeminiGenerationConfig()).copy(maxOutputTokens = 8192)
            request.copy(generationConfig = cfg)
        } else {
            request
        }
        val requestBodyJson = json.encodeToString(GeminiGenerateRequest.serializer(), effectiveRequest)

        val fallbackChain = buildFallbackChain(model)
        var response: okhttp3.Response? = null
        var lastErrorMsg: String? = null
        var lastStatusCode = 0

        for (targetModel in fallbackChain) {
            val currentResponse = executeStreamCall(targetModel, requestBodyJson, apiKey)
            if (currentResponse.isSuccessful) {
                response = currentResponse
                break
            }

            lastStatusCode = currentResponse.code
            val errorBody = currentResponse.body?.string().orEmpty()
            lastErrorMsg = try {
                val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), errorBody)
                parsed.error?.message ?: errorBody
            } catch (e: Exception) {
                errorBody
            }
            currentResponse.close()

            // Automatically fall back along active Gemini 3 chain only on 404 or 429
            if (lastStatusCode != 404 && lastStatusCode != 429) {
                throw GeminiApiException(lastStatusCode, lastErrorMsg)
            }
        }

        val finalResponse = response ?: throw GeminiApiException(
            lastStatusCode.takeIf { it != 0 } ?: 429,
            lastErrorMsg ?: "Streaming request failed for model $model"
        )

        val body = finalResponse.body ?: throw IOException("Empty response body from Gemini streaming endpoint")
        body.byteStream().use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                val sseDataBuffer = StringBuilder()

                suspend fun tryParseAndEmit(jsonStr: String): Boolean {
                    if (jsonStr.isBlank()) return false
                    return try {
                        val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), jsonStr)
                        val chunkText = parsed.candidates
                            ?.firstOrNull()
                            ?.content
                            ?.parts
                            ?.joinToString("") { it.text }
                            .orEmpty()

                        if (chunkText.isNotEmpty()) {
                            emit(chunkText)
                        }
                        true
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                }

                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line ?: break
                    val trimmed = rawLine.trim()

                    if (trimmed.isEmpty()) {
                        // Empty line signals dispatch of current SSE event block
                        if (sseDataBuffer.isNotEmpty()) {
                            tryParseAndEmit(sseDataBuffer.toString())
                            sseDataBuffer.clear()
                        }
                    } else if (trimmed.startsWith("data:")) {
                        val payload = trimmed.substring(5).trim()
                        if (payload.isNotEmpty()) {
                            // First attempt immediate parsing of single-line data event
                            val parsedImmediately = tryParseAndEmit(payload)
                            if (!parsedImmediately) {
                                // Accumulate in buffer for multi-line JSON events
                                if (sseDataBuffer.isNotEmpty()) {
                                    sseDataBuffer.append("\n")
                                }
                                sseDataBuffer.append(payload)
                            } else {
                                sseDataBuffer.clear()
                            }
                        }
                    }
                }

                // Flush any remaining buffer at end of stream
                if (sseDataBuffer.isNotEmpty()) {
                    tryParseAndEmit(sseDataBuffer.toString())
                }
            }
        }
        finalResponse.close()
    }.flowOn(Dispatchers.IO)

    companion object {
        val GEMINI_FALLBACK_CHAIN = listOf(
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite"
        )

        fun buildFallbackChain(initialModel: String): List<String> {
            val chain = mutableListOf(initialModel)
            for (fallback in GEMINI_FALLBACK_CHAIN) {
                if (fallback != initialModel && !chain.contains(fallback)) {
                    chain.add(fallback)
                }
            }
            return chain
        }
    }
}
