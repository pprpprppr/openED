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
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun generateContent(
        model: String,
        request: GeminiGenerateRequest
    ): String = withContext(Dispatchers.IO) {
        val apiKey = secureApiKeyStorage.getApiKey()
            ?: throw MissingApiKeyException()

        val url = "$baseUrl/$model:generateContent?key=$apiKey"
        val requestBodyJson = json.encodeToString(GeminiGenerateRequest.serializer(), request)

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorMsg = try {
                    val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), responseBody)
                    parsed.error?.message ?: responseBody
                } catch (e: Exception) {
                    responseBody
                }
                throw GeminiApiException(response.code, errorMsg)
            }

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

        val url = "$baseUrl/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val requestBodyJson = json.encodeToString(GeminiGenerateRequest.serializer(), request)

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            val errorMsg = try {
                val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), errorBody)
                parsed.error?.message ?: errorBody
            } catch (e: Exception) {
                errorBody
            }
            response.close()
            throw GeminiApiException(response.code, errorMsg)
        }

        val body = response.body ?: throw IOException("Empty response body from Gemini streaming endpoint")
        body.byteStream().use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim().orEmpty()
                    if (currentLine.startsWith("data:")) {
                        val jsonPart = currentLine.removePrefix("data:").trim()
                        if (jsonPart.isNotEmpty()) {
                            try {
                                val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), jsonPart)
                                val chunkText = parsed.candidates
                                    ?.firstOrNull()
                                    ?.content
                                    ?.parts
                                    ?.joinToString("") { it.text }
                                    .orEmpty()

                                if (chunkText.isNotEmpty()) {
                                    emit(chunkText)
                                }
                            } catch (e: Exception) {
                                // If partial JSON chunk or heartbeat, continue
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
