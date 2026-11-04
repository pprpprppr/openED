package org.opened.app.core.ai

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.security.SecureApiKeyStorage

class GeminiApiClientFallbackTest {

    @Test
    fun testAutoFallbackFromProToFlashOn429() = runBlocking {
        val requestedModels = mutableListOf<String>()

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val model = url.substringAfter("/models/").substringBefore(':')
            requestedModels.add(model)

            if (model == "gemini-3.1-pro-preview") {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Rate Limit")
                    .body(
                        """{"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            } else {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"candidates":[{"content":{"parts":[{"text":"Hello from fallback"}]}}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
        }

        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val mockStorage = SecureApiKeyStorage(context = null) {
            val map = mutableMapOf<String, Any?>()
            java.lang.reflect.Proxy.newProxyInstance(
                android.content.SharedPreferences::class.java.classLoader,
                arrayOf(android.content.SharedPreferences::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getString" -> "dummy-key"
                    else -> null
                }
            } as android.content.SharedPreferences
        }
        mockStorage.saveApiKey("dummy-key")

        val client = GeminiApiClient(okHttpClient, mockStorage)
        val result = client.generateContent(
            model = "gemini-3.1-pro-preview",
            request = GeminiGenerateRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart("test")))))
        )

        assertEquals("Hello from fallback", result)
        assertEquals(listOf("gemini-3.1-pro-preview", "gemini-3.8-flash"), requestedModels)
    }

    @Test
    fun testAutoFallbackAlongGemini3ChainWhen38FlashAlsoReturns429() = runBlocking {
        val requestedModels = mutableListOf<String>()

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val model = url.substringAfter("/models/").substringBefore(':')
            requestedModels.add(model)

            if (model == "gemini-3.1-pro-preview" || model == "gemini-3.8-flash") {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Rate Limit")
                    .body(
                        """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            } else {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"candidates":[{"content":{"parts":[{"text":"Hello from 3.6 flash"}]}}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
        }

        val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val mockStorage = SecureApiKeyStorage(context = null) {
            java.lang.reflect.Proxy.newProxyInstance(
                android.content.SharedPreferences::class.java.classLoader,
                arrayOf(android.content.SharedPreferences::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getString" -> "dummy-key"
                    else -> null
                }
            } as android.content.SharedPreferences
        }
        mockStorage.saveApiKey("dummy-key")

        val client = GeminiApiClient(okHttpClient, mockStorage)
        val result = client.generateContent(
            model = "gemini-3.1-pro-preview",
            request = GeminiGenerateRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart("test")))))
        )

        assertEquals("Hello from 3.6 flash", result)
        assertEquals(listOf("gemini-3.1-pro-preview", "gemini-3.8-flash", "gemini-3.6-flash"), requestedModels)
    }
}
